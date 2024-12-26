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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.orekit.frames.Transform;
import org.orekit.models.earth.Geoid;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.time.AbsoluteDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import posed.core.GeodeticFrames;
import posed.core.GeodeticPose;
import posed.core.NauticalAngles;
import posed.core.Pose;
import posed.core.PoseService;
import posed.core.UnknownTransformException;
import posed.grpc.proto.ConvertGeodeticReply;
import posed.grpc.proto.ConvertGeodeticRequest;
import posed.grpc.proto.ConvertLocalReply;
import posed.grpc.proto.ConvertLocalRequest;
import posed.grpc.proto.CreateReply;
import posed.grpc.proto.CreateRequest;
import posed.grpc.proto.CreateRootReply;
import posed.grpc.proto.CreateRootRequest;
import posed.grpc.proto.DeleteReply;
import posed.grpc.proto.DeleteRequest;
import posed.grpc.proto.Frame;
import posed.grpc.proto.PoseServiceGrpc.PoseServiceImplBase;
import posed.grpc.proto.SubgraphReply;
import posed.grpc.proto.SubgraphRequest;
import posed.grpc.proto.TransformReply;
import posed.grpc.proto.TransformRequest;
import posed.grpc.proto.TraverseReply;
import posed.grpc.proto.TraverseRequest;
import posed.grpc.proto.UpdateReply;
import posed.grpc.proto.UpdateRequest;
import reactor.core.scheduler.Schedulers;

/** GRPC service instance for managing poses. */
@Service
public class PoseGrpcService extends PoseServiceImplBase {
    private static Throwable toGrpc(Throwable t) {
        Status status;
        if (t instanceof StatusException) {
            status = ((StatusException) t).getStatus();
        } else if (t instanceof StatusRuntimeException) {
            status = ((StatusRuntimeException) t).getStatus();
        } else if (t instanceof RuntimeException) {
            status = Status.FAILED_PRECONDITION;
        } else {
            status = Status.UNKNOWN;
        }
        return status.withDescription(t.getMessage()).asException();
    }

    private final PoseService poseService;
    private final Geoid geoid;
    private final ReferenceEllipsoid referenceEllipsoid;
    private final org.orekit.frames.Frame bodyFrame;

    /**
     * Creates a GRPC pose service using a given pose service.
     * @param poseService pose service
     * @param geoid geoid to use when encoding and decoding AMSL
     */
    @Autowired
    public PoseGrpcService(final PoseService poseService, final Geoid geoid) {
        this.poseService = checkNotNull(poseService);
        this.geoid = checkNotNull(geoid);
        referenceEllipsoid = poseService.getReferenceEllipsoid();
        bodyFrame = referenceEllipsoid.getBodyFrame();
    }

    @Override
    public final void createRoot(CreateRootRequest request,
            StreamObserver<CreateRootReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            poseService.createRoot(request.getFrame());
            Optional<GeodeticPose> actual =
                    poseService.convert(request.getFrame(), Pose.IDENTITY);
            if (actual.isPresent()) {
                responseObserver.onNext(CreateRootReply.newBuilder()
                        .setGeopose(PosedProtos.encode(geoid, actual.get()))
                        .build());
            } else {
                responseObserver.onNext(CreateRootReply.getDefaultInstance());
            }
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void create(CreateRequest request,
            StreamObserver<CreateReply> responseObserver) {
        try {
            checkArgument(!request.getParent().isEmpty(), "no parent specified");
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            poseService.create(
                    request.getParent(), request.getFrame(),
                    PosedProtos.decode(request.getPose()));
            Optional<GeodeticPose> actual =
                    poseService.convert(request.getFrame(), Pose.IDENTITY);
            if (actual.isPresent()) {
                responseObserver.onNext(CreateReply.newBuilder()
                        .setGeopose(PosedProtos.encode(geoid, actual.get()))
                        .build());
            } else {
                responseObserver.onNext(CreateReply.getDefaultInstance());
            }
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void delete(DeleteRequest request,
            StreamObserver<DeleteReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            ImmutableList<String> toRemove;
            if (!request.getRecursive()) {
                toRemove = ImmutableList.of(request.getFrame());
            } else {
                toRemove = Streams.stream(poseService.traverse(request.getFrame()))
                        .map(org.orekit.frames.Frame::getName)
                        .collect(ImmutableList.toImmutableList());
            }

            long removed = 0;
            for (String name : toRemove.reverse()) {
                try {
                    poseService.remove(name);
                    removed++;
                } catch (Throwable t) {
                    // ignore
                }
            }

            responseObserver.onNext(DeleteReply.newBuilder()
                    .setRemoved(removed).build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    private Frame makeFrame(org.orekit.frames.Frame frame) {
        Frame.Builder frameBuilder = Frame.newBuilder()
                .setFrame(frame.getName());
        org.orekit.frames.Frame parent = frame.getParent();
        if (parent != bodyFrame) {
            frameBuilder.setParent(parent.getName());

            Transform xfrm = parent.getTransformTo(frame, AbsoluteDate.PAST_INFINITY);
            frameBuilder.setPose(PosedProtos.encode(new Pose(
                    xfrm.getCartesian().getPosition().negate(),
                    new NauticalAngles(xfrm.getRotation()))));
        }

        GeodeticPose geopose;
        try {
            geopose = GeodeticFrames.convert(referenceEllipsoid, frame);
        } catch (UnknownTransformException e) {
            geopose = null;
        }
        if (geopose != null) {
            frameBuilder.setGeopose(PosedProtos.encode(geoid, geopose));
        }
        return frameBuilder.build();
    }

    @Override
    public final void subgraph(SubgraphRequest request,
            StreamObserver<SubgraphReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            SubgraphReply.Builder builder = SubgraphReply.newBuilder();
            for (org.orekit.frames.Frame frame : poseService.subgraph(request.getFrame())) {
                if (frame == bodyFrame) {
                    continue;
                }
                builder.addFrames(makeFrame(frame));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void traverse(TraverseRequest request,
            StreamObserver<TraverseReply> responseObserver) {
        try {
            final Iterable<org.orekit.frames.Frame> traverse;
            if (request.getFrame().isEmpty()) {
                traverse = poseService.traverse();
            } else {
                traverse = poseService.traverse(request.getFrame());
            }
            TraverseReply.Builder builder = TraverseReply.newBuilder();
            for (org.orekit.frames.Frame frame : traverse) {
                if (frame == bodyFrame) {
                    continue;
                }
                builder.addFrames(makeFrame(frame));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void update(UpdateRequest request,
            StreamObserver<UpdateReply> responseObserver) {
        try {
            poseService.update(request.getFrame(),
                    PosedProtos.decode(geoid, request.getGeopose()));
            GeodeticPose actual =
                    poseService.convert(request.getFrame(), Pose.IDENTITY).get();
            responseObserver.onNext(UpdateReply.newBuilder()
                    .setGeopose(PosedProtos.encode(geoid, actual))
                    .build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convertGeodetic(ConvertGeodeticRequest request,
            StreamObserver<ConvertLocalReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            Optional<Pose> pose = poseService.convert(
                    request.getFrame(), PosedProtos.decode(geoid, request.getGeopose()));
            ConvertLocalReply.Builder builder = ConvertLocalReply.newBuilder();
            if (pose.isPresent()) {
                builder.setPose(PosedProtos.encode(pose.get()));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convertGeodeticStream(ConvertGeodeticRequest request,
            StreamObserver<ConvertLocalReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            poseService.convertStream(
                    request.getFrame(), PosedProtos.decode(geoid, request.getGeopose()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(pose -> {
                ConvertLocalReply.Builder builder = ConvertLocalReply.newBuilder();
                if (pose.isPresent()) {
                    builder.setPose(PosedProtos.encode(pose.get()));
                }
                responseObserver.onNext(builder.build());
            }, error -> {
                responseObserver.onError(Status.INTERNAL.withCause(error).asException());
            }, () -> {
                responseObserver.onCompleted();
            });
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convertLocal(ConvertLocalRequest request,
            StreamObserver<ConvertGeodeticReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            ConvertGeodeticReply.Builder builder = ConvertGeodeticReply.newBuilder();
            Optional<GeodeticPose> geopose = poseService.convert(
                    request.getFrame(), PosedProtos.decode(request.getPose()));
            if (geopose.isPresent()) {
                builder.setGeopose(PosedProtos.encode(geoid, geopose.get()));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convertLocalStream(ConvertLocalRequest request,
            StreamObserver<ConvertGeodeticReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            poseService.convertStream(
                    request.getFrame(), PosedProtos.decode(request.getPose()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(pose -> {
                ConvertGeodeticReply.Builder builder = ConvertGeodeticReply.newBuilder();
                if (pose.isPresent()) {
                    builder.setGeopose(PosedProtos.encode(geoid, pose.get()));
                }
                responseObserver.onNext(builder.build());
            }, error -> {
                responseObserver.onError(Status.INTERNAL.withCause(error).asException());
            }, () -> {
                responseObserver.onCompleted();
            });
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void transform(TransformRequest request,
            StreamObserver<TransformReply> responseObserver) {
        try {
            checkArgument(!request.getSrcFrame().isEmpty(), "no source frame specified");
            checkArgument(!request.getDstFrame().isEmpty(), "no destination frame specified");

            Optional<Pose> pose = poseService.transform(
                    request.getSrcFrame(), request.getDstFrame(),
                    PosedProtos.decode(request.getPose()));
            if (pose.isPresent()) {
                responseObserver.onNext(TransformReply.newBuilder()
                        .setPose(PosedProtos.encode(pose.get())).build());
            } else {
                throw new IllegalArgumentException("undefined frame");
            }
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void transformStream(TransformRequest request,
            StreamObserver<TransformReply> responseObserver) {
        try {
            checkArgument(!request.getSrcFrame().isEmpty(), "no source frame specified");
            checkArgument(!request.getDstFrame().isEmpty(), "no destination frame specified");

            poseService.transformStream(
                    request.getSrcFrame(), request.getDstFrame(),
                    PosedProtos.decode(request.getPose()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(pose -> {
                TransformReply.Builder builder = TransformReply.newBuilder();
                if (pose.isPresent()) {
                    builder.setPose(PosedProtos.encode(pose.get()));
                }
                responseObserver.onNext(builder.build());
            }, error -> {
                responseObserver.onError(Status.INTERNAL.withCause(error).asException());
            }, () -> {
                responseObserver.onCompleted();
            });
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }
}
