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

import org.orekit.data.ClasspathCrawler;
import org.orekit.data.DataProvidersManager;
import org.orekit.forces.gravity.potential.EGMFormatReader;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.Geoid;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration needed for the posed.core package. */
@Configuration
@ComponentScan
public class PosedCoreConfiguration {
    private static final ReferenceEllipsoid WGS84 =
            ReferenceEllipsoid.getWgs84(FramesFactory.getGCRF());
    private static final Geoid GEOID;

    static {
        final int maxDegree = 360;
        final int maxOrder = 360;
        // Load the EGM96 coefficients from our class path
        DataProvidersManager.getInstance().addProvider(
                new ClasspathCrawler(
                        PosedCoreConfiguration.class.getPackage().getName()
                        .replace('.', '/') + "/egm96"));
        GravityFieldFactory.clearPotentialCoefficientsReaders();
        GravityFieldFactory.addPotentialCoefficientsReader(
                new EGMFormatReader(".*egm96", false));
        NormalizedSphericalHarmonicsProvider geopotential =
                GravityFieldFactory.getConstantNormalizedProvider(maxDegree, maxOrder);
        GEOID = new Geoid(geopotential, WGS84);
    }

    /**
     * Gets the reference ellipsoid for the posed engine.
     * @return reference ellipsoid for the posed engine
     */
    @Bean
    public ReferenceEllipsoid getReferenceEllipsoid() {
        return WGS84;
    }

    /**
     * Gets the geoid for the posed engine.
     * @return geoid for the posed engine
     */
    @Bean
    public Geoid geoid() {
        return GEOID;
    }
}
