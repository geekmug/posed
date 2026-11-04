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

package posed.web;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hipparchus.util.FastMath.toRadians;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.orekit.bodies.GeodeticPoint;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import posed.core.GeodeticPose;
import posed.core.NauticalAngles;
import posed.core.Pose;
import posed.core.PoseService;
import posed.core.PosedCoreConfiguration;

public class PosedControllerTest {
    private static final Pattern COORDINATES =
            Pattern.compile("<coordinates>(.*?)</coordinates>", Pattern.DOTALL);

    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void testKmlCoordinatesIgnoreDefaultLocale() {
        // This locale uses a comma as its decimal separator.
        Locale.setDefault(Locale.GERMANY);

        PosedCoreConfiguration configuration = new PosedCoreConfiguration();
        PoseService poseService = new PoseService(configuration.getReferenceEllipsoid());
        poseService.createRoot("parent");
        poseService.update("parent", new GeodeticPose(
                new GeodeticPoint(toRadians(1.5), toRadians(2.5), 100),
                NauticalAngles.IDENTITY));
        poseService.create("parent", "child",
                new Pose(new Vector3D(1, 0, 0), NauticalAngles.IDENTITY));

        PosedController controller = new PosedController(poseService, configuration.geoid());
        String kml = new String(
                controller.getPosedKml("localhost", 1.0, true).blockOptional().get(),
                StandardCharsets.UTF_8);

        Matcher matcher = COORDINATES.matcher(kml);
        assertThat(matcher.find(), is(equalTo(true)));
        List<String> tuples = Splitter.on(CharMatcher.whitespace())
                .omitEmptyStrings().splitToList(matcher.group(1));
        assertThat(tuples.size(), is(equalTo(2)));
        for (String tuple : tuples) {
            // Each tuple is exactly a longitude, a latitude, and an altitude.
            assertThat(tuple, Splitter.on(',').splitToList(tuple).size(), is(equalTo(3)));
        }
    }
}
