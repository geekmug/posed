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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.models.earth.ReferenceEllipsoid;

public class PosedMatchers {
    private static class GeodeticPointMatcher extends TypeSafeMatcher<GeodeticPoint> {
        private final ReferenceEllipsoid referenceEllipsoid;
        private final GeodeticPoint expected;
        private final double eps;

        private GeodeticPointMatcher(
                final ReferenceEllipsoid referenceEllipsoid,
                final GeodeticPoint expected,
                final double eps) {
            this.referenceEllipsoid = referenceEllipsoid;
            this.expected = expected;
            this.eps = eps;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expected);
        }

        private double getError(GeodeticPoint item) {
            Vector3D expectedPosition = toEcef(expected);
            Vector3D itemPosition = toEcef(item);
            return Vector3D.distance(expectedPosition, itemPosition);
        }

        @Override
        protected void describeMismatchSafely(GeodeticPoint item,
                Description mismatchDescription) {
            mismatchDescription
                    .appendText("distance=")
                    .appendValue(getError(item))
                    .appendText(", was ")
                    .appendValue(item);
        }

        private Vector3D toEcef(GeodeticPoint position) {
            return referenceEllipsoid.transform(position);
        }

        @Override
        protected boolean matchesSafely(GeodeticPoint item) {
            return eps >= getError(item);
        }
    }

    public static Matcher<GeodeticPoint> equalTo(
            ReferenceEllipsoid referenceEllipsoid, GeodeticPoint expected,
            double error) {
        return new GeodeticPointMatcher(referenceEllipsoid, expected, error);
    }

    private static class NauticalAnglesMatcher extends TypeSafeMatcher<NauticalAngles> {
        private final NauticalAngles expected;
        private final double eps;

        private NauticalAnglesMatcher(final NauticalAngles expected,
                final double eps) {
            this.expected = expected;
            this.eps = eps;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expected);
        }

        private double getError(NauticalAngles item) {
            return Rotation.distance(expected.toRotation(), item.toRotation());
        }

        @Override
        protected void describeMismatchSafely(NauticalAngles item,
                Description mismatchDescription) {
            mismatchDescription
                    .appendText("distance=")
                    .appendValue(getError(item))
                    .appendText(", was ")
                    .appendValue(item);
        }

        @Override
        protected boolean matchesSafely(NauticalAngles item) {
            return eps >= getError(item);
        }
    }

    public static Matcher<NauticalAngles> equalTo(NauticalAngles expected,
            double error) {
        return new NauticalAnglesMatcher(expected, error);
    }

    private static class GeodeticPoseMatcher extends TypeSafeMatcher<GeodeticPose> {
        private final GeodeticPose expected;
        private final GeodeticPointMatcher positionMatcher;
        private final NauticalAnglesMatcher anglesMatcher;

        private GeodeticPoseMatcher(final ReferenceEllipsoid referenceEllipsoid,
                final GeodeticPose expected,
                final double positionEps,
                final double angleEps) {
            this.expected = expected;
            this.positionMatcher = new GeodeticPointMatcher(
                    referenceEllipsoid, expected.getPosition(), positionEps);
            this.anglesMatcher = new NauticalAnglesMatcher(
                    expected.getOrientation(), angleEps);
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expected);
        }

        @Override
        protected void describeMismatchSafely(GeodeticPose item,
                Description mismatchDescription) {
            mismatchDescription
                    .appendText("distance={")
                    .appendValue(positionMatcher.getError(item.getPosition()))
                    .appendText(", ")
                    .appendValue(anglesMatcher.getError(item.getOrientation()))
                    .appendText("}, was ")
                    .appendValue(new GeodeticPose(
                            item.getPosition(),
                            item.getOrientation()));
        }

        @Override
        protected boolean matchesSafely(GeodeticPose item) {
            return positionMatcher.matchesSafely(item.getPosition())
                    && anglesMatcher.matchesSafely(item.getOrientation());
        }
    }

    public static Matcher<GeodeticPose> equalTo(
            ReferenceEllipsoid referenceEllipsoid, GeodeticPose expected,
            double positionError, double orientationError) {
        return new GeodeticPoseMatcher(referenceEllipsoid, expected,
                positionError, orientationError);
    }

    private PosedMatchers() {}
}
