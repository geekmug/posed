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

import org.hipparchus.CalculusFieldElement;
import org.orekit.frames.FieldTransform;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;

/** A transform provider that throws OrkitExceptions when queried. */
public final class UnknownTransformProvider implements TransformProvider {
    private static final long serialVersionUID = 1L;

    /** Singleton instance of the unknown transform provider. */
    public static final UnknownTransformProvider INSTANCE = new UnknownTransformProvider();

    private UnknownTransformProvider() {}

    @Override
    public Transform getTransform(AbsoluteDate date) {
        throw UnknownTransformException.INSTANCE;
    }

    @Override
    public <T extends CalculusFieldElement<T>> FieldTransform<T> getTransform(
            FieldAbsoluteDate<T> date) {
        throw UnknownTransformException.INSTANCE;
    }
}
