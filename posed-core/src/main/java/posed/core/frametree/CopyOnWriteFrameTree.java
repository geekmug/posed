/*
 * Copyright (C) 2019, Scott Dial, All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package posed.core.frametree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import org.orekit.frames.FixedTransformProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;

import posed.core.Frames;
import posed.core.Pose;
import posed.core.UnknownTransformProvider;

/**
 * A concurrently mutable tree of frames.
 *
 * <p>Retrieval operations (including {@code get}) generally do not
 * block, so may overlap with update operations (including {@code create}
 * and {@code remove}). Retrievals reflect the results of the most
 * recently <em>completed</em> update operations holding upon their
 * onset. (More formally, an update operation for a given frame bears a
 * <em>happens-before</em> relation with any (non-null) retrieval for
 * that frame reporting the updated value.)
 */
public final class CopyOnWriteFrameTree implements FrameTree {
    /* See com.google.common.graph.GraphConstants.DEFAULT_NODE_COUNT */
    private static final int DEFAULT_NODE_COUNT = 10;

    private interface StateOperation {}

    private static final class CreateStateOperation implements StateOperation {
        private final Frame parent;
        private final String name;
        private final TransformProvider xfrm;

        private CreateStateOperation(final Frame parent, final String name,
                final TransformProvider xfrm) {
            this.parent = parent;
            this.name = name;
            this.xfrm = xfrm;
        }
    }

    private static final class UpdateStateOperation implements StateOperation {
        private final Frame frame;
        private final TransformProvider xfrm;

        private UpdateStateOperation(final Frame frame,
                final TransformProvider xfrm) {
            this.frame = frame;
            this.xfrm = xfrm;
        }
    }

    private static final class RemoveStateOperation implements StateOperation {
        private final Frame frame;

        private RemoveStateOperation(final Frame frame) {
            this.frame = frame;
        }
    }

    private static final class State {
        private final Frame root;
        private final HashMap<String, Frame> frames;
        private final MutableGraph<Frame> graph;

        /**
         * Initializes a state with a given root frame.
         * @param root root frame
         * @param size expected number of frames
         */
        private State(final Frame root, final int size) {
            this.root = checkNotNull(root);
            frames = new HashMap<>(size);
            graph = GraphBuilder.directed().expectedNodeCount(size).build();

            // Always copy the root node as-is.
            frames.put(root.getName(), root);
            graph.addNode(root);
        }

        /**
         * Creates a copy of a state with an edit.
         * @param state existing state
         * @param target frame to be edited
         * @param targetXfrm new transform for target frame
         */
        private State(final State state, final StateOperation operation) {
            this(state.root, state.frames.size());

            Frame update = null;
            TransformProvider updateXfrm = null;
            boolean childOfUpdate = false;
            if (operation instanceof UpdateStateOperation) {
                UpdateStateOperation op = (UpdateStateOperation) operation;
                update = op.frame;
                updateXfrm = op.xfrm;
            }

            Frame remove = null;
            if (operation instanceof RemoveStateOperation) {
                remove = ((RemoveStateOperation) operation).frame;
            }

            // Rebuild the frames by traversing the graph.
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            stack.push(root);
            for (Frame frame : Traverser.forGraph(state.graph).depthFirstPreOrder(root)) {
                if (frame == root || frame == remove) {
                    continue;
                }

                Frame parent = stack.peek();
                while (!parent.getName().equals(frame.getParent().getName())) {
                    Frame popped = stack.pop();
                    if (popped == update) {
                        childOfUpdate = false;
                    }
                    parent = stack.peek();
                }

                Frame newFrame;
                if (frame == update || childOfUpdate) {
                    TransformProvider xfrm = frame.getTransformProvider();
                    if (frame == update) {
                        if (updateXfrm != null) {
                            xfrm = updateXfrm;
                        }
                        childOfUpdate = true;
                    }
                    newFrame = new Frame(parent, xfrm, frame.getName(),
                            frame.isPseudoInertial());
                    if (frame == update) {
                        update = newFrame;
                    }
                } else {
                    newFrame = frame;
                }
                frames.put(frame.getName(), newFrame);
                graph.putEdge(parent, newFrame);
                stack.push(newFrame);
            }

            if (operation instanceof CreateStateOperation) {
                CreateStateOperation op = (CreateStateOperation) operation;

                TransformProvider xfrm = op.xfrm;
                if (xfrm == null) {
                    xfrm = UnknownTransformProvider.INSTANCE;
                }
                Frame newFrame = new Frame(op.parent, xfrm, op.name);
                frames.put(op.name, newFrame);
                graph.putEdge(op.parent, newFrame);
            }
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<State> stateRef;

    /**
     * Creates a new frame tree with a given root frame.
     * @param root root frame
     */
    public CopyOnWriteFrameTree(final Frame root) {
        stateRef = new AtomicReference<>(new State(root, DEFAULT_NODE_COUNT));
    }

    /**
     * Creates a (cheap) copy of the tree.
     * @param tree tree to be copied
     */
    public CopyOnWriteFrameTree(final CopyOnWriteFrameTree tree) {
        stateRef = new AtomicReference<>(tree.stateRef.get());
    }

    @GuardedBy("lock")
    private void copyAndEdit(StateOperation operation) {
        stateRef.set(new State(stateRef.get(), operation));
    }

    @Override
    public void createRoot(String name) {
        create(stateRef.get().root.getName(), name, (TransformProvider) null);
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * transform provider as long as the parent is the same.
     *
     * <p>All transform providers should be immutable to maintain the coherency
     * of operations on this frame tree.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param xfrm transform provider describing the new frame
     * @return a supplier of a iterable of frames that were effected
     */
    public Supplier<Iterable<Frame>> update(String parentName, String name, TransformProvider xfrm) {
        checkNotNull(parentName);
        checkNotNull(name);

        lock.lock();
        try {
            State state = stateRef.get();
            Frame parent = state.frames.get(parentName);
            checkArgument(parent != null, "parent frame is not defined");

            Frame frame = state.frames.get(name);
            checkArgument(frame == null || frame.getParent() == parent,
                    "frame exists with a different parent");

            if (frame != null) {
                copyAndEdit(new UpdateStateOperation(frame, xfrm));
                return () -> Traverser.forGraph(state.graph).depthFirstPreOrder(frame);
            } else {
                copyAndEdit(new CreateStateOperation(parent, name, xfrm));
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void create(String parentName, String name, TransformProvider xfrm) {
        update(parentName, name, xfrm);
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * transform as long as the parent is the same.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param xfrm transform describing the new frame
     * @return a supplier of a iterable of frames that were effected
     */
    public Supplier<Iterable<Frame>> update(String parentName, String name, Transform xfrm) {
        return update(parentName, name, new FixedTransformProvider(xfrm));
    }

    @Override
    public void create(String parentName, String name, Transform xfrm) {
        update(parentName, name, xfrm);
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * pose as long as the parent is the same.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param pose pose describing the new frame
     * @return a supplier of a iterable of frames that were effected
     */
    public Supplier<Iterable<Frame>> update(String parentName, String name, Pose pose) {
        return update(parentName, name, Frames.makeTransform(pose));
    }

    @Override
    public void create(String parentName, String name, Pose pose) {
        update(parentName, name, pose);
    }

    @Override
    public void remove(String name) {
        checkNotNull(name);

        lock.lock();
        try {
            State state = stateRef.get();

            Frame frame = state.frames.get(name);
            if (frame == null) {
                return;
            }

            checkArgument(state.graph.outDegree(frame) == 0,
                    "unable to remove a parent frame for other frames");

            copyAndEdit(new RemoveStateOperation(frame));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Frame get(String name) {
        return stateRef.get().frames.get(name);
    }

    @Override
    public Iterable<Frame> traverse(String root) {
        checkNotNull(root);

        State state = stateRef.get();
        Frame rootFrame = state.frames.get(root);
        if (rootFrame == null) {
            return ImmutableList.of();
        }
        return Traverser.forGraph(state.graph).depthFirstPreOrder(rootFrame);
    }

    @Override
    public Iterable<Frame> traverse() {
        return traverse(stateRef.get().root.getName());
    }

    @Override
    public Iterable<Frame> subgraph(String target) {
        checkNotNull(target);

        State state = stateRef.get();
        Frame targetFrame = state.frames.get(target);
        if (targetFrame == null) {
            return ImmutableList.of();
        }
        // Walk up the graph to find the root of this subgraph for traversal.
        while (true) {
            Frame parent = state.frames.get(targetFrame.getParent().getName());
            if (parent == state.root) {
                return Traverser.forGraph(state.graph)
                        .depthFirstPreOrder(targetFrame);
            }
            targetFrame = parent;
        }
    }
}
