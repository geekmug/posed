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
import static org.hipparchus.util.FastMath.PI;
import static org.hipparchus.util.FastMath.toRadians;
import static org.junit.Assert.assertThat;
import static posed.core.PosedMatchers.closeTo;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.Pair;
import org.junit.Test;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.time.AbsoluteDate;

import com.google.common.collect.ImmutableList;

public class GeodeticFramesTest {
    // Acceptable amount of error in geospatial calculations:
    private static final double ANGLE_ERROR = toRadians(0.0000001);
    private static final double POSITION_ERROR = 0.0000001;

    private static final Frame GCRF =
            FramesFactory.getGCRF();
    private static final OneAxisEllipsoid EARTH =
            ReferenceEllipsoid.getWgs84(GCRF);
    private static final OneAxisEllipsoid SPHERE =
            new OneAxisEllipsoid(1000, 0, GCRF);
    private static final GeodeticPose NULL_POSE =
            new GeodeticPose(
                    new GeodeticPoint(0, 0, 0),
                    NauticalAngles.IDENTITY);

    @Test
    public void testMakeTransformEquator() {
        for (int i = 0; i < 360; i++) {
            Transform xfrm = GeodeticFrames.makeTransform(SPHERE,
                    new GeodeticPose(
                            new GeodeticPoint(0, toRadians(i), 0),
                            NauticalAngles.IDENTITY));
            assertThat(new NauticalAngles(xfrm.getAngular().getRotation()),
                    is(closeTo(new NauticalAngles(0, -PI / 2, toRadians(i)),
                            ANGLE_ERROR)));
        }
    }

    @Test
    public void testMakeTransformNull() {
        Transform xfrm = GeodeticFrames.makeTransform(SPHERE, NULL_POSE);
        assertThat(xfrm.getCartesian().getPosition(),
                is(closeTo(new Vector3D(-1000, 0, 0), POSITION_ERROR)));
        assertThat(new NauticalAngles(xfrm.getAngular().getRotation()),
                is(closeTo(new NauticalAngles(0, -PI / 2, 0), ANGLE_ERROR)));
    }


    @Test
    public void testConvertEcefOnEarth() {
        ImmutableList<Pair<GeodeticPoint, Vector3D>> points = ImmutableList.of(
                Pair.create(new GeodeticPoint(0, 0, 0), new Vector3D(6378137, 0, 0)),
                Pair.create(new GeodeticPoint(0, PI / 2, 0), new Vector3D(0, 6378137, 0)),
                Pair.create(new GeodeticPoint(PI / 2, 0, 0), new Vector3D(0, 0, 6356752)),
                Pair.create(
                        new GeodeticPoint(toRadians(37.233333), toRadians(-115.808333), 1360),
                        new Vector3D(-2214012, -4578204, 3838865)));
        for (Pair<GeodeticPoint, Vector3D> point : points) {
            Pose pose = GeodeticFrames.convert(EARTH, GCRF,
                    new GeodeticPose(point.getFirst(), NauticalAngles.IDENTITY));
            assertThat(pose.getPosition(), is(closeTo(point.getSecond(), 1)));
        }
    }

    @Test
    public void testConvertEcefOnSphere() {
        ImmutableList<Pair<GeodeticPoint, Vector3D>> points = ImmutableList.of(
                Pair.create(new GeodeticPoint(0, 0, 0), new Vector3D(1000, 0, 0)),
                Pair.create(new GeodeticPoint(0, PI / 2, 0), new Vector3D(0, 1000, 0)),
                Pair.create(new GeodeticPoint(PI / 2, 0, 0), new Vector3D(0, 0, 1000)));
        for (Pair<GeodeticPoint, Vector3D> point : points) {
            Pose pose = GeodeticFrames.convert(SPHERE, GCRF,
                    new GeodeticPose(point.getFirst(), NauticalAngles.IDENTITY));
            assertThat(pose.getPosition(), is(closeTo(point.getSecond(), 1)));
        }
    }

    @Test
    public void testConvertFramePointsOnSphere() {
        ImmutableList<Pair<GeodeticPoint, Vector3D>> points = ImmutableList.of(
                Pair.create(new GeodeticPoint(0, 0, 0), new Vector3D(1000, 0, 0)),
                Pair.create(new GeodeticPoint(0, PI / 2, 0), new Vector3D(0, 1000, 0)),
                Pair.create(new GeodeticPoint(PI / 2, 0, 0), new Vector3D(0, 0, 1000)));
        for (Pair<GeodeticPoint, Vector3D> point : points) {
            Frame frame = new Frame(GCRF,
                    new Transform(AbsoluteDate.PAST_INFINITY,
                            point.getSecond().negate()),
                    "");
            GeodeticPose pose = GeodeticFrames.convert(SPHERE, frame);
            assertThat(pose.getPosition(), is(closeTo(SPHERE, point.getFirst(), 1)));
        }
    }
}
