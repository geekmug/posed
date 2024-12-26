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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hipparchus.util.FastMath.PI;
import static posed.core.PosedMatchers.closeTo;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.models.earth.Geoid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import posed.core.NauticalAngles;
import posed.core.PosedCoreConfiguration;
import posed.grpc.proto.GeodeticPositionRequest;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PosedCoreConfiguration.class)
public class PosedProtosTest {
    @Autowired
    private Geoid geoid;

    @Test
    public void testEncodeNauticalAnglesBoolean() {
        posed.grpc.proto.NauticalAngles encoded = PosedProtos.encode(
                new NauticalAngles(0, 0, -PI / 2), true);
        assertThat(encoded.getYaw(), is(closeTo(270, 0.1)));
    }

    @Test
    public void testAmslToHae() {
        /* According to EGM96, MSL at (0,0) is about 17m above the
         * ellipsoid, so this test checks that 0m AMSL about 17m HAE
         * to ensure that we didn't flip the sign on the offset. */
        assertThat(PosedProtos.decode(geoid,
                GeodeticPositionRequest.newBuilder()
                .setLatitude(0).setLongitude(0).setAmsl(0).build()).getAltitude(),
                is(closeTo(17.0, 1)));
    }

    @Test
    public void testHaeToAmsl() {
        /* According to EGM96, MSL at (0,0) is about 17m above the
         * ellipsoid, so this test checks that 0m AMSL about 17m HAE
         * to ensure that we didn't flip the sign on the offset. */
        assertThat(PosedProtos.encode(geoid, new GeodeticPoint(0, 0, 0)).getAmsl(),
                is(closeTo(-17.0, 1)));
    }

    @Test
    public void testDecodeSphericalForward() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(0)
                .setElevation(0)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(100, 0, 0), 1)));
    }

    @Test
    public void testDecodeSphericalLeft() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(-90)
                .setElevation(0)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(0, -100, 0), 1)));
    }

    @Test
    public void testDecodeSphericalRight() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(90)
                .setElevation(0)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(0, 100, 0), 1)));
    }

    @Test
    public void testDecodeSphericalUp() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(0)
                .setElevation(90)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(0, 0, -100), 1)));
    }

    @Test
    public void testDecodeSphericalDown() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(0)
                .setElevation(-90)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(0, 0, 100), 1)));
    }

    @Test
    public void testDecodeSpherical() {
        posed.grpc.proto.Spherical v = posed.grpc.proto.Spherical.newBuilder()
                .setAzimuth(45)
                .setElevation(45)
                .setRadius(100)
                .build();
        assertThat(PosedProtos.decode(v), is(closeTo(new Vector3D(50, 50, -70), 1)));
    }
}
