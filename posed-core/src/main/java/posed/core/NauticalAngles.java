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
import static org.hipparchus.util.FastMath.atan;
import static org.hipparchus.util.FastMath.atan2;
import static org.hipparchus.util.FastMath.copySign;
import static org.hipparchus.util.FastMath.sqrt;
import static org.hipparchus.util.FastMath.toDegrees;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.RotationOrder;
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
    public static final NauticalAngles IDENTITY = new NauticalAngles(0, 0, 0);

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
        /* This calculation originated with:
         *
         *   https://marc-b-reynolds.github.io/math/2017/04/18/TaitEuler.html
         *
         * The formulation above attributes the ambiguous rotation to roll, but
         * we have chosen to attribute it to yaw, since we most often have zero
         * roll. Additionally, the given formulation strictly tests for t != 0,
         * because that maps to a divide by zero condition, but when pitch is
         * exactly polar, cancellation effects on t0 and t1 prevent t from being
         * exactly zero and we amplify noise via atan2 calls for roll and yaw.
         * Empirically, a threshold of t > 1e-31 was determined as the point
         * where the atan2 results were more accurate than failing to the
         * degenerate case. */
        final double polarThreshold = 1e-31;

        double w = r.getQ0();
        double x = r.getQ1();
        double y = r.getQ2();
        double z = r.getQ3();

        double t0 = x * x - z * z;
        double t1 = w * w - y * y;
        double xx = (t0 + t1) / 2; // 1/2 x of x'
        double xy = x * y + w * z; // 1/2 y of x'
        double xz = w * y - x * z; // 1/2 z of x'
        double t = xx * xx + xy * xy; // cos(theta)^2
        double yz = 2 * (y * z + w * x); // z of y'

        pitch = atan(xz / sqrt(t));
        if (t > polarThreshold) {
            roll = atan2(yz, t1 - t0);
            yaw = atan2(xy, xx);
        } else {
            roll = 0;
            yaw = -copySign(2, w * y) * atan2(x, w);
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
        return new Rotation(
                RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR,
                -roll, -pitch, -yaw);
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
