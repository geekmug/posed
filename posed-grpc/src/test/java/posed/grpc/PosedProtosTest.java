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
import static org.hamcrest.Matchers.closeTo;
import static org.hipparchus.util.FastMath.PI;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import posed.core.NauticalAngles;
import posed.grpc.proto.Orientation;

public class PosedProtosTest {
    @Test
    public void testEncodeNauticalAnglesBoolean() {
        Orientation encoded = PosedProtos.encode(
                new NauticalAngles(0, 0, -PI / 2), true);
        assertThat(encoded.getYaw(), is(closeTo(270, 0.1)));
    }
}
