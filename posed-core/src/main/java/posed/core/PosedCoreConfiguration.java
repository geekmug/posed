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

import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration needed for the posed.core package. */
@Configuration
@ComponentScan
public class PosedCoreConfiguration {
    /**
     * Gets the reference ellipsoid for the posed engine.
     * @return reference ellipsoid for the posed engine
     */
    @Bean
    public ReferenceEllipsoid getReferenceEllipsoid() {
        return ReferenceEllipsoid.getWgs84(FramesFactory.getGCRF());
    }
}
