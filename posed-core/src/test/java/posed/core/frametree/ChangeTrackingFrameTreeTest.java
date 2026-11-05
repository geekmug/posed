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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TransformProviderUtils;
import org.reactivestreams.Subscription;

import com.google.common.collect.ImmutableList;

import posed.core.Pose;
import posed.core.frametree.ChangeTrackingFrameTree.Change;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.concurrent.Queues;

public class ChangeTrackingFrameTreeTest extends AbstractFrameTreeTest {
    /** A subscriber that only requests the initial frame and then stops requesting. */
    private static final class StalledSubscriber extends BaseSubscriber<Change> {
        private int received;
        private Throwable error;

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        protected void hookOnNext(Change change) {
            received++;
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            error = throwable;
        }
    }

    @Override
    protected ChangeTrackingFrameTree<?> newFrameTree(Frame root) {
        return new ChangeTrackingFrameTree<>(new CopyOnWriteFrameTree(root));
    }

    @Test
    public void testSubscriberGetsFramesCreatedBeforeSubscribing() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        Flux<Change> stream = tree.getChangeStream();
        tree.createRoot("test");

        AtomicInteger received = new AtomicInteger();
        stream.subscribe(change -> received.incrementAndGet());

        // The subscriber gets the initial frame and the frame that was created.
        assertThat(received.get(), is(equalTo(2)));
    }

    @Test
    public void testUpdateEmitsCreatedForFrameAndDescendants() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);

        List<String> created = new ArrayList<>();
        tree.getChangeStream().skip(3).subscribe(change ->
                created.add(((ChangeTrackingFrameTree.Created) change).getFrame().getName()));
        tree.update("GCRF", "A", TransformProviderUtils.IDENTITY_PROVIDER);

        assertThat(created, is(equalTo(ImmutableList.of("A", "B"))));
    }

    @Test
    public void testSubscriberGetsChangesWhileConsumingInitialFrames() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("before");
        StalledSubscriber stalled = new StalledSubscriber();
        tree.getChangeStream().subscribe(stalled);

        tree.createRoot("during");
        stalled.requestUnbounded();

        // The subscriber gets both of the initial frames and the change.
        assertThat(stalled.received, is(equalTo(3)));
    }

    @Test
    public void testBufferHoldsInitialFramesAndAsManyChanges() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        int frames = Queues.SMALL_BUFFER_SIZE;
        for (int i = 1; i < frames; i++) {
            tree.createRoot("frame" + i);
        }
        StalledSubscriber stalled = new StalledSubscriber();
        tree.getChangeStream().subscribe(stalled);

        for (int i = 0; i < frames; i++) {
            tree.createRoot("test");
        }
        stalled.requestUnbounded();

        // The subscriber gets every initial frame and every change.
        assertThat(stalled.received, is(equalTo(2 * frames)));
        assertThat(stalled.error, is(nullValue()));
    }

    @Test
    public void testCancelledSubscriberIsNoLongerTracked() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        Disposable subscription = tree.getChangeStream().subscribe();
        assertThat(tree.getSubscriberCount(), is(equalTo(1)));

        subscription.dispose();

        assertThat(tree.getSubscriberCount(), is(equalTo(0)));
    }

    @Test
    public void testStalledSubscriberDoesNotBlockOthers() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        AtomicInteger received = new AtomicInteger();
        tree.getChangeStream().subscribe(change -> received.incrementAndGet());
        StalledSubscriber stalled = new StalledSubscriber();
        tree.getChangeStream().subscribe(stalled);

        int updates = 1000;
        for (int i = 0; i < updates; i++) {
            tree.createRoot("test");
        }

        // The other subscriber gets the initial frame and every update.
        assertThat(received.get(), is(equalTo(updates + 1)));
        assertThat(stalled.error, is(nullValue()));
    }

    @Test
    public void testStalledSubscriberFailsAfterDrainingOverflowedBuffer() {
        ChangeTrackingFrameTree<?> tree = newFrameTree(FramesFactory.getGCRF());
        StalledSubscriber stalled = new StalledSubscriber();
        tree.getChangeStream().subscribe(stalled);

        for (int i = 0; i <= Queues.SMALL_BUFFER_SIZE; i++) {
            tree.createRoot("test");
        }
        stalled.requestUnbounded();

        // The subscriber gets the initial frame and a full buffer before the failure.
        assertThat(stalled.received, is(equalTo(Queues.SMALL_BUFFER_SIZE + 1)));
        assertThat(Exceptions.isOverflow(stalled.error), is(equalTo(true)));
    }
}
