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

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.orekit.frames.Frame;
import org.orekit.frames.TransformProvider;

/**
 * A mutable tree of frames using a ReadWriteLock for coherence.
 *
 * <p>Both the advantage and disadvantage to this implementation is that the
 * mutations occur in-place, requiring an exclusive write lock. Traversals are
 * implemented by copying the iterable to avoid a requirement to hold the read
 * lock for the duration of the traversal.
 */
public final class ReadWriteLockingFrameTree implements FrameTree {
    private final Frame root;
    private final SynchronizedFrameTree frameTree;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;

    /**
     * Creates a new frame tree with a given root frame.
     * @param root root frame
     */
    public ReadWriteLockingFrameTree(final Frame root) {
        this.root = checkNotNull(root);
        frameTree = new SynchronizedFrameTree(root);

        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    /** Acquires the read lock. */
    public void readLock() {
        readLock.lock();
    }

    /** Releases the read lock. */
    public void readUnlock() {
        readLock.unlock();
    }

    /** Acquires the write lock. */
    public void writeLock() {
        writeLock.lock();
    }

    /** Releases the write lock. */
    public void writeUnlock() {
        writeLock.unlock();
    }

    @Override
    public Frame get(String name) {
        checkNotNull(name);

        readLock.lock();
        try {
            return frameTree.get(name);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Iterable<Frame> traverse(String root) {
        checkNotNull(root);

        readLock.lock();
        try {
            return frameTree.traverse(root);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Iterable<Frame> traverse() {
        return traverse(root.getName());
    }

    @Override
    public Frame findRoot(String target) {
        readLock.lock();
        try {
            return frameTree.findRoot(target);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Iterable<Frame> subgraph(String target) {
        readLock.lock();
        try {
            return frameTree.subgraph(target);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void createRoot(String name) {
        create(root.getName(), name, (TransformProvider) null);
    }

    @Override
    public void create(String parentName, String name, TransformProvider xfrm) {
        writeLock.lock();
        try {
            frameTree.create(parentName, name, xfrm);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void update(String parentName, String name, TransformProvider xfrm) {
        writeLock.lock();
        try {
            frameTree.update(parentName, name, xfrm);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(String name) {
        writeLock.lock();
        try {
            frameTree.remove(name);
        } finally {
            writeLock.unlock();
        }
    }
}
