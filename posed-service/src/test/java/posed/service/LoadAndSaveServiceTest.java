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

package posed.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.ReferenceEllipsoid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import posed.core.Pose;
import posed.core.PoseService;

public class LoadAndSaveServiceTest {
    private static final String CRON_DISABLED = "-";
    private static final String CRON_ENABLED = "0 * * * * *";
    private static final String ADDED_FRAME = "added";
    private static final String SAVE_FILE = "---\n"
            + "root:\n"
            + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"
            + "  rotation: {w: 1.0, x: 0.0, y: 0.0, z: 0.0}\n"
            + "  children:\n"
            + "    child:\n"
            + "      position: {x: 4.0, y: 5.0, z: 6.0}\n"
            + "...\n";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path filename;

    @Before
    public void setUp() throws IOException {
        filename = folder.getRoot().toPath().resolve("posed.yml");
    }

    private static PoseService newPoseService() {
        return new PoseService(ReferenceEllipsoid.getWgs84(FramesFactory.getGCRF()));
    }

    private LoadAndSaveService newService(PoseService poseService, String cron) {
        return new LoadAndSaveService(poseService, filename.toString(), cron);
    }

    private static List<String> frameNames(PoseService poseService) {
        return Streams.stream(poseService.traverse())
                .map(Frame::getName)
                .collect(Collectors.toList());
    }

    private void writeSaveFile(String contents) throws IOException {
        Files.write(filename, contents.getBytes(StandardCharsets.UTF_8));
    }

    private String readSaveFile() throws IOException {
        return new String(Files.readAllBytes(filename), StandardCharsets.UTF_8);
    }

    /**
     * Starts a service with the given save file, adds a frame, and shuts down.
     * @param contents contents of the save file
     * @return names of the frames that were loaded
     */
    private List<String> startAndStop(String contents) throws IOException {
        writeSaveFile(contents);
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_ENABLED);
        service.afterPropertiesSet();
        List<String> names = frameNames(poseService);
        poseService.createRoot(ADDED_FRAME);
        service.destroy();
        return names;
    }

    /** Asserts that the save file is unchanged and that no other file was made. */
    private void assertLeftAsIs(String contents) throws IOException {
        assertThat(readSaveFile(), is(equalTo(contents)));
        assertThat(folder.getRoot().list(), is(arrayContaining("posed.yml")));
    }

    private void assertSaved() throws IOException {
        assertThat(readSaveFile(), containsString(ADDED_FRAME));
        assertThat(folder.getRoot().list(), is(arrayContaining("posed.yml")));
    }

    @Test
    public void testLoadsWhenAutosaveIsDisabled() throws IOException {
        writeSaveFile(SAVE_FILE);
        PoseService poseService = newPoseService();
        newService(poseService, CRON_DISABLED).afterPropertiesSet();
        assertThat(frameNames(poseService),
                is(equalTo(ImmutableList.of("GCRF", "root", "child"))));
    }

    @Test
    public void testLoadsWhenAutosaveIsEnabled() throws IOException {
        writeSaveFile(SAVE_FILE);
        PoseService poseService = newPoseService();
        newService(poseService, CRON_ENABLED).afterPropertiesSet();
        assertThat(frameNames(poseService),
                is(equalTo(ImmutableList.of("GCRF", "root", "child"))));
    }

    @Test
    public void testMissingSaveFileIsNotAnError() {
        PoseService poseService = newPoseService();
        newService(poseService, CRON_DISABLED).afterPropertiesSet();
        assertThat(frameNames(poseService), is(equalTo(ImmutableList.of("GCRF"))));
    }

    @Test
    public void testDoesNotSaveWhenAutosaveIsDisabled() throws IOException {
        writeSaveFile(SAVE_FILE);
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_DISABLED);
        service.afterPropertiesSet();
        poseService.createRoot(ADDED_FRAME);
        service.destroy();
        assertLeftAsIs(SAVE_FILE);
    }

    @Test
    public void testSavesWhenAutosaveIsEnabled() {
        PoseService poseService = newPoseService();
        poseService.createRoot("root");
        newService(poseService, CRON_ENABLED).destroy();

        PoseService loaded = newPoseService();
        newService(loaded, CRON_DISABLED).afterPropertiesSet();
        assertThat(frameNames(loaded), is(equalTo(ImmutableList.of("GCRF", "root"))));
    }

    @Test
    public void testSavesAfterMissingSaveFile() throws IOException {
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_ENABLED);
        service.afterPropertiesSet();
        poseService.createRoot(ADDED_FRAME);
        service.destroy();
        assertSaved();
    }

    @Test
    public void testSavesAfterValidSaveFile() throws IOException {
        startAndStop(SAVE_FILE);
        assertSaved();
    }

    @Test
    public void testSavesAfterEmptySaveFile() throws IOException {
        assertThat(startAndStop(""), is(equalTo(ImmutableList.of("GCRF"))));
        assertSaved();
    }

    @Test
    public void testFailureOnEveryStartupLeavesFileAsIs() throws IOException {
        String contents = "root: [unterminated\n";
        for (int i = 0; i < 2; i++) {
            assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF"))));
            assertLeftAsIs(contents);
        }
    }

    @Test
    public void testDocumentThatIsNotAMapIsNotFatal() throws IOException {
        String contents = "- one\n- two\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF"))));
        assertLeftAsIs(contents);
    }

    @Test
    public void testRotationWithoutW() throws IOException {
        // A root that cannot be loaded remains as a root without a known transform.
        String contents = "---\n"
                + "bad:\n"
                + "  rotation: {x: 0.0, y: 0.0, z: 0.0}\n"
                + "  children:\n"
                + "    lost: {position: {x: 1.0, y: 1.0, z: 1.0}}\n"
                + "good:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n";
        assertThat(startAndStop(contents), containsInAnyOrder("GCRF", "bad", "good"));
        assertLeftAsIs(contents);
    }

    @Test
    public void testBadChildOnlyLosesItsOwnSubtree() throws IOException {
        String contents = "---\n"
                + "root:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"
                + "  children:\n"
                + "    bad:\n"
                + "      position: {x: oops, y: 2.0, z: 3.0}\n"
                + "      children:\n"
                + "        lost: {position: {x: 1.0, y: 1.0, z: 1.0}}\n"
                + "    good:\n"
                + "      position: {x: 1.0, y: 2.0, z: 3.0}\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "root", "good"))));
        assertLeftAsIs(contents);
    }

    private void assertChildIsSkipped(String child) throws IOException {
        String contents = "---\n"
                + "root:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"
                + "  children:\n"
                + "    child: " + child + "\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "root"))));
        assertLeftAsIs(contents);
    }

    @Test
    public void testZeroRotationIsLoadedAndSaved() throws IOException {
        String contents = "---\n"
                + "root:\n"
                + "  rotation: {w: 0.0, x: 0.0, y: 0.0, z: 0.0}\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "root"))));
        assertSaved();
        assertThat(readSaveFile(), not(containsString("NaN")));
    }

    @Test
    public void testNonFiniteValuesAreLoadedAndSaved() throws IOException {
        String contents = "---\n"
                + "root:\n"
                + "  position: {x: .nan, y: .inf, z: 0.0}\n"
                + "  rotation: {w: .nan, x: 0.0, y: 0.0, z: 0.0}\n"
                + "  children:\n"
                + "    child: {position: {x: 1.0, y: 2.0, z: 3.0}}\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "root", "child"))));
        assertSaved();

        // What was saved also loads without problems, so saving remains enabled.
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_ENABLED);
        service.afterPropertiesSet();
        poseService.createRoot("another");
        service.destroy();
        assertThat(readSaveFile(), containsString("another"));
    }

    @Test
    public void testFrameThatIsNotAMapIsSkipped() throws IOException {
        assertChildIsSkipped("oops");
    }

    @Test
    public void testPositionThatIsNotAMapIsSkipped() throws IOException {
        assertChildIsSkipped("{position: oops}");
    }

    @Test
    public void testChildrenThatAreNotAMapKeepTheFrame() throws IOException {
        String contents = "---\n"
                + "root:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"
                + "  children: oops\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "root"))));
        assertLeftAsIs(contents);
    }

    @Test
    public void testDuplicateFrameNameIsSkipped() throws IOException {
        String contents = "---\n"
                + "first:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"
                + "  children:\n"
                + "    child: {position: {x: 1.0, y: 2.0, z: 3.0}}\n"
                + "child:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n";
        assertThat(startAndStop(contents), is(equalTo(ImmutableList.of("GCRF", "first", "child"))));
        assertLeftAsIs(contents);
    }

    @Test
    public void testFrameNameThatIsNotAString() throws IOException {
        assertThat(startAndStop("---\n"
                + "1:\n"
                + "  position: {x: 1.0, y: 2.0, z: 3.0}\n"),
                is(equalTo(ImmutableList.of("GCRF", "1"))));
        assertSaved();
    }

    @Test
    public void testIntegersAreAccepted() throws IOException {
        writeSaveFile("---\n"
                + "root:\n"
                + "  position: {x: 1, y: 2, z: 3}\n"
                + "  rotation: {w: 1, x: 0, y: 0, z: 0}\n");
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_ENABLED);
        service.afterPropertiesSet();
        assertThat(poseService.convert("root", Pose.IDENTITY).isPresent(), is(equalTo(true)));
        poseService.createRoot(ADDED_FRAME);
        service.destroy();
        assertSaved();
    }
}
