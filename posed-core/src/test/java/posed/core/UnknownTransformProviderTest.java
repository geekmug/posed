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

import org.hipparchus.util.Decimal64Field;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;

public class UnknownTransformProviderTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetTransform() {
        thrown.expect(UnknownTransformException.class);
        UnknownTransformProvider.INSTANCE.getTransform(AbsoluteDate.PAST_INFINITY);
    }

    @Test
    public void testGetTransformField() {
        thrown.expect(UnknownTransformException.class);
        UnknownTransformProvider.INSTANCE.getTransform(
                FieldAbsoluteDate.getPastInfinity(Decimal64Field.getInstance()));
    }
}
