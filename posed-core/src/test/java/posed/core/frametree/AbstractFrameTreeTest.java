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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProviderUtils;
import org.orekit.time.AbsoluteDate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import posed.core.NauticalAngles;
import posed.core.Pose;

public abstract class AbstractFrameTreeTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    protected abstract FrameTree newFrameTree(Frame root);

    @Test
    public void testCreateRoot() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("test");
    }

    @Test
    public void testCreateRootTwice() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("test");
        tree.createRoot("test");
    }

    @Test
    public void testCreateWithPose() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
    }

    @Test
    public void testCreateWithTransform() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Transform.IDENTITY);
    }

    @Test
    public void testCreateWithTransformProvider() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", TransformProviderUtils.IDENTITY_PROVIDER);
    }

    @Test
    public void testCreateRootThatIsAChild() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        thrown.expect(IllegalArgumentException.class);
        tree.createRoot("B");
    }

    @Test
    public void testTraverse() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        List<String> traversal = Streams.stream(tree.traverse())
                .map(Frame::getName)
                .collect(Collectors.toList());
        assertThat(traversal, is(equalTo(ImmutableList.of("GCRF", "A", "B", "C"))));
    }

    @Test
    public void testTraverseNonexistant() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        assertThat(ImmutableList.copyOf(tree.traverse("D")).isEmpty(),
                is(equalTo(true)));
    }

    @Test
    public void testGet() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("test");
        assertThat(tree.get("test"), is(not(nullValue())));
    }

    @Test
    public void testRemove() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("test");
        tree.remove("test");
        assertThat(tree.get("test"), is(nullValue()));
    }

    @Test
    public void testRemoveNonexistant() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.remove("test");
        assertThat(tree.get("test"), is(nullValue()));
    }

    @Test
    public void testRemoveThatIsAParent() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        thrown.expect(IllegalArgumentException.class);
        tree.remove("A");
    }

    @Test
    public void testCreateUpdate() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("A", "B",
                new Pose(new Vector3D(1, 1, 1), NauticalAngles.IDENTITY));
        Vector3D translation = tree.get("B")
                .getTransformProvider()
                .getTransform(AbsoluteDate.PAST_INFINITY)
                .getTranslation();
        assertThat(translation, is(equalTo(new Vector3D(-1, -1, -1))));
    }

    @Test
    public void testCreateUpdateMakesNewChild() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        tree.create("B", "D", Pose.IDENTITY);
        Frame d = tree.get("D");
        tree.create("A", "B", new Pose(new Vector3D(1, 1, 1), NauticalAngles.IDENTITY));
        assertThat(tree.get("D"), is(not(sameInstance(d))));
    }

    @Test
    public void testCreateUpdateKeepsUnrelatedChild() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("A", "C", Pose.IDENTITY);
        Frame b = tree.get("B");
        tree.create("A", "C", new Pose(new Vector3D(1, 1, 1), NauticalAngles.IDENTITY));
        assertThat(tree.get("B"), is(sameInstance(b)));
    }

    @Test
    public void testCreateWithUnknownParent() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        thrown.expect(IllegalArgumentException.class);
        tree.create("test", "B", Pose.IDENTITY);
    }

    @Test
    public void testCreateWithDifferentParent() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        thrown.expect(IllegalArgumentException.class);
        tree.create("A", "C", Pose.IDENTITY);
    }

    @Test
    public void testFindRoot() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        tree.createRoot("D");
        tree.create("D", "E", Pose.IDENTITY);
        assertThat(tree.findRoot("B").getName(), is(equalTo("A")));
    }

    @Test
    public void testFindRootNonexistant() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        assertThat(tree.findRoot("D"), is(nullValue()));
    }

    @Test
    public void testSubgraph() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        tree.createRoot("D");
        tree.create("D", "E", Pose.IDENTITY);
        List<String> subgraph = Streams.stream(tree.subgraph("B"))
                .map(Frame::getName)
                .collect(Collectors.toList());
        assertThat(subgraph, is(equalTo(ImmutableList.of("A", "B", "C"))));
    }

    @Test
    public void testSubgraphNonexistant() {
        FrameTree tree = newFrameTree(FramesFactory.getGCRF());
        tree.createRoot("A");
        tree.create("A", "B", Pose.IDENTITY);
        tree.create("B", "C", Pose.IDENTITY);
        assertThat(ImmutableList.copyOf(tree.subgraph("D")).isEmpty(),
                is(equalTo(true)));
    }
}
