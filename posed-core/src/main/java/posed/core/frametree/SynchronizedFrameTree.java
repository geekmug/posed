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
 * A mutable tree of frames requiring synchronization for coherence.
 *
 * <p>Both the advantage and disadvantage to this implementation is that the
 * mutations occur in-place, requiring synchronization on the instance.
 * Traversals are implemented by copying the iterable to avoid a requirement to
 * hold the read lock for the duration of the traversal.
 */
public final class SynchronizedFrameTree implements FrameTree {
    private final HashMap<String, Frame> frames = new HashMap<>();
    private final MutableGraph<Frame> graph = GraphBuilder.directed().build();
    private final Frame root;

    /**
     * Creates a new frame tree with a given root frame.
     * @param root root frame
     */
    public SynchronizedFrameTree(final Frame root) {
        this.root = checkNotNull(root);
        frames.put(root.getName(), root);
        graph.addNode(root);
    }

    @Override
    public Frame get(String name) {
        checkNotNull(name);

        return frames.get(name);
    }

    @Override
    public Iterable<Frame> traverse(String name) {
        checkNotNull(name);

        Frame rootFrame = frames.get(name);
        if (rootFrame == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(
                Traverser.forGraph(graph).depthFirstPreOrder(rootFrame));
    }

    @Override
    public Iterable<Frame> traverse() {
        return traverse(root.getName());
    }

    private Frame findRoot0(String target) {
        Frame targetFrame = frames.get(target);
        if (targetFrame == null) {
            return null;
        }
        // Walk up the graph to find the root of this subgraph for traversal.
        while (true) {
            Frame parent = frames.get(targetFrame.getParent().getName());
            if (parent == root) {
                return targetFrame;
            }
            targetFrame = parent;
        }
    }

    @Override
    public Frame findRoot(String target) {
        checkNotNull(target);
        return findRoot0(target);
    }

    @Override
    public Iterable<Frame> subgraph(String target) {
        checkNotNull(target);

        Frame targetFrame = findRoot0(target);
        if (targetFrame == null) {
            return ImmutableList.of();
        } else {
            return Traverser.forGraph(graph).depthFirstPreOrder(targetFrame);
        }
    }

    @Override
    public void createRoot(String name) {
        checkNotNull(name);
        create(root.getName(), name, (TransformProvider) null);
    }

    @Override
    public void create(String parentName, String name,
            TransformProvider xfrm) {
        checkNotNull(parentName);
        checkNotNull(name);

        Frame parent = frames.get(parentName);
        checkArgument(parent != null, "parent frame is not defined");

        Frame update = frames.get(name);
        checkArgument(update == null || update.getParent() == parent,
                "frame exists with a different parent");

        if (update != null) {
            // Rebuild the frames by traversing the graph.
            ImmutableList<Frame> effected = ImmutableList.copyOf(
                    Traverser.forGraph(graph).depthFirstPreOrder(update));
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            stack.push(parent);
            for (Frame frame : effected) {
                parent = stack.peek();
                while (!parent.getName().equals(frame.getParent().getName())) {
                    stack.pop();
                    parent = stack.peek();
                }

                TransformProvider newXfrm = frame.getTransformProvider();
                if (frame == update) {
                    newXfrm = xfrm;
                }
                Frame newFrame = new Frame(parent, newXfrm, frame.getName(),
                        frame.isPseudoInertial());

                graph.removeNode(frame);
                frames.put(newFrame.getName(), newFrame);
                graph.putEdge(parent, newFrame);
                stack.push(newFrame);
            }
        } else {
            TransformProvider newXfrm = xfrm;
            if (xfrm == null) {
                newXfrm = UnknownTransformProvider.INSTANCE;
            }

            Frame newFrame = new Frame(parent, newXfrm, name);
            frames.put(name, newFrame);
            graph.putEdge(parent, newFrame);
        }
    }

    @Override
    public void create(String parentName, String name, Transform xfrm) {
        checkNotNull(parentName);
        checkNotNull(name);
        checkNotNull(xfrm);

        create(parentName, name, new FixedTransformProvider(xfrm));
    }

    @Override
    public void create(String parentName, String name,
            Pose pose) {
        checkNotNull(parentName);
        checkNotNull(name);
        checkNotNull(pose);

        create(parentName, name, Frames.makeTransform(pose));
    }

    @Override
    public void remove(String name) {
        checkNotNull(name);

        Frame frame = frames.get(name);
        if (frame == null) {
            return;
        }

        checkArgument(graph.outDegree(frame) == 0,
                "unable to remove a parent frame for other frames");

        frames.remove(name);
        graph.removeNode(frame);
    }
}
