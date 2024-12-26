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

package posed.web;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.internal.shaded.guava.collect.ImmutableList;
import com.linecorp.armeria.server.ServiceRequestContext;

import posed.core.GeodeticFrames;
import posed.core.GeodeticPose;
import posed.core.PoseService;
import posed.core.frametree.ChangeTrackingFrameTree;
import posed.czml.CzmlArcType;
import posed.czml.CzmlBoolean;
import posed.czml.CzmlColor;
import posed.czml.CzmlDouble;
import posed.czml.CzmlModel;
import posed.czml.CzmlOrientation;
import posed.czml.CzmlPacket;
import posed.czml.CzmlPolyline;
import posed.czml.CzmlPolylineMaterial;
import posed.czml.CzmlPosition;
import posed.czml.CzmlPositionList;
import posed.czml.CzmlShadowMode;
import posed.czml.CzmlSolidColorMaterial;
import posed.czml.CzmlUri;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** REST controller for produce a CZML Server-Side Events stream. */
@RestController
public class CzmlController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PoseService poseService;
    private final ReferenceEllipsoid referenceEllipsoid;
    private final Frame bodyFrame;

    /**
     * Creates a REST controller service using a given pose service.
     * @param poseService pose service
     */
    @Autowired
    public CzmlController(final PoseService poseService) {
        this.poseService = checkNotNull(poseService);
        referenceEllipsoid = poseService.getReferenceEllipsoid();
        bodyFrame = referenceEllipsoid.getBodyFrame();
    }

    private CzmlPosition getPosition(GeodeticPoint point) {
        // Give the position in ECEF to avoid heightReference issues.
        Vector3D ecef = referenceEllipsoid.transform(point);
        return new CzmlPosition().withCartesian(ImmutableList.of(
                ecef.getX(), ecef.getY(), ecef.getZ()));
    }

    private static final Rotation NED_TO_ENU =
            new Rotation(Vector3D.PLUS_K, Vector3D.MINUS_K);

    private CzmlOrientation getOrientation(GeodeticPose pose) {
        /* Annoyingly, CZML Orientations are relative to the GCRF frame, so we
         * need to bake in the rotation for the given geodetic point.
         * Additionally, the unitQuaternion is ENU, so we need to flip it over.
         */
        Rotation r = NED_TO_ENU.applyTo(pose.getOrientation().toRotation()
                .applyTo(GeodeticFrames.getTopocentricRotation(pose.getPosition())));
        // Note: CZML quaternions are in xyzw order.
        return new CzmlOrientation().withUnitQuaternion(ImmutableList.of(
                r.getQ1(), r.getQ2(), r.getQ3(), r.getQ0()));
    }

    private static final CzmlUri BALL_GLB_URI =
            new CzmlUri().withUri("/files/ball.glb");
    private static final CzmlPolylineMaterial SOLID_MAGENTA_MATERIAL =
            new CzmlPolylineMaterial()
            .withSolidColor(new CzmlSolidColorMaterial()
                    .withColor(new CzmlColor()
                            .withRgbaf(ImmutableList.of(1, 0, 1, 1))));
    private static final CzmlShadowMode SHADOW_DISABLED =
            new CzmlShadowMode().withShadowMode("DISABLED");
    private static final CzmlDouble POLYLINE_WIDTH =
            new CzmlDouble().withNumber(3.0);
    private static final CzmlBoolean BOOLEAN_FALSE =
            new CzmlBoolean().withBoolean(Boolean.FALSE);
    private static final CzmlArcType ARCTYPE_NONE =
            new CzmlArcType().withArcType("NONE");

    private CzmlPacket getCzmlPacketForFrame(Frame frame, double scale) {
        GeodeticPose pose;
        try {
            pose = GeodeticFrames.convert(referenceEllipsoid, frame);
        } catch (OrekitException e) {
            return null;
        }

        CzmlModel model = new CzmlModel();
        model.setGltf(BALL_GLB_URI);
        model.setScale(new CzmlDouble().withNumber(scale));
        model.setShadows(SHADOW_DISABLED);

        CzmlPacket packet = new CzmlPacket();
        packet.setId(frame.getName());
        packet.setModel(model);
        packet.setPosition(getPosition(pose.getPosition()));
        packet.setOrientation(getOrientation(pose));

        Frame parent = frame.getParent();
        GeodeticPose parentPose = null;
        if (parent != bodyFrame) {
            try {
                parentPose = GeodeticFrames.convert(referenceEllipsoid, parent);
            } catch (OrekitException e) {
                // ignore
            }
        }

        // Draw a polyline back to the parent if it's on the map.
        if (parentPose != null) {
            CzmlPositionList positions = new CzmlPositionList();
            // Give the position in ECEF to avoid heightReference issues.
            Vector3D ourEcef = referenceEllipsoid.transform(pose.getPosition());
            Vector3D parentEcef = referenceEllipsoid.transform(parentPose.getPosition());
            positions.setCartesian(ImmutableList.of(
                    ourEcef.getX(), ourEcef.getY(), ourEcef.getZ(),
                    parentEcef.getX(), parentEcef.getY(), parentEcef.getZ()));

            CzmlPolyline polyline = new CzmlPolyline();
            polyline.setArcType(ARCTYPE_NONE);
            polyline.setFollowSurface(BOOLEAN_FALSE);
            polyline.setMaterial(SOLID_MAGENTA_MATERIAL);
            polyline.setPositions(positions);
            polyline.setShadows(SHADOW_DISABLED);
            polyline.setWidth(POLYLINE_WIDTH);

            packet.setPolyline(polyline);
        }

        return packet;
    }

    private CzmlPacket getCzmlDocument() {
        CzmlPacket packet = new CzmlPacket();
        packet.setId("document");
        packet.setVersion("1.0");
        return packet;
    }

    private Flux<CzmlPacket> getCzmlPacket(double scale) {
        return Flux.concat(ImmutableList.of(
            Flux.just(getCzmlDocument()),
            poseService.getChangeStream().handle((change, sink) -> {
                if (change instanceof ChangeTrackingFrameTree.Created) {
                    Frame frame = ((ChangeTrackingFrameTree.Created) change).getFrame();
                    if (frame == bodyFrame) {
                        return;
                    }

                    CzmlPacket packet = getCzmlPacketForFrame(frame, scale);
                    if (packet != null) {
                        sink.next(packet);
                    }
                } else if (change instanceof ChangeTrackingFrameTree.Removed) {
                    String name = ((ChangeTrackingFrameTree.Removed) change).getName();
                    sink.next(new CzmlPacket().withId(name).withDelete(Boolean.TRUE));
                }
            })));
    }

    /**
     * Gets a KML document for the current pose service.
     * @param scale scale at which to render the pose balls
     * @return a KML document
     */
    @GetMapping(path = "/posed.czml", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public final Flux<ServerSentEvent<String>> getPosedCzml(
            @RequestParam(name = "scale", required = false, defaultValue = "1.0") double scale) {
        ServiceRequestContext ctx = (ServiceRequestContext) RequestContext.current();
        // Disable the request timeout on this SSE stream:
        ctx.clearRequestTimeout();
        // Set the content-type header for this SSE stream:
        ctx.addAdditionalResponseHeader("content-type", MediaType.TEXT_EVENT_STREAM_VALUE);
        // Start streaming CZML documents:
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder();
        builder.event("czml");
        return getCzmlPacket(scale).map(packet -> {
            try {
                builder.data(mapper.writeValueAsString(packet));
            } catch (JsonProcessingException e) {
                builder.data("{}");
            }
            return builder.build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
