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

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.MathUtils;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.models.earth.Geoid;
import org.orekit.time.AbsoluteDate;

import posed.core.GeodeticPose;
import posed.core.NauticalAngles;
import posed.core.Pose;

final class PosedProtos {
    public static NauticalAngles decode(posed.grpc.proto.Quaternion quaternion) {
        return new NauticalAngles(new Rotation(
                quaternion.getW(), quaternion.getX(),
                quaternion.getY(), quaternion.getZ(), true));
    }

    public static posed.grpc.proto.Quaternion encode(Rotation rotation) {
        return posed.grpc.proto.Quaternion.newBuilder()
                .setW(rotation.getQ0())
                .setX(rotation.getQ1())
                .setY(rotation.getQ2())
                .setZ(rotation.getQ3())
                .build();
    }

    public static NauticalAngles decode(posed.grpc.proto.NauticalAngles angles) {
        return new NauticalAngles(
                toRadians(angles.getRoll()),
                toRadians(angles.getPitch()),
                toRadians(angles.getYaw()));
    }

    public static posed.grpc.proto.NauticalAngles encode(NauticalAngles angles,
            boolean normalizeForGeoPose) {
        double yaw;
        if (normalizeForGeoPose) {
            yaw = MathUtils.normalizeAngle(angles.getYaw(), PI);
        } else {
            yaw = angles.getYaw();
        }
        return posed.grpc.proto.NauticalAngles.newBuilder()
                .setRoll(toDegrees(angles.getRoll()))
                .setPitch(toDegrees(angles.getPitch()))
                .setYaw(toDegrees(yaw))
                .build();
    }

    public static GeodeticPoint decode(Geoid geoid,
            posed.grpc.proto.GeodeticPositionRequest position) {
        double lat = toRadians(position.getLatitude());
        double lon = toRadians(position.getLongitude());
        switch (position.getAltitudeCase()) {
        case AMSL:
            double undulation = geoid.getUndulation(
                    lat, lon, AbsoluteDate.PAST_INFINITY);
            return new GeodeticPoint(lat, lon, position.getAmsl() + undulation);
        case HAE:
            return new GeodeticPoint(lat, lon, position.getHae());
        default:
            throw new IllegalArgumentException("no altitude specified");
        }
    }

    public static posed.grpc.proto.GeodeticPositionReply encode(Geoid geoid,
            GeodeticPoint point) {
        double undulation = geoid.getUndulation(
                point.getLatitude(), point.getLongitude(),
                AbsoluteDate.PAST_INFINITY);
        return posed.grpc.proto.GeodeticPositionReply.newBuilder()
                .setLatitude(toDegrees(point.getLatitude()))
                .setLongitude(toDegrees(point.getLongitude()))
                .setHae(point.getAltitude())
                .setAmsl(point.getAltitude() - undulation)
                .build();
    }

    public static GeodeticPose decode(Geoid geoid,
            posed.grpc.proto.GeodeticPoseRequest pose) {
        switch (pose.getRotationCase()) {
        case ANGLES:
            return new GeodeticPose(
                    decode(geoid, pose.getPosition()),
                    decode(pose.getAngles()));
        case QUATERNION:
            return new GeodeticPose(
                    decode(geoid, pose.getPosition()),
                    decode(pose.getQuaternion()));
        default:
            throw new IllegalArgumentException("no rotation specified");
        }
    }

    public static posed.grpc.proto.GeodeticPoseReply encode(Geoid geoid,
            GeodeticPose pose) {
        return posed.grpc.proto.GeodeticPoseReply.newBuilder()
                .setPosition(encode(geoid, pose.getPosition()))
                .setAngles(encode(pose.getOrientation(), true))
                .setQuaternion(encode(pose.getOrientation().toRotation()))
                .build();
    }

    public static Vector3D decode(posed.grpc.proto.Position position) {
        return new Vector3D(position.getX(), position.getY(), position.getZ());
    }

    public static posed.grpc.proto.Position encode(Vector3D position) {
        return posed.grpc.proto.Position.newBuilder()
                .setX(position.getX())
                .setY(position.getY())
                .setZ(position.getZ())
                .build();
    }

    public static Pose decode(posed.grpc.proto.PoseRequest pose) {
        switch (pose.getRotationCase()) {
        case ANGLES:
            return new Pose(decode(pose.getPosition()), decode(pose.getAngles()));
        case QUATERNION:
            return new Pose(decode(pose.getPosition()), decode(pose.getQuaternion()));
        default:
            throw new IllegalArgumentException("no rotation specified");
        }
    }

    public static posed.grpc.proto.PoseReply encode(Pose pose) {
        return posed.grpc.proto.PoseReply.newBuilder()
                .setPosition(encode(pose.getPosition()))
                .setAngles(encode(pose.getOrientation(), false))
                .setQuaternion(encode(pose.getOrientation().toRotation()))
                .build();
    }

    private PosedProtos() {}
}
