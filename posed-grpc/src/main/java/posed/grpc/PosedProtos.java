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

import static org.hipparchus.util.FastMath.PI;
import static org.hipparchus.util.FastMath.toDegrees;
import static org.hipparchus.util.FastMath.toRadians;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.MathUtils;
import org.orekit.bodies.GeodeticPoint;

import posed.core.GeodeticPose;
import posed.core.NauticalAngles;
import posed.core.Pose;
import posed.grpc.proto.GeoPose;
import posed.grpc.proto.GeoPosition;
import posed.grpc.proto.Orientation;
import posed.grpc.proto.Position;

final class PosedProtos {
    public static NauticalAngles decode(Orientation orientation) {
        return new NauticalAngles(
                toRadians(orientation.getRoll()),
                toRadians(orientation.getPitch()),
                toRadians(orientation.getYaw()));
    }

    public static Orientation encode(NauticalAngles angles,
            boolean normalizeForGeoPose) {
        double yaw;
        if (normalizeForGeoPose) {
            yaw = MathUtils.normalizeAngle(angles.getYaw(), PI);
        } else {
            yaw = angles.getYaw();
        }
        return Orientation.newBuilder()
                .setRoll(toDegrees(angles.getRoll()))
                .setPitch(toDegrees(angles.getPitch()))
                .setYaw(toDegrees(yaw))
                .build();
    }

    public static GeodeticPoint decode(GeoPosition position) {
        return new GeodeticPoint(
                toRadians(position.getLatitude()),
                toRadians(position.getLongitude()),
                position.getAltitude());
    }

    public static GeoPosition encode(GeodeticPoint point) {
        return GeoPosition.newBuilder()
                .setLatitude(toDegrees(point.getLatitude()))
                .setLongitude(toDegrees(point.getLongitude()))
                .setAltitude(point.getAltitude())
                .build();
    }

    public static GeodeticPose decode(GeoPose pose) {
        return new GeodeticPose(
                decode(pose.getPosition()),
                decode(pose.getOrientation()));
    }

    public static GeoPose encode(GeodeticPose pose) {
        return GeoPose.newBuilder()
                .setPosition(encode(pose.getPosition()))
                .setOrientation(encode(pose.getOrientation(), true))
                .build();
    }

    public static Vector3D decode(Position position) {
        return new Vector3D(position.getX(), position.getY(), position.getZ());
    }

    public static Position encode(Vector3D position) {
        return Position.newBuilder()
                .setX(position.getX())
                .setY(position.getY())
                .setZ(position.getZ())
                .build();
    }

    public static Pose decode(posed.grpc.proto.Pose pose) {
        return new Pose(decode(pose.getPosition()), decode(pose.getOrientation()));
    }

    public static posed.grpc.proto.Pose encode(Pose pose) {
        return posed.grpc.proto.Pose.newBuilder()
                .setPosition(encode(pose.getPosition()))
                .setOrientation(encode(pose.getOrientation(), false))
                .build();
    }

    private PosedProtos() {}
}
