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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.BodyShape;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FixedTransformProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;
import org.orekit.time.AbsoluteDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import posed.core.frametree.CopyOnWriteFrameTree;
import posed.core.frametree.FrameTree;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;

/** Service instance for managing poses. */
@Service
public class PoseService {
    private final ConcurrentHashMap<String, ReplayProcessor<Boolean>> updateProcessors =
            new ConcurrentHashMap<>();
    private final BodyShape bodyShape;
    private final Frame bodyFrame;
    private final CopyOnWriteFrameTree tree;

    /**
     * Creates a pose service with a given geodetic body.
     * @param bodyShape geodetic body
     */
    @Autowired
    public PoseService(final BodyShape bodyShape) {
        this.bodyShape = checkNotNull(bodyShape);
        bodyFrame = bodyShape.getBodyFrame();
        tree = new CopyOnWriteFrameTree(bodyFrame);
    }

    /**
     * Gets the geodetic body for this pose service.
     * @return geodetic body
     */
    public final BodyShape getBodyShape() {
        return bodyShape;
    }

    /**
     * Gets a depth-first, pre-order traversal starting a given root.
     * @param name the root of the graph to traverse
     * @return an iterable of frames for a given root
     */
    public final Iterable<Frame> traverse(String name) {
        return tree.traverse(name);
    }

    /**
     * Gets a depth-first, pre-order traversal for the entire frame tree.
     * @return an iterable of frames for the entire frame tree
     */
    public final Iterable<Frame> traverse() {
        return tree.traverse();
    }

    /**
     * Creates a new frame attached to the root frame.
     *
     * <p>This is a short-hand for calling {@code create} with the name of the
     * root frame as the parent and a {@code null} transform provider.
     *
     * @param name name of the new frame.
     */
    public final void createRoot(String name) {
        tree.createRoot(name);
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * transform provider as long as the parent is the same.
     *
     * <p>All transform providers should be immutable to maintain the coherency
     * of operations on this frame tree.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param xfrm transform provider describing the new frame
     */
    public final void create(String parentName, String name, TransformProvider xfrm) {
        Supplier<Iterable<Frame>> updated = tree.update(parentName, name, xfrm);
        if (updated != null) {
            updated.get().forEach(frame -> {
                // Notify this and all of the children about an update.
                ReplayProcessor<Boolean> processor = updateProcessors.get(frame.getName());
                if (processor != null) {
                    processor.onNext(Boolean.TRUE);
                }
            });
        }
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * transform as long as the parent is the same.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param xfrm transform describing the new frame
     */
    public final void create(String parentName, String name, Transform xfrm) {
        create(parentName, name, new FixedTransformProvider(xfrm));
    }

    /**
     * Creates (or updates) a frame attach to a given parent.
     *
     * <p>If this frame already exists, then it will be updated with the given
     * pose as long as the parent is the same.
     *
     * @param parentName name of the parent frame
     * @param name name of the new frame
     * @param pose pose describing the new frame
     */
    public final void create(String parentName, String name, Pose pose) {
        create(parentName, name, Frames.makeTransform(pose));
    }

    /**
     * Removes a frame from the frame tree if present.
     *
     * <p>If the frame is currently a parent to other frames, then this is an
     * an illegal argument to this method and will return an exception.
     *
     * @param name name of the frame to remove
     * @throws IllegalArgumentException if the frame is a parent
     */
    public final void remove(String name) {
        tree.remove(name);
        ReplayProcessor<Boolean> processor = updateProcessors.remove(name);
        if (processor != null) {
            processor.onComplete();
        }
    }

    /**
     * Updates the geodetic position of the given frame.
     *
     * <p>The actual update is performed on the transform between the geodetic
     * body to the first frame in the given frame's subgraph.
     *
     * @param name name of the frame to update
     * @param pose pose of the given frame
     */
    public final void update(String name, GeodeticPose pose) {
        FrameTree treeCopy = new CopyOnWriteFrameTree(tree);

        // Updates are always done to the frame that is attached to GCRF.
        Frame frame = treeCopy.get(name);
        if (frame.getParent() == bodyFrame) {
            create(bodyFrame.getName(), name,
                    GeodeticFrames.makeTransform(bodyShape, pose));
        } else {
            // The first frame in the subgraph iterator is always the root.
            Frame root = treeCopy.findRoot(frame.getName());

            // Get the topocentric rotation at this point.
            Rotation topoRot =
                    GeodeticFrames.getTopocentricRotation(pose.getPosition());

            // Get the ECEF position for this point.
            Vector3D ecefPosition = bodyShape.transform(pose.getPosition());

            // Rotate the ECEF position into the frame
            Vector3D framePosition = topoRot.applyInverseTo(ecefPosition);
            Rotation frameRotation = topoRot.applyInverseTo(pose.getOrientation().toRotation());

            // Get the transform between the root and frame to adjust.
            Transform xfrm = frame.getTransformTo(root, AbsoluteDate.PAST_INFINITY);

            // Apply the transform to get the pose in the root frame.
            Vector3D rootPosition = xfrm.getTranslation().add(framePosition);
            Rotation rootRotation = xfrm.getRotation().applyTo(frameRotation);

            // Rotate the position back into the ECEF frame
            Vector3D rootEcefPosition = topoRot.applyTo(rootPosition);
            Rotation rootEcefRotation = topoRot.applyTo(rootRotation);

            // Update the root frame with the adjusted transform.
            Transform rootXfrm = new Transform(AbsoluteDate.PAST_INFINITY,
                    new Transform(AbsoluteDate.PAST_INFINITY, rootEcefPosition.negate()),
                    new Transform(AbsoluteDate.PAST_INFINITY, rootEcefRotation.revert()));
            create(bodyFrame.getName(), root.getName(), rootXfrm);
        }
    }

    /**
     * Gets the apparent pose in a destination frame for a source frame and pose.
     * @param src source frame
     * @param dst destination frame
     * @param pose pose in the source frame
     * @return pose in the destination frame
     */
    public final Optional<Pose> transform(String src, String dst, Pose pose) {
        checkNotNull(src);
        checkNotNull(dst);
        checkNotNull(pose);

        FrameTree treeCopy = new CopyOnWriteFrameTree(tree);

        Frame srcFrame = treeCopy.get(src);
        if (srcFrame == null) {
            return Optional.absent();
        }
        Frame dstFrame = treeCopy.get(dst);
        if (dstFrame == null) {
            return Optional.absent();
        }

        return Optional.of(Frames.transform(srcFrame, dstFrame, pose));
    }

    private Flux<Boolean> getOrMakeUpdateProcessor(String name) {
        return updateProcessors.computeIfAbsent(name,
                frameName -> ReplayProcessor.cacheLast()).hide();
    }

    /**
     * Gets a geodetic pose for a pose in a given frame.
     * @param name frame in which the pose is expressed
     * @param pose pose in the given frame
     * @return a geodetic pose for the given pose in a frame
     */
    public final Optional<GeodeticPose> convert(String name, Pose pose) {
        checkNotNull(name);
        checkNotNull(pose);

        Frame frame = tree.get(name);
        if (frame == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(GeodeticFrames.convert(bodyShape, frame, pose));
        } catch (OrekitException e) {
            return Optional.absent();
        }
    }

    /**
     * Gets a stream of geodetic poses for a pose in a given frame.
     * @param name frame in which the pose is expressed
     * @param pose pose in the given frame
     * @return a stream of geodetic poses for the given pose in a frame
     */
    public final Flux<Optional<GeodeticPose>> convertStream(String name, Pose pose) {
        checkNotNull(name);
        checkNotNull(pose);

        // Return the current conversion, then any updates.
        return Flux.concat(ImmutableList.of(
                Flux.just(convert(name, pose)),
                getOrMakeUpdateProcessor(name).map(v -> {
                    return convert(name, pose);
                })));
    }

    /**
     * Gets a pose in a frame given a geodetic pose.
     * @param name frame in which to express the pose
     * @param geopose geodetic pose
     * @return a pose in a frame for the geodetic pose
     */
    public final Optional<Pose> convert(String name, GeodeticPose geopose) {
        checkNotNull(name);
        checkNotNull(geopose);

        Frame frame = tree.get(name);
        if (frame == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(GeodeticFrames.convert(bodyShape, frame, geopose));
        } catch (OrekitException e) {
            return Optional.absent();
        }
    }

    /**
     * Gets a stream of poses in a frame given a geodetic pose.
     * @param name frame in which to express the pose
     * @param geopose geodetic pose
     * @return a a stream of poses in a frame for the geodetic pose
     */
    public final Flux<Optional<Pose>> convertStream(String name, GeodeticPose geopose) {
        checkNotNull(name);
        checkNotNull(geopose);

        // Return the current conversion, then any updates.
        return Flux.concat(ImmutableList.of(
                Flux.just(convert(name, geopose)),
                getOrMakeUpdateProcessor(name).map(v -> {
                    return convert(name, geopose);
                })));
    }
}
