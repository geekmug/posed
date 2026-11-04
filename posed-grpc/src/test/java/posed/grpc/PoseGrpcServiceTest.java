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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ReferenceEllipsoid referenceEllipsoid;
    @Autowired
    private Geoid geoid;

    private PoseService poseService;
    private PoseGrpcService service;

    @SuppressWarnings("unchecked")
    private static <T> ServerCallStreamObserver<T> mockObserver() {
        return mock(ServerCallStreamObserver.class);
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

        Server server = Server.builder()
                .service(GrpcService.builder()
                        .addService(new PoseGrpcService(spiedPoseService, geoid))
                        .build())
                .build();
        server.start().join();
        try {
            int port = server.activeLocalPort();
            GrpcClients.newClient("gproto+h2c://127.0.0.1:" + port + "/",
                    PoseServiceGrpc.PoseServiceStub.class)
            .transformStream(TRANSFORM, new CancelOnFirstReply());

            // No frames change, so only the disconnect can cancel the stream.
            assertThat(cancelled.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            server.stop().join();
        }
    }
}
