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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hipparchus.util.FastMath.toRadians;
import static org.junit.Assert.assertThat;
import static posed.core.PosedMatchers.closeTo;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.comparator.Comparators;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PosedCoreConfiguration.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class PoseServiceTest {
    // Acceptable amount of error in geospatial calculations:
    private static final double ANGLE_ERROR = toRadians(0.0000001);
    private static final double POSITION_ERROR = 0.0000001;

    private static final GeodeticPoint NULL_POSITION = new GeodeticPoint(0, 0, 0);
    private static final GeodeticPoint TEST_POSITION = new GeodeticPoint(
            toRadians(37.233333), toRadians(-115.808333), 1360);
    private static final GeodeticPose NULL_POSE = new GeodeticPose(
            NULL_POSITION, NauticalAngles.IDENTITY);
    private static final GeodeticPose TEST_POSE = new GeodeticPose(
            TEST_POSITION, NauticalAngles.IDENTITY);

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

    @Test
    public void testConvertStreamPose() {
        StepVerifier.withVirtualTime(
                () -> poseService.convertStream("front", Pose.IDENTITY))
        .expectSubscription()
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(false)));
        })
        .expectNoEvent(Duration.ofDays(1))
        .then(() -> poseService.update("root", new GeodeticPose(TEST_POSITION, NauticalAngles.IDENTITY)))
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(true)));
        })
        .expectNoEvent(Duration.ofDays(1))
        .then(() -> poseService.update("root", new GeodeticPose(TEST_POSITION, NauticalAngles.IDENTITY)))
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(true)));
        })
        .thenCancel()
        .verify();
    }

    @Test
    public void testConvertStreamGeopose() {
        StepVerifier.withVirtualTime(
                () -> poseService.convertStream("front", TEST_POSE))
        .expectSubscription()
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(false)));
        })
        .expectNoEvent(Duration.ofDays(1))
        .then(() -> {
            poseService.update("root", TEST_POSE);
        })
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(true)));
        })
        .expectNoEvent(Duration.ofDays(1))
        .then(() -> poseService.update("root", TEST_POSE))
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(true)));
        })
        .thenCancel()
        .verify();
    }

    @Test
    public void testConvertBadFramePose() {
        assertThat(poseService.convert("unknown", Pose.IDENTITY),
                is(equalTo(Optional.absent())));
    }

    @Test
    public void testConvertBadFrameGeopose() {
        assertThat(poseService.convert("unknown", TEST_POSE),
                is(equalTo(Optional.absent())));
    }

    @Test
    public void testGetBodyShape() {
        assertThat(poseService.getBodyShape(), is(not(nullValue())));
    }

    @Test
    public void testTraverse() {
        List<String> names = Streams.stream(poseService.traverse())
                .map(Frame::getName).collect(Collectors.toList());
        // The ordering of the first two are stable
        List<String> first = names.subList(0, 2);
        assertThat(first, is(equalTo(ImmutableList.of("GCRF", "root"))));
        // The ordering for the last three is unstable
        List<String> last = names.subList(2, names.size());
        last.sort(Comparators.comparable());
        assertThat(last, is(equalTo(
                ImmutableList.of("below", "front", "right"))));
    }

    @Test
    public void testTraverseName() {
        List<String> names = Streams.stream(poseService.traverse("root"))
                .map(Frame::getName).collect(Collectors.toList());
        // The ordering of the first one is stable
        List<String> first = names.subList(0, 1);
        assertThat(first, is(equalTo(ImmutableList.of("root"))));
        // The ordering for the last three is unstable
        List<String> last = names.subList(1, names.size());
        last.sort(Comparators.comparable());
        assertThat(last, is(equalTo(
                ImmutableList.of("below", "front", "right"))));
    }

    @Test
    public void testRemove() {
        poseService.remove("below");
        assertThat(ImmutableList.copyOf(poseService.traverse("below")).isEmpty(),
                is(equalTo(true)));
    }

    @Test
    public void testRemoveWithStream() {
        StepVerifier.withVirtualTime(
                () -> poseService.convertStream("below", TEST_POSE))
        .expectSubscription()
        .assertNext(maybePose -> {
            assertThat(maybePose.isPresent(), is(equalTo(false)));
        })
        .expectNoEvent(Duration.ofDays(1))
        .then(() -> {
            poseService.remove("below");
        })
        .expectComplete()
        .verify();
    }

    @Test
    public void testTransform() {
        assertThat(poseService.transform("front", "below", Pose.IDENTITY).get(),
                is(closeTo(new Pose(
                        new Vector3D(1, 0, -1), NauticalAngles.IDENTITY),
                        POSITION_ERROR, ANGLE_ERROR)));
    }

    @Test
    public void testTransformBadSrc() {
        assertThat(poseService.transform("unknown", "below", Pose.IDENTITY).isPresent(),
                is(equalTo(false)));
    }

    @Test
    public void testTransformBadDst() {
        assertThat(poseService.transform("below", "unknown", Pose.IDENTITY).isPresent(),
                is(equalTo(false)));
    }

    @Test
    public void testUpdateChildFront() {
        poseService.update("front", NULL_POSE);
        GeodeticPose pose = poseService.convert("root", Pose.IDENTITY).get();
        assertThat(pose.getPosition().getLatitude(), is(lessThan(0.0)));
        assertThat(pose.getPosition().getLongitude(), is(closeTo(0, ANGLE_ERROR)));
        assertThat(pose.getPosition().getAltitude(), is(greaterThan(0.0)));
    }

    @Test
    public void testUpdateChildRight() {
        poseService.update("right", NULL_POSE);
        GeodeticPose pose = poseService.convert("root", Pose.IDENTITY).get();
        assertThat(pose.getPosition().getLatitude(), is(closeTo(0, ANGLE_ERROR)));
        assertThat(pose.getPosition().getLongitude(), is(lessThan(0.0)));
        assertThat(pose.getPosition().getAltitude(), is(closeTo(0, POSITION_ERROR)));
    }

    @Test
    public void testUpdateChildBelow() {
        poseService.update("below", NULL_POSE);
        GeodeticPose pose = poseService.convert("root", Pose.IDENTITY).get();
        assertThat(pose.getPosition().getLatitude(), is(closeTo(0, ANGLE_ERROR)));
        assertThat(pose.getPosition().getLongitude(), is(closeTo(0, ANGLE_ERROR)));
        assertThat(pose.getPosition().getAltitude(), is(closeTo(1, POSITION_ERROR)));
    }

    private void assertUpdateChildRotated(NauticalAngles expected, NauticalAngles rotation) {
        poseService.create("root", "rotated",
                new Pose(new Vector3D(0, 0, 0), rotation));
        poseService.update("rotated", NULL_POSE);
        GeodeticPose pose = poseService.convert("root", Pose.IDENTITY).get();
        assertThat(pose, is(closeTo(poseService.getBodyShape(),
                new GeodeticPose(new GeodeticPoint(0, 0, 0), expected),
                POSITION_ERROR, ANGLE_ERROR)));
    }

    @Test
    public void testUpdateChildRotatedRoll() {
        assertUpdateChildRotated(
                new NauticalAngles(-toRadians(30), 0, 0),
                new NauticalAngles(toRadians(30), 0, 0));
    }

    @Test
    public void testUpdateChildRotatedPitch() {
        assertUpdateChildRotated(
                new NauticalAngles(0, -toRadians(30), 0),
                new NauticalAngles(0, toRadians(30), 0));
    }

    @Test
    public void testUpdateChildRotatedYaw() {
        assertUpdateChildRotated(
                new NauticalAngles(0, 0, -toRadians(30)),
                new NauticalAngles(0, 0, toRadians(30)));
    }

    @Test
    public void testUpdateChildRotated() {
        // In general, the root rotation should be the reverted rotation.
        for (int r = -31; r < 31; r += 3) {
            double roll = r / 10.0;
            for (int p = -155; p < 155; p += 30) {
                double pitch = p / 100.0;
                for (int y = -31; y < 31; y += 3) {
                    double yaw = y / 10.0;
                    NauticalAngles a = new NauticalAngles(roll, pitch, yaw);
                    assertUpdateChildRotated(
                            new NauticalAngles(a.toRotation().revert()),
                            a);
                }
            }
        }
    }
}
