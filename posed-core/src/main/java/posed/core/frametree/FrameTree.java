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

package posed.core.frametree;

import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;

import posed.core.Pose;

/** A mutable tree of frames. */
public interface FrameTree {
    /**
     * Creates a new frame attached to the root frame.
     *
     * <p>This is a short-hand for calling {@code create} with the name of the
     * root frame as the parent and a {@code null} transform provider.
     *
     * @param name name of the new frame.
     */
    void createRoot(String name);

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
    void create(String parentName, String name, TransformProvider xfrm);

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
    void create(String parentName, String name, Transform xfrm);

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
    void create(String parentName, String name, Pose pose);

    /**
     * Removes a frame from the frame tree if present.
     *
     * <p>If the frame is currently a parent to other frames, then this is an
     * an illegal argument to this method and will return an exception.
     *
     * @param name name of the frame to remove
     * @throws IllegalArgumentException if the frame is a parent
     */
    void remove(String name);

    /**
     * Gets the frame with the given name.
     * @param name name of the frame to retrieve
     * @return a frame from the tree
     */
    Frame get(String name);

    /**
     * Gets a depth-first, pre-order traversal starting a given root.
     * @param root the root of the graph to traverse
     * @return an iterable of frames for a given root
     */
    Iterable<Frame> traverse(String root);

    /**
     * Gets a depth-first, pre-order traversal for the entire frame tree.
     * @return an iterable of frames for the entire frame tree
     */
    Iterable<Frame> traverse();

    /**
     * Gets a depth-first, pre-order traversal for a subgraph containing the
     * target.
     *
     * <p>The subgraph is defined as root with the first frame that is attached
     * to the root frame of the frame tree.
     *
     * @param target a frame in the frame tree
     * @return an iterable of frames for the subgraph
     */
    Iterable<Frame> subgraph(String target);
}
