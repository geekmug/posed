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

import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import posed.core.Pose;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * A proxy class that allows tracking changes to the {@code FrameTree}.
 * @param <T> type of the underlying {@code FrameTree}
 */
public final class ChangeTrackingFrameTree<T extends FrameTree> implements FrameTree {
    /** Tagging interface for all changes to the {@code FrameTree}. */
    public interface Change {}

    /** Represents the creation (or update) to {@link Frame} in the {@code FrameTree}. */
    public static final class Created implements Change {
        private final Frame frame;

        private Created(final Frame frame) {
            this.frame = checkNotNull(frame);
        }

        public Frame getFrame() {
            return this.frame;
        }
    }

    /** Represents the removal of a {@link Frame} in the {@code FrameTree}. */
    public static final class Removed implements Change {
        private final String name;

        private Removed(final String name) {
            this.name = checkNotNull(name);
        }

        public String getName() {
            return this.name;
        }
    }

    private final Object changeMonitor = new Object();
    private final Sinks.Many<Change> changeSink = Sinks.many().multicast().directBestEffort();
    private final T delegate;

    /**
     * Creates a proxy for the given delegate.
     * @param delegate actual implementation of {@code FrameTree} to use
     */
    public ChangeTrackingFrameTree(final T delegate) {
        this.delegate = checkNotNull(delegate);
    }

    /**
     * Gets the underlying {@code FrameTree} for this proxy.
     * @return underlying {@code FrameTree} for this proxy
     */
    public T getDelegate() {
        return delegate;
    }

    /**
     * Gets a stream of {@code Change}s for this {@code FrameTree}.
     *
     * <p>The produced stream is based on <em>potential</em> updates and may
     * produce updates that have no apparent change.
     *
     * <p>Each subscription starts with a creation for every frame that exists
     * at the time of subscribing, followed by every change after that.
     *
     * <p>Each subscriber is buffered independently, so a slow subscriber does
     * not delay any others. The buffer holds the initial frames and at least as
     * many changes again. A subscriber that falls too far behind stops receiving
     * new changes and, after it has consumed the changes that were buffered,
     * gets an overflow error. It can then subscribe again to get the current
     * frames.
     *
     * @return a stream of {@code Change}s for this {@code FrameTree}
     */
    public Flux<Change> getChangeStream() {
        return Flux.defer(() -> {
            int bufferSize = Math.max(Queues.SMALL_BUFFER_SIZE, 2 * Iterables.size(delegate.traverse()));
            return Flux.<Change>create(emitter -> {
                synchronized (changeMonitor) {
                    emitter.onDispose(changeSink.asFlux().subscribe(emitter::next));
                    delegate.traverse().forEach(frame -> emitter.next(new Created(frame)));
                }
            }).onBackpressureBuffer(bufferSize, BufferOverflowStrategy.ERROR);
        });
    }

    @VisibleForTesting
    int getSubscriberCount() {
        return changeSink.currentSubscriberCount();
    }

    private void emitCreates(String name) {
        delegate.traverse(name).forEach(frame -> {
            changeSink.tryEmitNext(new Created(frame));
        });
    }

    @Override
    public void createRoot(String name) {
        synchronized (changeMonitor) {
            delegate.createRoot(name);
            emitCreates(name);
        }
    }

    @Override
    public void create(String parentName, String name, TransformProvider xfrm) {
        synchronized (changeMonitor) {
            delegate.create(parentName, name, xfrm);
            emitCreates(name);
        }
    }

    @Override
    public void create(String parentName, String name, Transform xfrm) {
        synchronized (changeMonitor) {
            delegate.create(parentName, name, xfrm);
            emitCreates(name);
        }
    }

    @Override
    public void create(String parentName, String name, Pose pose) {
        synchronized (changeMonitor) {
            delegate.create(parentName, name, pose);
            emitCreates(name);
        }
    }

    @Override
    public void update(String parentName, String name, TransformProvider xfrm) {
        synchronized (changeMonitor) {
            delegate.update(parentName, name, xfrm);
            emitCreates(name);
        }
    }

    @Override
    public void update(String parentName, String name, Transform xfrm) {
        synchronized (changeMonitor) {
            delegate.update(parentName, name, xfrm);
            emitCreates(name);
        }
    }

    @Override
    public void update(String parentName, String name, Pose pose) {
        synchronized (changeMonitor) {
            delegate.update(parentName, name, pose);
            emitCreates(name);
        }
    }

    @Override
    public void remove(String name) {
        synchronized (changeMonitor) {
            delegate.remove(name);
            changeSink.tryEmitNext(new Removed(name));
        }
    }

    @Override
    public Frame get(String name) {
        return delegate.get(name);
    }

    @Override
    public Iterable<Frame> traverse(String root) {
        return delegate.traverse(root);
    }

    @Override
    public Iterable<Frame> traverse() {
        return delegate.traverse();
    }

    @Override
    public Frame findRoot(String target) {
        return delegate.findRoot(target);
    }

    @Override
    public Iterable<Frame> subgraph(String target) {
        return delegate.subgraph(target);
    }
}
