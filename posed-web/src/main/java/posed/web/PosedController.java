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

package posed.web;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hipparchus.util.FastMath.toDegrees;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.hipparchus.util.Pair;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import net.opengis.kml.v_2_2_0.AbstractFeatureType;
import net.opengis.kml.v_2_2_0.AltitudeModeEnumType;
import net.opengis.kml.v_2_2_0.DocumentType;
import net.opengis.kml.v_2_2_0.FolderType;
import net.opengis.kml.v_2_2_0.KmlType;
import net.opengis.kml.v_2_2_0.LineStringType;
import net.opengis.kml.v_2_2_0.LineStyleType;
import net.opengis.kml.v_2_2_0.LinkType;
import net.opengis.kml.v_2_2_0.LocationType;
import net.opengis.kml.v_2_2_0.ModelType;
import net.opengis.kml.v_2_2_0.ObjectFactory;
import net.opengis.kml.v_2_2_0.OrientationType;
import net.opengis.kml.v_2_2_0.PlacemarkType;
import net.opengis.kml.v_2_2_0.ScaleType;
import net.opengis.kml.v_2_2_0.StyleType;
import posed.core.GeodeticFrames;
import posed.core.GeodeticPose;
import posed.core.PoseService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** REST controller for managing poses. */
@RestController
public class PosedController {
    private static final String PREFIX_MAPPER = "com.sun.xml.bind.namespacePrefixMapper";
    private static final byte[] LINE_COLOR = new byte[] {(byte) 0xff, (byte) 0xff, 0x00, (byte) 0xff};
    private static final Double LINE_WIDTH = Double.valueOf(3);
    private static final ImmutableList<Pair<String, String>> STATIC_FILES = ImmutableList.of(
            Pair.create("files/ball.dae", "/static/files/ball.dae"));
    private static final String KMZ_MAIN_FILENAME = "main.kml";

    // Create a custom namespace prefix mapper to make the namespaces less ugly.
    private static final class KmlNamespacePrefixMapper extends NamespacePrefixMapper {
        private Map<String, String> namespaceMap = new HashMap<>();

        private KmlNamespacePrefixMapper() {
            namespaceMap.put("http://www.opengis.net/kml/2.2", "");
            namespaceMap.put("http://www.w3.org/2005/Atom", "atom");
            namespaceMap.put("urn:oasis:names:tc:ciq:xsdschema:xAL:2.0", "xal");
        }

        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion,
                boolean requirePrefix) {
            return namespaceMap.getOrDefault(namespaceUri, suggestion);
        }
    }

    private final ObjectFactory factory = new net.opengis.kml.v_2_2_0.ObjectFactory();
    private final PoseService poseService;
    private final ReferenceEllipsoid referenceEllipsoid;
    private final Frame bodyFrame;

    /**
     * Creates a REST controller service using a given pose service.
     * @param poseService pose service
     */
    @Autowired
    public PosedController(final PoseService poseService) {
        this.poseService = checkNotNull(poseService);
        referenceEllipsoid = poseService.getReferenceEllipsoid();
        bodyFrame = referenceEllipsoid.getBodyFrame();
    }

    private String toCoordinates(GeodeticPoint position) {
        return String.format("%f,%f,%f",
                toDegrees(position.getLongitude()),
                toDegrees(position.getLatitude()),
                position.getAltitude());
    }

    private List<JAXBElement<PlacemarkType>> createNode(String baseUrl, double scaleValue, Frame frame) {
        GeodeticPose pose;
        try {
            pose = GeodeticFrames.convert(referenceEllipsoid, frame);
        } catch (OrekitException e) {
            return Collections.emptyList();
        }

        LocationType location = factory.createLocationType();
        location.setLongitude(toDegrees(pose.getPosition().getLongitude()));
        location.setLatitude(toDegrees(pose.getPosition().getLatitude()));
        location.setAltitude(pose.getPosition().getAltitude());

        /* The good news is that KML viewers apply these rotations in the
         * traditional ZY'X'' order that we use for our rotations, but the bad
         * news is that they don't define the direction of rotation in a
         * consistent manner:
         *   - heading (Z) - positive is clockwise around Z-axis (below)
         *   - tilt (Y') - positive is anti-clockwise around Y'-axis (right)
         *   - roll (X'') - positive is anti-clockwise around X''-axis (front)
         */
        OrientationType orientation = factory.createOrientationType();
        orientation.setHeading(toDegrees(pose.getOrientation().getYaw()));
        orientation.setTilt(toDegrees(-pose.getOrientation().getPitch()));
        orientation.setRoll(toDegrees(-pose.getOrientation().getRoll()));

        ScaleType scale = factory.createScaleType();
        scale.setX(scaleValue);
        scale.setY(scaleValue);
        scale.setZ(scaleValue);

        LinkType link = factory.createLinkType();
        link.setHref(baseUrl + "files/ball.dae");

        ModelType model = factory.createModelType();
        model.setAltitudeModeGroup(factory.createAltitudeMode(AltitudeModeEnumType.ABSOLUTE));
        model.setLocation(location);
        model.setOrientation(orientation);
        model.setScale(scale);
        model.setLink(link);

        PlacemarkType placemark = factory.createPlacemarkType();
        placemark.setName(frame.getName());
        placemark.setAbstractGeometryGroup(factory.createModel(model));

        JAXBElement<PlacemarkType> originPlacemark = factory.createPlacemark(placemark);

        Frame parent = frame.getParent();
        GeodeticPose parentPose = null;
        if (parent != bodyFrame) {
            try {
                parentPose = GeodeticFrames.convert(referenceEllipsoid, parent);
            } catch (OrekitException e) {
                // ignore
            }
        }

        if (parentPose != null) {
            LineStringType lineString = factory.createLineStringType();
            lineString.setAltitudeModeGroup(factory.createAltitudeMode(AltitudeModeEnumType.ABSOLUTE));
            lineString.setCoordinates(ImmutableList.of(
                    toCoordinates(parentPose.getPosition()),
                    toCoordinates(pose.getPosition())));

            LineStyleType lineStyle = factory.createLineStyleType();
            lineStyle.setColor(LINE_COLOR);
            lineStyle.setWidth(LINE_WIDTH);

            StyleType style = factory.createStyleType();
            style.setLineStyle(lineStyle);

            PlacemarkType linePlacemark = factory.createPlacemarkType();
            linePlacemark.setName(parent.getName() + "->" + frame.getName());
            linePlacemark.setAbstractGeometryGroup(factory.createLineString(lineString));
            linePlacemark.setAbstractStyleSelectorGroup(ImmutableList.of(factory.createStyle(style)));

            return ImmutableList.of(factory.createPlacemark(linePlacemark), originPlacemark);
        } else {
            return ImmutableList.of(originPlacemark);
        }
    }

    private void writeKml(OutputStream out, String baseUrl, double scale, boolean flat) throws JAXBException {
        KmlType kml = factory.createKmlType();
        DocumentType document = factory.createDocumentType();
        kml.setAbstractFeatureGroup(factory.createDocument(document));

        if (flat) {
            for (Frame frame : poseService.traverse()) {
                if (frame == bodyFrame) {
                    continue;
                }

                document.getAbstractFeatureGroup()
                        .addAll(createNode(baseUrl, scale, frame));
            }
        } else {
            Deque<FolderType> folderStack = new ArrayDeque<>();
            for (Frame frame : poseService.traverse()) {
                if (frame == bodyFrame) {
                    continue;
                }

                while (folderStack.peek() != null
                        && !folderStack.peek().getName().equals(frame.getParent().getName())) {
                    folderStack.pop();
                }

                FolderType folder = factory.createFolderType();
                folder.setName(frame.getName());
                List<JAXBElement<? extends AbstractFeatureType>> featureGroup;
                if (folderStack.peek() != null) {
                    featureGroup = folderStack.peek().getAbstractFeatureGroup();
                } else {
                    featureGroup = document.getAbstractFeatureGroup();
                }
                featureGroup.add(factory.createFolder(folder));

                folderStack.push(folder);

                folder.getAbstractFeatureGroup().addAll(createNode(baseUrl, scale, frame));
            }
        }

        JAXBContext context = JAXBContext.newInstance(
                factory.getClass().getPackage().getName(),
                factory.getClass().getClassLoader());
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_ENCODING, Charsets.UTF_8.displayName());
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(PREFIX_MAPPER, new KmlNamespacePrefixMapper());
        marshaller.marshal(factory.createKml(kml), out);
    }

    /**
     * Gets a KML document for the current pose service.
     * @param host name and port used by the client to request the document
     * @param scale scale at which to render the pose balls
     * @param flat whether or not to generate folders in the document
     * @return a KML document
     */
    @GetMapping(path = "/posed.kml", produces = "application/vnd.google-earth.kml+xml")
    public final Mono<byte[]> getPosedKml(@RequestHeader(":authority") String host,
            @RequestParam(name = "scale", required = false, defaultValue = "1.0") double scale,
            @RequestParam(name = "flat", required = false, defaultValue = "true") boolean flat) {
        return Mono.<byte[]>create(sink -> {
            try {
                String baseUrl = String.format("http://%s/", host);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                writeKml(out, baseUrl, scale, flat);
                sink.success(out.toByteArray());
            } catch (Throwable t) {
                sink.error(t);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a KMZ document for the current pose service.
     * @param scale scale at which to render the pose balls
     * @param flat whether or not to generate folders in the document
     * @return a KML document
     */
    @GetMapping(path = "/posed.kmz", produces = "application/vnd.google-earth.kmz")
    public final Mono<byte[]> get(
            @RequestParam(name = "scale", required = false, defaultValue = "1.0") double scale,
            @RequestParam(name = "flat", required = false, defaultValue = "true") boolean flat) {
        return Mono.<byte[]>create(sink -> {
            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(stream);

                for (Pair<String, String> entry : STATIC_FILES) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    try (InputStream from = new ClassPathResource(entry.getValue(),
                            getClass().getClassLoader()).getInputStream()) {
                        ByteStreams.copy(from, zip);
                    }
                }

                zip.putNextEntry(new ZipEntry(KMZ_MAIN_FILENAME));
                writeKml(zip, "", scale, flat);

                zip.close();
                sink.success(stream.toByteArray());
            } catch (Throwable t) {
                sink.error(t);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
