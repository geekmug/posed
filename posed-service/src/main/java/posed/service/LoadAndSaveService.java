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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.Pair;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import posed.core.PoseService;
import posed.core.UnknownTransformException;
import posed.core.UnknownTransformProvider;

/** Loads and (periodically) saves the state of the PoseService. */
@Service
public class LoadAndSaveService implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(LoadAndSaveService.class);

    private final PoseService poseService;
    private final Path filename;
    private final Path workFilename;
    private final boolean autosave;

    /**
     * Creates a LoadAndSaveService instance.
     * @param poseService pose service to load/save
     * @param filename filename for loading/saving
     * @param cron cron-like expression, extending the usual UN*X definition to
     *          include triggers on the second, minute, hour, day of month,
     *          month, and day of week.
     */
    @Autowired
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public LoadAndSaveService(final PoseService poseService,
            final @Value("${posed.save.filename}") String filename,
            final @Value("${posed.save.cron}") String cron) {
        this.poseService = checkNotNull(poseService);
        this.filename = Paths.get(filename);
        workFilename = Paths.get(filename + "~");
        autosave = CronExpression.isValidExpression(cron);
    }

    @Override
    public final void afterPropertiesSet() {
        if (autosave) {
            load();
        }
    }

    @Override
    public final void destroy() {
        if (autosave) {
            save();
        }
    }

    private Double loadDouble(Object value) {
        if (value == null) {
            return null;
        } else if (!(value instanceof Double)) {
            LOG.warn("unexpected value in save file while loading double: {}", value);
            return null;
        }
        return (Double) value;
    }

    private Vector3D loadPosition(Object value) {
        if (value == null) {
            return null;
        } else if (!(value instanceof Map)) {
            LOG.warn("unexpected value in save file while loading position: {}", value);
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) value;

        Double x = loadDouble(values.get("x"));
        Double y = loadDouble(values.get("y"));
        Double z = loadDouble(values.get("z"));

        if (x != null && y != null && z != null) {
            return new Vector3D(x, y, z);
        } else {
            LOG.warn("unexpected value in save file while loading position: {}", value);
            return null;
        }
    }

    private Rotation loadRotation(Object value) {
        if (value == null) {
            return null;
        } else if (!(value instanceof Map)) {
            LOG.warn("unexpected value in save file while loading rotation: {}", value);
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) value;

        Double w = loadDouble(values.get("w"));
        Double x = loadDouble(values.get("x"));
        Double y = loadDouble(values.get("y"));
        Double z = loadDouble(values.get("z"));

        if (x != null && y != null && z != null) {
            return new Rotation(w, x, y, z, true);
        } else {
            LOG.warn("unexpected value in save file while loading rotation: {}", value);
            return null;
        }
    }

    private Transform loadTrasnform(Object value) {
        if (value == null) {
            return null;
        } else if (!(value instanceof Map)) {
            LOG.warn("unexpected value in save file while loading transform: {}", value);
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) value;

        Vector3D position = loadPosition(values.get("position"));
        Rotation rotation = loadRotation(values.get("rotation"));

        if (position == null && rotation == null) {
            return null;
        }

        Transform xfrm = Transform.IDENTITY;
        if (position != null) {
            xfrm = new Transform(AbsoluteDate.PAST_INFINITY, position);
        }
        if (rotation != null) {
            xfrm = new Transform(AbsoluteDate.PAST_INFINITY, xfrm,
                    new Transform(AbsoluteDate.PAST_INFINITY, rotation));
        }

        return xfrm;
    }

    private void loadFrame(String parent, String name, Object value) {
        if (value == null) {
            return;
        } else if (!(value instanceof Map)) {
            LOG.warn("unexpected value in save file while loading frame: {}", value);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) value;

        Transform xfrm = loadTrasnform(values);
        if (xfrm == null) {
            poseService.create(parent, name, UnknownTransformProvider.INSTANCE);
        } else {
            poseService.create(parent, name, xfrm);
        }

        Object childrenObject = values.get("children");
        if (childrenObject == null) {
            return;
        } else if (!(childrenObject instanceof Map)) {
            LOG.warn("unexpected value in save file while loading frame children: {}", childrenObject);
            return;
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> children = (Map<String, Object>) childrenObject;
            for (Map.Entry<String, Object> child : children.entrySet()) {
                loadFrame(name, child.getKey(), child.getValue());
            }
        }
    }

    /** Loads frames from the saved file into the PoseService. */
    @SuppressWarnings("unchecked")
    public final void load() {
        LOG.info("Loading pose service state...");

        Map<String, Object> roots = null;

        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            roots = yaml.loadAs(reader, Map.class);
        } catch (IOException e) {
            LOG.warn("failed to load save file", e);
            return;
        }

        if (roots != null) {
            String bodyFrame = poseService.getReferenceEllipsoid().getBodyFrame().getName();
            for (Map.Entry<String, Object> root : roots.entrySet()) {
                poseService.createRoot(root.getKey());
                loadFrame(bodyFrame, root.getKey(), root.getValue());
            }
        }
    }

    /** Saves frames to the saved file from the PoseService. */
    @Scheduled(cron = "${posed.save.cron}")
    @SuppressWarnings("unchecked")
    public final void save() {
        LOG.info("Saving pose service state...");

        Iterator<Frame> traversal = poseService.traverse().iterator();
        Frame bodyFrame = traversal.next();

        Map<String, Object> roots = new LinkedHashMap<String, Object>();
        ArrayDeque<Pair<Frame, Map<String, Object>>> stack = new ArrayDeque<>();
        stack.push(Pair.create(bodyFrame, roots));
        while (traversal.hasNext()) {
            Frame frame = traversal.next();

            Pair<Frame, Map<String, Object>> parent = stack.peek();
            while (!parent.getKey().getName().equals(frame.getParent().getName())) {
                stack.pop();
                parent = stack.peek();
            }

            Map<String, Object> children = roots;
            if (parent.getKey() != bodyFrame) {
                children = (Map<String, Object>) parent.getValue()
                        .computeIfAbsent("children",
                                k -> new LinkedHashMap<String, Object>());
            }

            Map<String, Object> frameData = new LinkedHashMap<String, Object>();
            children.put(frame.getName(), frameData);

            try {
                Transform xfrm = parent.getKey().getTransformTo(
                        frame, AbsoluteDate.PAST_INFINITY);
                frameData.put("position", ImmutableMap.of(
                        "x", xfrm.getTranslation().getX(),
                        "y", xfrm.getTranslation().getY(),
                        "z", xfrm.getTranslation().getZ()));
                frameData.put("rotation", ImmutableMap.of(
                        "w", xfrm.getRotation().getQ0(),
                        "x", xfrm.getRotation().getQ1(),
                        "y", xfrm.getRotation().getQ2(),
                        "z", xfrm.getRotation().getQ3()));
            } catch (UnknownTransformException e) {
                // ignore
            }

            stack.push(Pair.create(frame, frameData));
        }

        DumperOptions options = new DumperOptions();
        options.setExplicitStart(true);
        options.setExplicitEnd(true);
        options.setSplitLines(false);
        Yaml yaml = new Yaml(options);

        // Write the data to a working file.
        try (Writer writer = Files.newBufferedWriter(workFilename, StandardCharsets.UTF_8)) {
            yaml.dump(roots, writer);
        } catch (IOException e) {
            LOG.warn("failed to write save file", e);
            return;
        }

        // Then atomically move the file in place to avoid garbled data.
        try {
            Files.move(workFilename, filename,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("failed to atomically move save file", e);
        }
    }
}
