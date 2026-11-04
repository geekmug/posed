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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hipparchus.util.FastMath.toRadians;
import static posed.core.PosedMatchers.closeTo;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;

public class FramesTest {
    // Acceptable amount of error in geospatial calculations:
    private static final double ANGLE_ERROR = toRadians(0.0000001);
    private static final double POSITION_ERROR = 0.0000001;

    @Test
    public void testTransform() {
        Frame src = FramesFactory.getGCRF();
        Frame dst = new Frame(src, Transform.IDENTITY, "");
        Pose pose = Frames.transform(FramesFactory.getGCRF(), dst, Pose.IDENTITY);
        assertThat(pose, is(closeTo(Pose.IDENTITY, POSITION_ERROR, ANGLE_ERROR)));
    }

    @Test
    public void testTransformToSameFrameKeepsPose() {
        Frame frame = FramesFactory.getGCRF();
        Pose pose = new Pose(new Vector3D(1, 2, 3), new NauticalAngles(0.1, 0.2, 0.3));
        assertThat(Frames.transform(frame, frame, pose),
                is(closeTo(pose, POSITION_ERROR, ANGLE_ERROR)));
    }

    @Test
    public void testTransformIntoRotatedFrame() {
        // The destination faces the source's right, so the source's front is on its left.
        Frame src = FramesFactory.getGCRF();
        Frame dst = new Frame(src, Frames.makeTransform(new Pose(
                Vector3D.ZERO, new NauticalAngles(0, 0, toRadians(90)))), "dst");
        Pose pose = new Pose(new Vector3D(10, 0, 0), NauticalAngles.IDENTITY);
        assertThat(Frames.transform(src, dst, pose), is(closeTo(
                new Pose(new Vector3D(0, -10, 0), new NauticalAngles(0, 0, toRadians(-90))),
                POSITION_ERROR, ANGLE_ERROR)));
    }

    @Test
    public void testTransformFromRotatedFrame() {
        // The source faces the destination's right, so its front is on the destination's right.
        Frame dst = FramesFactory.getGCRF();
        Frame src = new Frame(dst, Frames.makeTransform(new Pose(
                Vector3D.ZERO, new NauticalAngles(0, 0, toRadians(90)))), "src");
        Pose pose = new Pose(new Vector3D(10, 0, 0), NauticalAngles.IDENTITY);
        assertThat(Frames.transform(src, dst, pose), is(closeTo(
                new Pose(new Vector3D(0, 10, 0), new NauticalAngles(0, 0, toRadians(90))),
                POSITION_ERROR, ANGLE_ERROR)));
    }
}
