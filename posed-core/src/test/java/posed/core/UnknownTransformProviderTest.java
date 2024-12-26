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

import static org.junit.Assert.assertThrows;

import org.hipparchus.util.Binary64Field;
import org.junit.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;

public class UnknownTransformProviderTest {
    @Test
    public void testGetTransform() {
        assertThrows(UnknownTransformException.class, () -> {
            UnknownTransformProvider.INSTANCE.getTransform(AbsoluteDate.PAST_INFINITY);
        });
    }

    @Test
    public void testGetTransformField() {
        assertThrows(UnknownTransformException.class, () -> {
            UnknownTransformProvider.INSTANCE.getTransform(
                    FieldAbsoluteDate.getPastInfinity(Binary64Field.getInstance()));
        });
    }
}
