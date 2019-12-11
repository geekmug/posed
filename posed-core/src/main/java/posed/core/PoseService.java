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

import org.orekit.errors.OrekitException;
import org.orekit.frames.FixedTransformProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.time.AbsoluteDate;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import posed.core.frametree.ChangeTrackingFrameTree;
import posed.core.frametree.ChangeTrackingFrameTree.Change;
import posed.core.frametree.CopyOnWriteFrameTree;
import posed.core.frametree.FrameTree;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Schedulers;

/** Service instance for managing poses. */
@Service
public class PoseService {
    /**
     * Merge data from {@link Publisher} sequences contained in an array / vararg
     * into an interleaved merged sequence. Unlike {@link Flux#concat(Publisher) concat},
     * sources are subscribed to eagerly.
     * <p>
     * <img class="marble" src="doc-files/marbles/mergeFixedSources.svg" alt="">
     * <p>
     * Note that merge is tailored to work with asynchronous sources or finite sources. When dealing with
     * an infinite source that doesn't already publish on a dedicated Scheduler, you must isolate that source
     * in its own Scheduler, as merge would otherwise attempt to drain it before subscribing to
     * another source.
     *
     * @param sources the array of {@link Publisher} sources to merge
     * @param <I> The source type of the data sequence
     *
     * @return a merged {@link Flux}
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    @VisibleForTesting
    static <I> Flux<I> mergeWithEarlyExit(Flux<? extends I>... sources) {
        return Flux.merge(Flux.just(sources).map(Flux::materialize))
        .handle((signal, sink) -> {
            if (signal.isOnComplete()) {
                sink.complete();
            } else if (signal.isOnError()) {
                sink.error(signal.getThrowable());
            } else {
                sink.next(signal.get());
            }
        });
    }

    private final ConcurrentHashMap<String, ReplayProcessor<Boolean>> updateProcessors =
            new ConcurrentHashMap<>();
    private final ReferenceEllipsoid referenceEllipsoid;
    private final Frame bodyFrame;
    private final ChangeTrackingFrameTree<CopyOnWriteFrameTree> tree;

    /**
     * Creates a pose service with a given geodetic body.
     * @param referenceEllipsoid reference ellipsoid
     */
    @Autowired
    public PoseService(final ReferenceEllipsoid referenceEllipsoid) {
        this.referenceEllipsoid = checkNotNull(referenceEllipsoid);
        bodyFrame = referenceEllipsoid.getBodyFrame();
        tree = new ChangeTrackingFrameTree<>(new CopyOnWriteFrameTree(bodyFrame));
        tree.getChangeStream().subscribe(this::handleChangeStream);
    }

    @SuppressFBWarnings("ITC_INHERITANCE_TYPE_CHECKING")
    private void handleChangeStream(Change change) {
        if (change instanceof ChangeTrackingFrameTree.Created) {
            Frame frame = ((ChangeTrackingFrameTree.Created) change).getFrame();
            ReplayProcessor<Boolean> processor = updateProcessors.get(frame.getName());
            if (processor != null) {
                processor.onNext(Boolean.TRUE);
            }
        } else {
            String name = ((ChangeTrackingFrameTree.Removed) change).getName();
            ReplayProcessor<Boolean> processor = updateProcessors.get(name);
            if (processor != null) {
                processor.onComplete();
            }
        }
    }

    /**
     * Gets the reference ellipsoid for this pose service.
     * @return reference ellipsoid
     */
    public final ReferenceEllipsoid getReferenceEllipsoid() {
        return referenceEllipsoid;
    }

    /**
     * Gets a stream of {@code Change}s for this {@code FrameTree}.
     *
     * <p>The produced stream is based on <em>potential</em> updates and may
     * produce updates that have no apparent change.
     *
     * @return a stream of {@code Change}s for this {@code FrameTree}
     */
    public final Flux<ChangeTrackingFrameTree.Change> getChangeStream() {
        return tree.getChangeStream();
    }

    /**
     * Gets a depth-first, pre-order traversal for a subgraph containing the
     * target.
     *
     * <p>The subgraph is defined as rooted with the first frame that is
     * attached to the root frame of the frame tree.
     *
     * @param name a frame in the frame tree
     * @return an iterable of frames for the subgraph
     */
    public final Iterable<Frame> subgraph(String name) {
        return tree.subgraph(name);
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
        tree.create(parentName, name, xfrm);
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
        FrameTree treeCopy = new CopyOnWriteFrameTree(tree.getDelegate());

        // Updates are always done to the frame that is attached to GCRF.
        Frame frame = treeCopy.get(name);
        if (frame.getParent() == bodyFrame) {
            create(bodyFrame.getName(), name,
                    GeodeticFrames.makeTransform(referenceEllipsoid, pose));
        } else {
            // The first frame in the subgraph iterator is always the root.
            Frame root = treeCopy.findRoot(frame.getName());

            /* Build a new transform from bodyFrame to root WITHOUT using the
             * existing provider from bodyFrame to root that will be updated. */
            Transform xfrm = new Transform(AbsoluteDate.PAST_INFINITY,
                    GeodeticFrames.makeTransform(referenceEllipsoid, pose),
                    frame.getTransformTo(root, AbsoluteDate.PAST_INFINITY));

            // Update the root frame with the derived transform.
            create(bodyFrame.getName(), root.getName(), xfrm);
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

        FrameTree treeCopy = new CopyOnWriteFrameTree(tree.getDelegate());

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
                frameName -> ReplayProcessor.cacheLast())
                .subscribeOn(Schedulers.boundedElastic())
                .hide();
    }

    /**
     * Gets a stream of the apparent pose in a destination frame for a source frame and pose.
     * @param src source frame
     * @param dst destination frame
     * @param pose pose in the source frame
     * @return a stream of apparent pose in the destination frame
     */
    public Flux<Optional<Pose>> transformStream(String src,
            String dst, Pose pose) {
        checkNotNull(src);
        checkNotNull(dst);
        checkNotNull(pose);

        // Return the current transform, then any updates.
        return Flux.just(transform(src, dst, pose)).concatWith(
                mergeWithEarlyExit(
                        getOrMakeUpdateProcessor(src),
                        getOrMakeUpdateProcessor(dst))
                .map(v -> transform(src, dst, pose)));
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
            return Optional.of(GeodeticFrames.convert(referenceEllipsoid, frame, pose));
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
        return Flux.just(convert(name, pose)).concatWith(
                getOrMakeUpdateProcessor(name).map(v -> {
                    return convert(name, pose);
                }));
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
            return Optional.of(GeodeticFrames.convert(referenceEllipsoid, frame, geopose));
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
        return Flux.just(convert(name, geopose)).concatWith(
                getOrMakeUpdateProcessor(name).map(v -> {
                    return convert(name, geopose);
                }));
    }
}
