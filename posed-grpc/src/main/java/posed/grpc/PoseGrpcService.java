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
import org.orekit.time.AbsoluteDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import posed.core.GeodeticPose;
import posed.core.NauticalAngles;
import posed.core.Pose;
import posed.core.PoseService;
import posed.grpc.proto.ConvertReply;
import posed.grpc.proto.ConvertRequest;
import posed.grpc.proto.CreateReply;
import posed.grpc.proto.CreateRequest;
import posed.grpc.proto.CreateRootReply;
import posed.grpc.proto.CreateRootRequest;
import posed.grpc.proto.DeleteReply;
import posed.grpc.proto.DeleteRequest;
import posed.grpc.proto.Frame;
import posed.grpc.proto.PoseServiceGrpc.PoseServiceImplBase;
import posed.grpc.proto.TransformReply;
import posed.grpc.proto.TransformRequest;
import posed.grpc.proto.TraverseReply;
import posed.grpc.proto.TraverseRequest;
import posed.grpc.proto.UpdateReply;
import posed.grpc.proto.UpdateRequest;

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
    private final org.orekit.frames.Frame bodyFrame;

    /**
     * Creates a GRPC pose service using a given pose service.
     * @param poseService pose service
     */
    @Autowired
    public PoseGrpcService(final PoseService poseService) {
        this.poseService = checkNotNull(poseService);
        bodyFrame = poseService.getBodyShape().getBodyFrame();
    }

    @Override
    public final void createRoot(CreateRootRequest request,
            StreamObserver<CreateRootReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            poseService.createRoot(request.getFrame());
            responseObserver.onNext(CreateRootReply.newBuilder().build());
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
                    PosedProtos.decode(request.getOffset()));
            responseObserver.onNext(CreateReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void delete(DeleteRequest request,
            StreamObserver<DeleteReply> responseObserver) {
        try {
            poseService.remove(request.getFrame());
            responseObserver.onNext(DeleteReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void traverse(TraverseRequest request,
            StreamObserver<TraverseReply> responseObserver) {
        try {
            TraverseReply.Builder builder = TraverseReply.newBuilder();
            for (org.orekit.frames.Frame frame : poseService.traverse()) {
                if (frame == bodyFrame) {
                    continue;
                }

                Frame.Builder frameBuilder = Frame.newBuilder()
                        .setFrame(frame.getName());
                org.orekit.frames.Frame parent = frame.getParent();
                if (parent != bodyFrame) {
                    frameBuilder.setParent(parent.getName());
                    Transform xfrm = parent.getTransformTo(frame, AbsoluteDate.PAST_INFINITY);
                    frameBuilder.setOffset(PosedProtos.encode(new Pose(
                            xfrm.getCartesian().getPosition(),
                            new NauticalAngles(xfrm.getRotation()))));
                }
                builder.addFrames(frameBuilder);
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
                    PosedProtos.decode(request.getPose()));
            Optional<GeodeticPose> pose =
                    poseService.convert(request.getFrame(), Pose.IDENTITY);
            responseObserver.onNext(UpdateReply.newBuilder()
                    .setPose(PosedProtos.encode(pose.get()))
                    .build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convert(ConvertRequest request,
            StreamObserver<ConvertReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            ConvertReply.Builder builder = ConvertReply.newBuilder();
            switch (request.getValueCase()) {
            case POSE:
                Optional<GeodeticPose> geopose = poseService.convert(
                        request.getFrame(), PosedProtos.decode(request.getPose()));
                if (geopose.isPresent()) {
                    builder.setGeopose(PosedProtos.encode(geopose.get()));
                }
                break;
            case GEOPOSE:
                Optional<Pose> pose = poseService.convert(
                        request.getFrame(), PosedProtos.decode(request.getGeopose()));
                if (pose.isPresent()) {
                    builder.setPose(PosedProtos.encode(pose.get()));
                }
                break;
            default:
                throw new RuntimeException("no pose specified");
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(toGrpc(t));
        }
    }

    @Override
    public final void convertStream(ConvertRequest request,
        StreamObserver<ConvertReply> responseObserver) {
        try {
            checkArgument(!request.getFrame().isEmpty(), "no frame specified");

            switch (request.getValueCase()) {
            case POSE:
                poseService.convertStream(
                        request.getFrame(), PosedProtos.decode(request.getPose()))
                .subscribe(geopose -> {
                    ConvertReply.Builder builder = ConvertReply.newBuilder();
                    if (geopose.isPresent()) {
                        builder.setGeopose(PosedProtos.encode(geopose.get()));
                    }
                    responseObserver.onNext(builder.build());
                }, error -> {
                    responseObserver.onError(Status.INTERNAL.withCause(error).asException());
                }, () -> {
                    responseObserver.onCompleted();
                });
                break;
            case GEOPOSE:
                poseService.convertStream(
                        request.getFrame(), PosedProtos.decode(request.getGeopose()))
                .subscribe(pose -> {
                    ConvertReply.Builder builder = ConvertReply.newBuilder();
                    if (pose.isPresent()) {
                        builder.setPose(PosedProtos.encode(pose.get()));
                    }
                    responseObserver.onNext(builder.build());
                }, error -> {
                    responseObserver.onError(Status.INTERNAL.withCause(error).asException());
                }, () -> {
                    responseObserver.onCompleted();
                });
                break;
            default:
                throw new RuntimeException("no pose specified");
            }
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
}
