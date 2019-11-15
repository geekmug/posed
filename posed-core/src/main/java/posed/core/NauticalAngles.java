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

import static org.hipparchus.util.FastMath.PI;
import static org.hipparchus.util.FastMath.asin;
import static org.hipparchus.util.FastMath.atan2;
import static org.hipparchus.util.FastMath.copySign;
import static org.hipparchus.util.FastMath.toDegrees;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.RotationOrder;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.MathUtils;

import com.google.common.base.MoreObjects;

/**
 * A collection of angles representing rotation about three axes.  Positive
 * angles correspond to clockwise movement about the axis, from the perspective
 * of the origin. The rotations are applied in the order of nautical/Cardan
 * angles, which are applied as z-y'-x".
 */
public final class NauticalAngles {
    /** A set of angles equivalent to no roll, pitch, or yaw. */
    public static final NauticalAngles IDENTITY =
            new NauticalAngles(Rotation.IDENTITY);

    private final double roll;
    private final double pitch;
    private final double yaw;

    /**
     * Creates a set of angles from the given roll, pitch, and yaw.
     *
     * <p>If needsNormalization is true, then the angles will be normalized so
     * that pitch is between ±π/2 and the roll and yaw is between ±π.
     *
     * @param roll the angle around the x-axis. A positive angle corresponds to
     *     a clockwise movement around that axis.
     * @param pitch the angle around the y-axis. A positive angle corresponds to
     *     a clockwise movement around that axis.
     * @param yaw the angle around the z-axis. A positive angle corresponds to a
     *     clockwise movement around that axis.
     * @param needsNormalization if true, then the angles will be normalized
     */
    public NauticalAngles(final double roll, final double pitch,
            final double yaw, final boolean needsNormalization) {
        if (needsNormalization) {
            double r = MathUtils.normalizeAngle(roll, 0);
            double p = MathUtils.normalizeAngle(pitch, PI / 2);
            double y = MathUtils.normalizeAngle(yaw, 0);
            if (p > PI / 2.0) {
                // pitch is beyond the pole -> add 180 to longitude and roll
                r = MathUtils.normalizeAngle(r + PI, 0);
                p = PI - p;
                y = MathUtils.normalizeAngle(y + PI, 0);
            }
            this.roll = r;
            this.pitch = p;
            this.yaw = y;
        } else {
            this.roll = roll;
            this.pitch = pitch;
            this.yaw = yaw;
        }
    }

    /**
     * Creates a set of angles from the given roll, pitch, and yaw.
     *
     * <p>The angles will be normalized so that pitch is between ±π/2 and the
     * roll and yaw is between ±π.
     *
     * @param roll the angle around the x-axis. A positive angle corresponds to
     *     a clockwise movement around that axis.
     * @param pitch the angle around the y-axis. A positive angle corresponds to
     *     a clockwise movement around that axis.
     * @param yaw the angle around the z-axis. A positive angle corresponds to a
     *     clockwise movement around that axis.
     */
    public NauticalAngles(
            final double roll, final double pitch, final double yaw) {
        this(roll, pitch, yaw, true);
    }

    /**
     * Creates a set of angles given a Hipparchus Rotation.
     * @param r Hipparchus Rotation
     */
    public NauticalAngles(final Rotation r) {
        // r (Vector3D.plusK) coordinates are :
        //  -sin (theta), cos (theta) sin (phi), cos (theta) cos (phi)
        // (-r) (Vector3D.plusI) coordinates are :
        // cos (psi) cos (theta), sin (psi) cos (theta), -sin (theta)
        // and we can choose to have theta in the interval [-PI/2 ; +PI/2]
        Vector3D v1 = r.applyTo(Vector3D.PLUS_K);
        Vector3D v2 = r.applyInverseTo(Vector3D.PLUS_I);
        pitch = -asin(v2.getZ());

        /* If +i is exactly point down or up, then we can't get any yaw
         * information based on which way it's pointing, nor can we can any
         * information about the roll, because +k is coincident with +i,
         * also known as "gimbal lock" (loss of one degree of freedom). */
        if (v2.getZ() == -1 || v2.getZ() == 1) {
            /* We can recover the rotation around +k from the quarternion
             * to avoid losing the information. Because roll and yaw are
             * the same effective rotation in this situation, they are
             * additive, and there are an infinite number of ways to split
             * the total rotation around +k between those two rotations.
             * Since a rotation around +k is yaw when not looking nadir or
             * zenith, we choose to maintain that relationship. */
            roll = 0;
            yaw = -copySign(2, r.getQ0() * r.getQ2())
                    * atan2(r.getQ1(), r.getQ0());
        } else {
            roll = atan2(v1.getY(), v1.getZ());
            yaw = atan2(v2.getY(), v2.getX());
        }
    }

    /**
     * Creates a Hipparchus Rotation.
     * @return Hipparchus Rotation
     */
    public Rotation toRotation() {
        return new Rotation(
                RotationOrder.ZYX, RotationConvention.FRAME_TRANSFORM,
                yaw, pitch, roll);
    }

    /**
     * Creates a Hipparchus Rotation suitable for use in a Transform.
     * @return Hipparchus Rotation
     */
    public Rotation toTransformRotation() {
        RotationConvention convention = RotationConvention.VECTOR_OPERATOR;
        Rotation r1 = new Rotation(Vector3D.MINUS_I, roll, convention);
        Rotation r2 = new Rotation(Vector3D.MINUS_J, pitch, convention);
        Rotation r3 = new Rotation(Vector3D.MINUS_K, yaw, convention);
        return r1.compose(r2.compose(r3, convention), convention);
    }

    /**
     * Gets the angle around the x-axis (with zero y/z-axis rotation points to
     * the North). A positive angle corresponds to clockwise rotation about the
     * axis, from the perspective of the origin.
     *
     * @return an angular value in the range [-π, π]
     */
    public double getRoll() {
        return roll;
    }

    /**
     * Gets the angle around the y-axis (with zero x/z-axis rotation points to
     * the East). A positive angle corresponds to clockwise rotation about the
     * axis, from the perspective of the origin.
     *
     * @return an angular value in the range [-π/2, π/2]
     */
    public double getPitch() {
        return pitch;
    }

    /**
     * Gets the angle around the z-axis (with zero x/y-axis rotation points
     * down). A positive angle corresponds to clockwise rotation about the axis,
     * from the perspective of the origin.
     *
     * @return an angular value in the range [-π, π]
     */
    public double getYaw() {
        return yaw;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper("")
                .add("r", toDegrees(roll))
                .add("p", toDegrees(pitch))
                .add("y", toDegrees(yaw))
                .toString();
    }
}
