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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.closeTo;
import static org.hipparchus.util.FastMath.PI;
import static org.hipparchus.util.FastMath.toRadians;
import static org.junit.Assert.assertThat;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.junit.Test;

public class NauticalAnglesTest {
    private static final double ANGLE_ERROR = toRadians(0.000001);

    @Test
    public void testNormalizeYaw() {
        NauticalAngles a = new NauticalAngles(0, 0, 3 * PI / 2);
        assertThat(a.getRoll(), is(closeTo(0.0, ANGLE_ERROR)));
        assertThat(a.getPitch(), is(closeTo(0.0, ANGLE_ERROR)));
        assertThat(a.getYaw(), is(closeTo(-PI / 2, ANGLE_ERROR)));
    }

    @Test
    public void testNormalizePitch() {
        NauticalAngles a = new NauticalAngles(PI / 4, 3 * PI / 4, PI / 2);
        assertThat(a.getRoll(), is(closeTo(-3 * PI / 4, ANGLE_ERROR)));
        assertThat(a.getPitch(), is(closeTo(PI / 4, ANGLE_ERROR)));
        assertThat(a.getYaw(), is(closeTo(-PI / 2, ANGLE_ERROR)));
    }

    @Test
    public void testNormalizeWithRotation() {
        double roll = PI / 4;
        double pitch = 3 * PI / 4;
        double yaw = PI / 2;

        // Same test case as above with normalization turned off.
        NauticalAngles a = new NauticalAngles(roll, pitch, yaw, false);
        // Round-trip through Rotation to normalize it.
        NauticalAngles b = new NauticalAngles(a.toRotation());
        // Use our normalization to compare with Rotation output.
        NauticalAngles c = new NauticalAngles(roll, pitch, yaw);

        assertThat(b.getRoll(), is(closeTo(c.getRoll(), ANGLE_ERROR)));
        assertThat(b.getPitch(), is(closeTo(c.getPitch(), ANGLE_ERROR)));
        assertThat(b.getYaw(), is(closeTo(c.getYaw(), ANGLE_ERROR)));
    }

    @Test
    public void testGimbalLockUp() {
        NauticalAngles a = new NauticalAngles(new Rotation(0.5, -0.5, 0.5, 0.5, true));
        assertThat(a.getRoll(), is(closeTo(0.0, ANGLE_ERROR)));
        assertThat(a.getPitch(), is(closeTo(PI / 2, ANGLE_ERROR)));
        assertThat(a.getYaw(), is(closeTo(PI / 2, ANGLE_ERROR)));
    }

    @Test
    public void testGimbalLockUpWithSmallYaw() {
        NauticalAngles a = new NauticalAngles(
                new NauticalAngles(0, PI / 2, toRadians(1)).toRotation());
        assertThat(a.getRoll(), is(closeTo(0.0, ANGLE_ERROR)));
        assertThat(a.getPitch(), is(closeTo(PI / 2, ANGLE_ERROR)));
        assertThat(a.getYaw(), is(closeTo(toRadians(1), ANGLE_ERROR)));
    }

    @Test
    public void testNearlyGimbalLockUpWithYaws() {
        // Any closer to the pole and our error starts to increase
        double pitch = toRadians(90 - 0.000001);
        for (int i = 0; i < 18000; i += 25) {
            double angle = i / 100.0;
            NauticalAngles a = new NauticalAngles(
                    new NauticalAngles(toRadians(angle), pitch, toRadians(angle))
                    .toRotation());
            assertThat(a.getRoll(), is(closeTo(toRadians(angle), ANGLE_ERROR)));
            assertThat(a.getPitch(), is(closeTo(pitch, ANGLE_ERROR)));
            assertThat(a.getYaw(), is(closeTo(toRadians(angle), ANGLE_ERROR)));
        }
    }

    @Test
    public void testGimbalLockUpWithYaws() {
        for (int i = 0; i < 18000; i += 25) {
            double angle = i / 100.0;
            NauticalAngles a = new NauticalAngles(
                    new NauticalAngles(0, PI / 2, toRadians(angle)).toRotation());
            assertThat(a.getRoll(), is(closeTo(0, ANGLE_ERROR)));
            assertThat(a.getPitch(), is(closeTo(PI / 2, ANGLE_ERROR)));
            assertThat(a.getYaw(), is(closeTo(toRadians(angle), ANGLE_ERROR)));
        }
    }

    @Test
    public void testRotationRoundtrip() {
        for (int r = -31; r < 31; r += 3) {
            double roll = r / 10.0;
            for (int p = -155; p < 155; p += 30) {
                double pitch = p / 100.0;
                for (int y = -31; y < 31; y += 3) {
                    double yaw = y / 10.0;
                    NauticalAngles a = new NauticalAngles(
                            new NauticalAngles(roll, pitch, yaw).toRotation());
                    assertThat(a.getRoll(), is(closeTo(roll, ANGLE_ERROR)));
                    assertThat(a.getPitch(), is(closeTo(pitch, ANGLE_ERROR)));
                    assertThat(a.getYaw(), is(closeTo(yaw, ANGLE_ERROR)));
                }
            }
        }
    }

    @Test
    public void testGimbalLockDown() {
        NauticalAngles a = new NauticalAngles(new Rotation(0.5, -0.5, -0.5, -0.5, true));
        assertThat(a.getRoll(), is(closeTo(0.0, ANGLE_ERROR)));
        assertThat(a.getPitch(), is(closeTo(-PI / 2, ANGLE_ERROR)));
        assertThat(a.getYaw(), is(closeTo(-PI / 2, ANGLE_ERROR)));
    }
}
