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

import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;

/** Helpers for working with {@link Frame}s. */
public final class Frames {
    /**
     * Creates a transform from a given pose.
     * @param pose pose of the child
     * @return a transform from the child to the parent
     */
    public static Transform makeTransform(Pose pose) {
        return new Transform(
                AbsoluteDate.PAST_INFINITY,
                new Transform(AbsoluteDate.PAST_INFINITY,
                        pose.getPosition().negate()),
                new Transform(AbsoluteDate.PAST_INFINITY,
                        pose.getOrientation().toTransformRotation()));
    }

    /**
     * Gets the apparent pose in a destination frame for a source frame and pose.
     * @param src source frame
     * @param dst destination frame
     * @param pose pose in the source frame
     * @return pose in the destination frame
     */
    public static Pose transform(Frame src, Frame dst, Pose pose) {
        checkNotNull(src);
        checkNotNull(dst);
        checkNotNull(pose);

        // Create a Frame based on the pose, so we can get it's transform.
        Frame poseFrame = new Frame(src, makeTransform(pose), "");
        Transform xfrm = poseFrame.getTransformTo(dst, AbsoluteDate.PAST_INFINITY);
        return new Pose(xfrm.getTranslation(), new NauticalAngles(xfrm.getRotation()));
    }

    private Frames() {}
}
