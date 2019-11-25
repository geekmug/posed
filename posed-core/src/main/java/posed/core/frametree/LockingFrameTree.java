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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.locks.ReentrantLock;

import org.orekit.frames.FixedTransformProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;

import posed.core.Frames;
import posed.core.Pose;

/**
 * A mutable tree of frames using a ReentrantLock for coherence.
 *
 * <p>Both the advantage and disadvantage to this implementation is that the
 * mutations occur in-place, requiring an exclusive lock. Traversals are
 * implemented by copying the iterable to avoid a requirement to hold the
 * lock for the duration of the traversal.
 */
public final class LockingFrameTree implements FrameTree {
    private final ReentrantLock lock = new ReentrantLock();
    private final Frame root;
    private final SynchronizedFrameTree frameTree;

    /**
     * Creates a new frame tree with a given root frame.
     * @param root root frame
     */
    public LockingFrameTree(final Frame root) {
        this.root = checkNotNull(root);
        frameTree = new SynchronizedFrameTree(root);
    }

    /** Acquires the lock. */
    public void lock() {
        lock.lock();
    }

    /** Releases the lock. */
    public void unlock() {
        lock.unlock();
    }

    @Override
    public Frame get(String name) {
        checkNotNull(name);

        lock.lock();
        try {
            return frameTree.get(name);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterable<Frame> traverse(String root) {
        checkNotNull(root);

        lock.lock();
        try {
            return frameTree.traverse(root);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterable<Frame> traverse() {
        return traverse(root.getName());
    }

    @Override
    public Frame findRoot(String target) {
        lock.lock();
        try {
            return frameTree.findRoot(target);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterable<Frame> subgraph(String target) {
        lock.lock();
        try {
            return frameTree.subgraph(target);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void createRoot(String name) {
        create(root.getName(), name, (TransformProvider) null);
    }

    @Override
    public void create(String parentName, String name, TransformProvider xfrm) {
        lock.lock();
        try {
            frameTree.create(parentName, name, xfrm);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void create(String parentName, String name,
            Transform xfrm) {
        create(parentName, name, new FixedTransformProvider(xfrm));
    }

    @Override
    public void create(String parentName, String name,
            Pose pose) {
        create(parentName, name, Frames.makeTransform(pose));
    }

    @Override
    public void remove(String name) {
        lock.lock();
        try {
            frameTree.remove(name);
        } finally {
            lock.unlock();
        }
    }
}
