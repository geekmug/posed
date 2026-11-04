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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.hipparchus.util.FastMath.sqrt;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
import org.yaml.snakeyaml.error.YAMLException;

import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import posed.core.PoseService;
import posed.core.UnknownTransformException;
import posed.core.UnknownTransformProvider;

/**
 * Loads and (periodically) saves the state of the PoseService.
 *
 * <p>The state is always loaded on startup if the file exists. The state is
 * only saved, periodically and on shutdown, if a valid cron expression is given
 * and the file did not have any problems when it was loaded.
 */
@Service
public class LoadAndSaveService implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(LoadAndSaveService.class);

    private final PoseService poseService;
    private final Path filename;
    private final Path workFilename;
    private final boolean autosave;
    private volatile boolean saveFileHasProblems;

    /**
     * Creates a LoadAndSaveService instance.
     * @param poseService pose service to load/save
     * @param filename filename for loading/saving
     * @param cron cron-like expression, extending the usual UN*X definition to
     *          include triggers on the second, minute, hour, day of month,
     *          month, and day of week. Saving is disabled if this is not a
     *          valid expression, such as "-".
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
        load();
    }

    @Override
    public final void destroy() {
        if (autosave) {
            save();
        }
    }

    private static double loadDouble(Map<?, ?> values, String key) {
        Object value = values.get(key);
        checkArgument(value instanceof Number, "expected %s to be a number: %s", key, value);
        return ((Number) value).doubleValue();
    }

    private static Vector3D loadPosition(Object value) {
        if (value == null) {
            return null;
        }
        checkArgument(value instanceof Map, "expected position to be a map: %s", value);
        Map<?, ?> values = (Map<?, ?>) value;

        return new Vector3D(
                loadDouble(values, "x"),
                loadDouble(values, "y"),
                loadDouble(values, "z"));
    }

    private static Rotation loadRotation(Object value) {
        if (value == null) {
            return null;
        }
        checkArgument(value instanceof Map, "expected rotation to be a map: %s", value);
        Map<?, ?> values = (Map<?, ?>) value;

        double w = loadDouble(values, "w");
        double x = loadDouble(values, "x");
        double y = loadDouble(values, "y");
        double z = loadDouble(values, "z");

        // A quaternion with a zero norm cannot be normalized.
        double squaredNorm = w * w + x * x + y * y + z * z;
        if (squaredNorm != 0) {
            double inv = 1 / sqrt(squaredNorm);
            w *= inv;
            x *= inv;
            y *= inv;
            z *= inv;
        }
        return new Rotation(w, x, y, z, false);
    }

    private static Transform loadTransform(Map<?, ?> values) {
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

    /**
     * Loads a frame and its children, skipping anything that cannot be loaded.
     * @param parent name of the parent frame
     * @param name name of the frame
     * @param value contents of the save file for the frame
     * @return whether everything was loaded
     */
    private boolean loadFrame(String parent, String name, Object value) {
        if (value == null) {
            return true;
        }

        Object children;
        try {
            checkArgument(value instanceof Map, "expected frame to be a map: %s", value);
            Map<?, ?> values = (Map<?, ?>) value;

            Transform xfrm = loadTransform(values);
            if (xfrm == null) {
                poseService.create(parent, name, UnknownTransformProvider.INSTANCE);
            } else {
                poseService.create(parent, name, xfrm);
            }
            children = values.get("children");
        } catch (RuntimeException e) {
            LOG.warn("skipping frame in save file and its children: {}", name, e);
            return false;
        }

        if (children == null) {
            return true;
        } else if (!(children instanceof Map)) {
            LOG.warn("skipping children of frame in save file: {}: {}", name, children);
            return false;
        }

        boolean complete = true;
        for (Map.Entry<?, ?> child : ((Map<?, ?>) children).entrySet()) {
            complete &= loadFrame(name, String.valueOf(child.getKey()), child.getValue());
        }
        return complete;
    }

    /**
     * Loads frames from the saved file into the PoseService.
     *
     * <p>Problems with the file do not prevent startup. Anything that cannot
     * be loaded is logged and skipped, and the file is left as it is by not
     * saving over it, because that would lose the parts that were skipped.
     */
    public final void load() {
        LOG.info("Loading pose service state...");

        saveFileHasProblems = false;

        Map<?, ?> roots;

        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            roots = yaml.loadAs(reader, Map.class);
        } catch (NoSuchFileException e) {
            LOG.info("no save file to load: {}", filename);
            return;
        } catch (IOException | YAMLException e) {
            LOG.warn("failed to load save file", e);
            saveFileHasProblems = true;
            return;
        }

        if (roots == null) {
            return;
        }

        boolean complete = true;
        String bodyFrame = poseService.getReferenceEllipsoid().getBodyFrame().getName();
        for (Map.Entry<?, ?> root : roots.entrySet()) {
            String name = String.valueOf(root.getKey());
            try {
                poseService.createRoot(name);
                complete &= loadFrame(bodyFrame, name, root.getValue());
            } catch (IllegalArgumentException e) {
                LOG.warn("skipping root frame in save file and its children: {}", name, e);
                complete = false;
            }
        }
        saveFileHasProblems = !complete;
    }

    /**
     * Saves frames to the saved file from the PoseService, unless the file had
     * problems when it was loaded.
     */
    @Scheduled(cron = "${posed.save.cron}")
    @SuppressWarnings("unchecked")
    public final void save() {
        if (saveFileHasProblems) {
            LOG.warn("not saving over a save file that had problems: {}", filename);
            return;
        }

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
