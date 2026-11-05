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

package posed.grpc;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.orekit.models.earth.Geoid;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.base.Optional;

import com.linecorp.armeria.client.grpc.GrpcClients;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.grpc.GrpcService;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.ServerCallStreamObserver;
import posed.core.Pose;
import posed.core.PoseService;
import posed.core.PosedCoreConfiguration;
import posed.grpc.proto.Cartesian;
import posed.grpc.proto.ConvertGeodeticReply;
import posed.grpc.proto.ConvertGeodeticRequest;
import posed.grpc.proto.ConvertLocalReply;
import posed.grpc.proto.ConvertLocalRequest;
import posed.grpc.proto.GeodeticPoseRequest;
import posed.grpc.proto.GeodeticPositionRequest;
import posed.grpc.proto.NauticalAngles;
import posed.grpc.proto.PoseRequest;
import posed.grpc.proto.PoseServiceGrpc;
import posed.grpc.proto.Quaternion;
import posed.grpc.proto.TransformReply;
import posed.grpc.proto.TransformRequest;
import reactor.core.publisher.Flux;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PosedCoreConfiguration.class)
public class PoseGrpcServiceTest {
    private static final long TIMEOUT_MILLIS = 5000;
    private static final long QUIET_MILLIS = 500;
    private static final PoseRequest POSE = PoseRequest.newBuilder()
            .setCartesian(Cartesian.getDefaultInstance())
            .setQuaternion(Quaternion.newBuilder().setW(1))
            .build();
    private static final GeodeticPoseRequest GEOPOSE = GeodeticPoseRequest.newBuilder()
            .setPosition(GeodeticPositionRequest.newBuilder().setHae(0))
            .setAngles(NauticalAngles.getDefaultInstance())
            .build();
    private static final TransformRequest TRANSFORM = TransformRequest.newBuilder()
            .setSrcFrame("test")
            .setDstFrame("other")
            .setPose(POSE)
            .build();
    private static final TransformRequest CHILD_TRANSFORM = TransformRequest.newBuilder()
            .setSrcFrame("child")
            .setDstFrame("test")
            .setPose(POSE)
            .build();

    /** A client that disconnects as soon as it gets a reply. */
    private static final class CancelOnFirstReply
            implements ClientResponseObserver<TransformRequest, TransformReply> {
        private ClientCallStreamObserver<TransformRequest> requestStream;

        @Override
        public void beforeStart(ClientCallStreamObserver<TransformRequest> stream) {
            requestStream = stream;
        }

        @Override
        public void onNext(TransformReply reply) {
            requestStream.cancel("disconnecting", null);
        }

        @Override
        public void onError(Throwable t) {
            // The call fails because it was cancelled.
        }

        @Override
        public void onCompleted() {
            // The call is only ever cancelled.
        }
    }

    /** A client that only reads a single reply until it is told to read the rest. */
    private static final class SlowClient
            implements ClientResponseObserver<TransformRequest, TransformReply> {
        private final CountDownLatch gotFirstReply = new CountDownLatch(1);
        private final CountDownLatch gotLastReply = new CountDownLatch(1);
        private final double lastPosition;
        private ClientCallStreamObserver<TransformRequest> requestStream;

        SlowClient(final double lastPosition) {
            this.lastPosition = lastPosition;
        }

        void readRemainingReplies() {
            requestStream.request(Integer.MAX_VALUE);
        }

        void disconnect() {
            requestStream.cancel("disconnecting", null);
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<TransformRequest> stream) {
            requestStream = stream;
            stream.disableAutoRequestWithInitial(1);
        }

        @Override
        public void onNext(TransformReply reply) {
            gotFirstReply.countDown();
            if (Double.compare(reply.getPose().getCartesian().getX(), lastPosition) == 0) {
                gotLastReply.countDown();
            }
        }

        @Override
        public void onError(Throwable t) {
            // The call fails because it was cancelled.
        }

        @Override
        public void onCompleted() {
            // The call is only ever cancelled.
        }
    }

    /** Counts the replies that a server gives to the underlying transport. */
    private static final class ReplyCounter implements ServerInterceptor {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public <Q, A> ServerCall.Listener<Q> interceptCall(ServerCall<Q, A> call,
                Metadata headers, ServerCallHandler<Q, A> next) {
            return next.startCall(new SimpleForwardingServerCall<Q, A>(call) {
                @Override
                public void sendMessage(A message) {
                    count.incrementAndGet();
                    super.sendMessage(message);
                }
            }, headers);
        }
    }

    @Autowired
    private ReferenceEllipsoid referenceEllipsoid;
    @Autowired
    private Geoid geoid;

    private PoseService poseService;
    private PoseGrpcService service;

    private static <T> ServerCallStreamObserver<T> mockObserver() {
        return mockObserver(new AtomicBoolean(true));
    }

    @SuppressWarnings("unchecked")
    private static <T> ServerCallStreamObserver<T> mockObserver(AtomicBoolean ready) {
        ServerCallStreamObserver<T> observer = mock(ServerCallStreamObserver.class);
        when(observer.isReady()).thenAnswer(invocation -> ready.get());
        return observer;
    }

    private static Server startServer(GrpcService grpcService) {
        Server server = Server.builder()
                .gracefulShutdownTimeoutMillis(0, 0)
                .requestTimeoutMillis(0)
                .service(grpcService)
                .build();
        server.start().join();
        return server;
    }

    private static PoseServiceGrpc.PoseServiceStub newClient(Server server) {
        return GrpcClients.builder("gproto+h2c://127.0.0.1:" + server.activeLocalPort() + "/")
                .responseTimeoutMillis(0)
                .build(PoseServiceGrpc.PoseServiceStub.class);
    }

    private void moveChild(double position) {
        poseService.create("test", "child", new Pose(
                new Vector3D(position, 0, 0), posed.core.NauticalAngles.IDENTITY));
    }

    @Before
    public void setUp() {
        poseService = new PoseService(referenceEllipsoid);
        poseService.createRoot("test");
        poseService.createRoot("other");
        service = new PoseGrpcService(poseService, geoid);
    }

    private <T> void assertRepliesUntilCancelled(ServerCallStreamObserver<T> observer) {
        ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);
        verify(observer).setOnCancelHandler(onCancel.capture());

        // The stream replies with the current value and then for any updates.
        verify(observer, timeout(TIMEOUT_MILLIS)).onNext(any());
        poseService.createRoot("test");
        verify(observer, timeout(TIMEOUT_MILLIS).times(2)).onNext(any());

        onCancel.getValue().run();

        poseService.createRoot("test");
        verify(observer, after(QUIET_MILLIS).times(2)).onNext(any());
        verify(observer, never()).onError(any());
        verify(observer, never()).onCompleted();
    }

    @Test
    public void testConvertGeodeticStreamStopsWhenCancelled() {
        ServerCallStreamObserver<ConvertLocalReply> observer = mockObserver();
        service.convertGeodeticStream(ConvertGeodeticRequest.newBuilder()
                .setFrame("test").setGeopose(GEOPOSE).build(), observer);
        assertRepliesUntilCancelled(observer);
    }

    @Test
    public void testConvertLocalStreamStopsWhenCancelled() {
        ServerCallStreamObserver<ConvertGeodeticReply> observer = mockObserver();
        service.convertLocalStream(ConvertLocalRequest.newBuilder()
                .setFrame("test").setPose(POSE).build(), observer);
        assertRepliesUntilCancelled(observer);
    }

    @Test
    public void testTransformStreamStopsWhenCancelled() {
        ServerCallStreamObserver<TransformReply> observer = mockObserver();
        service.transformStream(TRANSFORM, observer);
        assertRepliesUntilCancelled(observer);
    }

    @Test
    public void testClientCancellingCallCancelsStream() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        PoseService spiedPoseService = new PoseService(referenceEllipsoid) {
            @Override
            public Flux<Optional<Pose>> transformStream(String src, String dst, Pose pose) {
                return super.transformStream(src, dst, pose).doOnCancel(cancelled::countDown);
            }
        };

        Server server = startServer(GrpcService.builder()
                .addService(new PoseGrpcService(spiedPoseService, geoid))
                .build());
        try {
            newClient(server).transformStream(TRANSFORM, new CancelOnFirstReply());

            // No frames change, so only the disconnect can cancel the stream.
            assertThat(cancelled.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            server.stop().join();
        }
    }

    @Test
    public void testClientThatIsNotReadyOnlyGetsLatestValueOnceReady() throws Exception {
        int updates = 100;
        moveChild(0);
        AtomicBoolean ready = new AtomicBoolean();
        ServerCallStreamObserver<TransformReply> observer = mockObserver(ready);
        service.transformStream(CHILD_TRANSFORM, observer);

        for (int i = 1; i <= updates; i++) {
            moveChild(i);
        }
        verify(observer, after(QUIET_MILLIS).never()).onNext(any());

        ArgumentCaptor<Runnable> onReady = ArgumentCaptor.forClass(Runnable.class);
        verify(observer).setOnReadyHandler(onReady.capture());
        ready.set(true);
        onReady.getValue().run();

        Thread.sleep(QUIET_MILLIS);
        ArgumentCaptor<TransformReply> replies = ArgumentCaptor.forClass(TransformReply.class);
        verify(observer, atLeastOnce()).onNext(replies.capture());
        List<TransformReply> sent = replies.getAllValues();
        assertThat(sent.size(), is(lessThan(updates)));
        assertThat(sent.get(sent.size() - 1).getPose().getCartesian().getX(),
                is(closeTo(updates, 0.000001)));
    }

    @Test
    public void testClientThatStopsReadingDoesNotGetRepliesBuffered() throws Exception {
        // These replies are far more than fit in the flow control window for the stream.
        int updates = 20000;
        moveChild(0);
        ReplyCounter replyCounter = new ReplyCounter();
        Server server = startServer(GrpcService.builder()
                .addService(service)
                .intercept(replyCounter)
                .build());
        SlowClient client = new SlowClient(updates);
        try {
            newClient(server).transformStream(CHILD_TRANSFORM, client);
            assertThat(client.gotFirstReply.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));

            for (int i = 1; i <= updates; i++) {
                moveChild(i);
            }
            Thread.sleep(QUIET_MILLIS);
            assertThat(replyCounter.count.get(), is(lessThan(updates / 2)));

            // The stream recovers, and no matter what was skipped the client ends up current.
            client.readRemainingReplies();
            assertThat(client.gotLastReply.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            client.disconnect();
            server.stop().join();
        }
    }
}
