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

import static com.google.common.base.Preconditions.checkNotNull;

import org.orekit.bodies.GeodeticPoint;

import com.google.common.base.MoreObjects;

/** Represents a geodetic pose. */
public final class GeodeticPose {
    private final GeodeticPoint position;
    private final NauticalAngles orientation;

    /**
     * Creates a geodetic pose.
     * @param position geodetic position
     * @param orientation orientation with respect to the topocentric frame
     */
    public GeodeticPose(final GeodeticPoint position, final NauticalAngles orientation) {
        this.position = checkNotNull(position);
        this.orientation = checkNotNull(orientation);
    }

    /**
     * Gets the geodetic position.
     * @return geodetic position
     */
    public GeodeticPoint getPosition() {
        return position;
    }

    /**
     * Gets the orientation with respect to the topocentric frame.
     * @return orientation with respect to the topocentric frame
     */
    public NauticalAngles getOrientation() {
        return orientation;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper("")
                .addValue(position)
                .addValue(orientation)
                .toString();
    }
}
