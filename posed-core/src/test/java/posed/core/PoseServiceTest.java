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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hipparchus.util.FastMath.toRadians;
import static org.junit.Assert.assertThat;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.orekit.bodies.GeodeticPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.base.Optional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PosedCoreConfiguration.class)
public class PoseServiceTest {
    // Acceptable amount of error in geospatial calculations:
    private static final double ANGLE_ERROR = toRadians(0.0000001);
    private static final double POSITION_ERROR = 0.0000001;

    private static final GeodeticPoint NULL_POSITION = new GeodeticPoint(0, 0, 0);
    private static final GeodeticPoint TEST_POSITION = new GeodeticPoint(
            toRadians(37.233333), toRadians(-115.808333), 1360);

    @Autowired
    private PoseService poseService;

    @Before
    public void setUp() {
        poseService.createRoot("root");
        poseService.create("root", "front",
                new Pose(new Vector3D(1, 0, 0), NauticalAngles.IDENTITY));
        poseService.create("root", "right",
                new Pose(new Vector3D(0, 1, 0), NauticalAngles.IDENTITY));
        poseService.create("root", "below",
                new Pose(new Vector3D(0, 0, 1), NauticalAngles.IDENTITY));
    }

    private void assertFront(GeodeticPoint position) {
        Optional<GeodeticPose> geopose = poseService.convert("front", Pose.IDENTITY);
        assertThat(geopose.get().getPosition().getLatitude(),
                is(greaterThan(position.getLatitude())));
        assertThat(geopose.get().getPosition().getLongitude(),
                is(closeTo(position.getLongitude(), ANGLE_ERROR)));
        assertThat(geopose.get().getPosition().getAltitude(),
                is(closeTo(position.getAltitude(), POSITION_ERROR)));
    }

    private void assertRight(GeodeticPoint position) {
        Optional<GeodeticPose> geopose = poseService.convert("right", Pose.IDENTITY);
        assertThat(geopose.get().getPosition().getLatitude(),
                is(closeTo(position.getLatitude(), ANGLE_ERROR)));
        assertThat(geopose.get().getPosition().getLongitude(),
                is(greaterThan(position.getLongitude())));
        assertThat(geopose.get().getPosition().getAltitude(),
                is(closeTo(position.getAltitude(), POSITION_ERROR)));
    }

    private void assertBelow(GeodeticPoint position) {
        Optional<GeodeticPose> geopose = poseService.convert("below", Pose.IDENTITY);
        assertThat(geopose.get().getPosition().getLatitude(),
                is(closeTo(position.getLatitude(), ANGLE_ERROR)));
        assertThat(geopose.get().getPosition().getLongitude(),
                is(closeTo(position.getLongitude(), ANGLE_ERROR)));
        assertThat(geopose.get().getPosition().getAltitude(),
                is(lessThan(position.getAltitude())));
    }

    @Test
    public void testNullFront() {
        poseService.update("root", new GeodeticPose(NULL_POSITION, NauticalAngles.IDENTITY));
        assertFront(NULL_POSITION);
    }

    @Test
    public void testNullRight() {
        poseService.update("root", new GeodeticPose(NULL_POSITION, NauticalAngles.IDENTITY));
        assertRight(NULL_POSITION);
    }

    @Test
    public void testNullBelow() {
        poseService.update("root", new GeodeticPose(NULL_POSITION, NauticalAngles.IDENTITY));
        assertBelow(NULL_POSITION);
    }

    @Test
    public void testTPFront() {
        poseService.update("root", new GeodeticPose(TEST_POSITION, NauticalAngles.IDENTITY));
        assertFront(TEST_POSITION);
    }

    @Test
    public void testTPRight() {
        poseService.update("root", new GeodeticPose(TEST_POSITION, NauticalAngles.IDENTITY));
        assertRight(TEST_POSITION);
    }

    @Test
    public void testTPBelow() {
        poseService.update("root", new GeodeticPose(TEST_POSITION, NauticalAngles.IDENTITY));
        assertBelow(TEST_POSITION);
    }
}
