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

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import com.google.common.base.MoreObjects;

/** Represents a pose in Euclidean space. */
public final class Pose {
    /** A pose at the origin with no roll, pitch, or yaw. */
    public static final Pose IDENTITY = new Pose(Vector3D.ZERO, new NauticalAngles(Rotation.IDENTITY));

    private final Vector3D position;
    private final NauticalAngles orientation;

    /**
     * Creates a pose at the given position and orientation.
     * @param position Cartesian coordinates of a point in Euclidean space
     * @param orientation orientation with respect to the Cartesian axes
     */
    public Pose(final Vector3D position, final NauticalAngles orientation) {
        this.position = checkNotNull(position);
        this.orientation = checkNotNull(orientation);
    }

    /**
     * Gets the Cartesian coordinates.
     * @return Cartesian coordinates
     */
    public Vector3D getPosition() {
        return position;
    }

    /**
     * Gets the orientation with respect to the Cartesian axes.
     * @return orientation
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
