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

import org.hipparchus.exception.DummyLocalizable;
import org.orekit.errors.OrekitException;

/** Exception thrown when Orekit needs a transform but none is available. */
public final class UnknownTransformException extends OrekitException {
    private static final long serialVersionUID = 1L;

    /** Singleton instance of the unknown transform exception. */
    public static final UnknownTransformException INSTANCE =
            new UnknownTransformException("Unknown transform");

    /** Creates a new instance of this exception. */
    private UnknownTransformException(final String message) {
        super(new DummyLocalizable(message));
    }
}
