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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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

import posed.core.PoseService;

public class LoadAndSaveServiceTest {
    private static final String CRON_DISABLED = "-";
    private static final String CRON_ENABLED = "0 * * * * *";
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

    private void writeSaveFile() throws IOException {
        Files.write(filename, SAVE_FILE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testLoadsWhenAutosaveIsDisabled() throws IOException {
        writeSaveFile();
        PoseService poseService = newPoseService();
        newService(poseService, CRON_DISABLED).afterPropertiesSet();
        assertThat(frameNames(poseService),
                is(equalTo(ImmutableList.of("GCRF", "root", "child"))));
    }

    @Test
    public void testLoadsWhenAutosaveIsEnabled() throws IOException {
        writeSaveFile();
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
        writeSaveFile();
        PoseService poseService = newPoseService();
        LoadAndSaveService service = newService(poseService, CRON_DISABLED);
        service.afterPropertiesSet();
        poseService.createRoot("other");
        service.destroy();
        assertThat(new String(Files.readAllBytes(filename), StandardCharsets.UTF_8),
                is(equalTo(SAVE_FILE)));
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
}
