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

package posed.core;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;

/** Helpers for working with geodetic {@link Frame}s. */
public final class GeodeticFrames {
    /**
     * Creates a transform from a given pose on a given body.
     * @param bodyShape geodetic body
     * @param pose geodetic pose
     * @return a transform from the pose to the body frame
     */
    public static Transform makeTransform(
            BodyShape bodyShape, GeodeticPose pose) {
        GeodeticPoint point = pose.getPosition();

        Vector3D xlat = bodyShape.transform(point).negate();

        /* The orientation of a GeodeticPose is defined in reference to the
         * topocentric frame at the position, so we need to recover the
         * rotation of the topocentric frame with respect to the GCRF body
         * frame, so that we can compose it with the given orientation. */
        Rotation topoRot = new Rotation(
                point.getNorth(), point.getEast(),
                Vector3D.PLUS_I, Vector3D.PLUS_J);
        Rotation rot = pose.getOrientation().toRotation().applyTo(topoRot);

        return new Transform(
                AbsoluteDate.PAST_INFINITY,
                new Transform(AbsoluteDate.PAST_INFINITY, xlat),
                new Transform(AbsoluteDate.PAST_INFINITY, rot));
    }

    /**
     * Gets a pose in a frame given a pose on a body.
     * @param bodyShape geodetic body
     * @param frame frame in which to express the pose
     * @param pose geodetic pose
     * @return a pose in a frame for the geodetic pose
     */
    public static Pose convert(BodyShape bodyShape, Frame frame, GeodeticPose pose) {
        checkNotNull(bodyShape);
        checkNotNull(frame);
        checkNotNull(pose);

        // Create a Frame based on the pose, so we can get it's transform.
        Frame poseFrame = new Frame(bodyShape.getBodyFrame(),
                makeTransform(bodyShape, pose), "");
        Transform xfrm = frame.getTransformTo(
                poseFrame, AbsoluteDate.PAST_INFINITY);
        return new Pose(
                xfrm.getTranslation().negate(),
                new NauticalAngles(xfrm.getRotation()));
    }

    /**
     * Gets a geodetic pose for a pose in a given frame.
     * @param bodyShape geodetic body
     * @param frame frame in which the pose is expressed
     * @param pose pose in the given frame
     * @return a geodetic pose for the given pose in a frame
     */
    public static GeodeticPose convert(BodyShape bodyShape, Frame frame, Pose pose) {
        checkNotNull(bodyShape);
        checkNotNull(frame);
        checkNotNull(pose);

        // Create a Frame based on the pose, so we can get it's transform.
        Frame poseFrame = new Frame(frame, Frames.makeTransform(pose), "");
        Transform xfrm = poseFrame.getTransformTo(
                bodyShape.getBodyFrame(), AbsoluteDate.PAST_INFINITY);
        GeodeticPoint point = bodyShape.transform(
                Vector3D.ZERO, poseFrame, AbsoluteDate.PAST_INFINITY);

        /* The orientation of a GeodeticPose is defined in reference to the
         * topocentric frame at the position, so we need to recover the
         * rotation of the topocentric frame with respect to the GCRF body
         * frame, so that we can compose it with the given orientation. */
        Rotation topoRot = new Rotation(
                point.getNorth(), point.getEast(),
                Vector3D.PLUS_I, Vector3D.PLUS_J);
        Rotation poseRot = topoRot.applyTo(xfrm.getRotation()).revert();

        return new GeodeticPose(point, new NauticalAngles(poseRot));
    }

    /**
     * Gets a geodetic pose for a pose in a given frame.
     * @param bodyShape geodetic body
     * @param frame frame from which to get a pose
     * @return a geodetic pose for the frame
     */
    public static GeodeticPose convert(BodyShape bodyShape, Frame frame) {
        checkNotNull(bodyShape);
        checkNotNull(frame);

        Transform xfrm = frame.getTransformTo(
                bodyShape.getBodyFrame(), AbsoluteDate.PAST_INFINITY);
        GeodeticPoint point = bodyShape.transform(
                Vector3D.ZERO, frame, AbsoluteDate.PAST_INFINITY);

        /* The orientation of a GeodeticPose is defined in reference to the
         * topocentric frame at the position, so we need to recover the
         * rotation of the topocentric frame with respect to the GCRF body
         * frame, so that we can compose it with the given orientation. */
        Rotation topoRot = new Rotation(
                point.getNorth(), point.getEast(),
                Vector3D.PLUS_I, Vector3D.PLUS_J);
        Rotation poseRot = topoRot.applyTo(xfrm.getRotation()).revert();

        return new GeodeticPose(point, new NauticalAngles(poseRot));
    }

    private GeodeticFrames() {}
}
