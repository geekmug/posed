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
(function () {
    /*
     Options parsed from query string:
       scale=double        Scale to apply to the frame balls.
       stats=true          Enable the FPS performance display.
       inspector=true      Enable the inspector widget.
       debug=true          Full WebGL error reporting at substantial performance cost.
       view=longitude,latitude,[height,heading,pitch,roll]
                           Automatically set a camera view. Values in degrees and meters.
                           [height,heading,pitch,roll] default is looking straight down, [300,0,-90,0]
       saveCamera=false    Don't automatically update the camera view in the URL when it changes.
     */
    var endUserOptions = Cesium.queryToObject(window.location.search.substring(1));

    Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI2YTI4Y2JjMi04MWUzLTQ2MDctOGJjYy1iOThiY2RlMDJjMWYiLCJpZCI6MTgwODMsInNjb3BlcyI6WyJhc3IiLCJnYyJdLCJpYXQiOjE1NzMyMjU2Njl9.GBSRA0I34ZJAXQKhUk-qSyTrICGpFrKB2KN7z9-t7UQ';

    var loadingIndicator = document.getElementById('loadingIndicator');
    var viewer;
    try {
        viewer = new Cesium.Viewer('cesiumContainer', {
            animation: false,
            baseLayerPicker: false,
            imageryProvider: new Cesium.GridImageryProvider(),
            maximumRenderTimeChange: Infinity,
            navigationHelpButton: false,
            requestRenderMode: true,
            scene3DOnly: true,
            shadows: false,
            terrainProvider: Cesium.EllipsoidTerrainProvider(),
            timeline: false,
        });
        viewer.scene.skyBox.destroy();
        viewer.scene.skyBox = null;

        // Try to get terrain data, then switch the globe to use it.
        var worldTerrain = Cesium.createWorldTerrain();
        worldTerrain.readyPromise.then(function() {
            viewer.terrainProvider = worldTerrain;
        });

        var imagery = viewer.scene.imageryLayers;
        imagery.addImageryProvider(new Cesium.SingleTileImageryProvider({
            url: '/files/world.topo.bathy.200408.3x5400x2700.jpg',
        }));
        imagery.addImageryProvider(Cesium.createWorldImagery({
            style: Cesium.IonWorldImageryStyle.AERIAL,
        }));
    } catch (exception) {
        loadingIndicator.style.display = 'none';
        var message = Cesium.formatError(exception);
        console.error(message);
        if (!document.querySelector('.cesium-widget-errorPanel')) {
            window.alert(message); //eslint-disable-line no-alert
        }
        return;
    }

    // Add a compass, navigator, and distance scale UI to the viewer.
    viewer.extend(Cesium.viewerCesiumNavigationMixin, {
        enableZoomControls: false,
    });

    if (endUserOptions.inspector) {
        viewer.extend(Cesium.viewerCesiumInspectorMixin);
    }

    var showLoadError = function(name, error) {
        var title = 'An error occurred while loading the file: ' + name;
        var message = 'An error occurred while loading the file, which may indicate that it is invalid.  A detailed error report is below:';
        viewer.cesiumWidget.showErrorPanel(title, message, error);
    };

    var scene = viewer.scene;
    // Disable the atmospheric haze effect when zoomed out.
    scene.globe.showGroundAtmosphere = false;
    // Remove the sun from the scene to illuminate the whole globe evenly.
    scene.sun.destroy();
    scene.sun = undefined;

    var context = scene.context;
    if (endUserOptions.debug) {
        context.validateShaderProgram = true;
        context.validateFramebuffer = true;
        context.logShaderCompilation = true;
        context.throwOnWebGLError = true;
    }

    /* The CzmlDataSource JSON process generators static values for all of the
     * Entity properties. In the case of a polyline, that means it is considered
     * static and is scheduled for updates in batches asynchronously. In
     * contrast, the model's are scheduled synchronously, so the update for the
     * two entities gets split in time and the drawing gets torn. However,
     * entities that use a CallbackProperty are always handled synchronously, so
     * we can trick the rendering engine by rewriting the positions property of
     * the polyline to return the same value via a CallbackProperty.
     *
     * https://groups.google.com/d/msg/cesium-dev/5N1P_qPmbPo/rbnHNKFoAQAJ
     */
    var originalUpdaters = Cesium.CzmlDataSource.updaters;
    Cesium.CzmlDataSource.updaters = Cesium.CzmlDataSource.updaters.map(function (updater) {
        if (updater.name != 'processPolyline') {
            return updater;
        } else {
            return function (entity, packet, entityCollection, sourceUri) {
                updater(entity, packet, entityCollection, sourceUri);
                var polyline = entity.polyline;
                if (typeof polyline !== 'undefined') {
                    var positions = entity.polyline.positions;
                    if (typeof positions !== 'undefined') {
                        entity.polyline.positions = new Cesium.CallbackProperty(function () {
                            return positions._value;
                        }, false);
                    }
                }
            }
        }
    });

    var czmlDataSource = new Cesium.CzmlDataSource();
    viewer.dataSources.add(czmlDataSource);

    function setupCzmlEventSource() {
        var scale = endUserOptions.scale;
        if (typeof scale === 'undefined') {
            scale = 1;
        }
        var czmlEventSource = new EventSource('/posed.czml?scale=' + scale);
        czmlEventSource.addEventListener('czml', function(e) {
            czmlDataSource.process(JSON.parse(e.data));
            scene.requestRender();
        });
        czmlEventSource.onopen = function() {
            czmlDataSource.entities.removeAll();
            scene.requestRender();
        };
        czmlEventSource.onerror = function(e) {
            // Close the error'd stream.
            czmlEventSource.close();

            // Clear out all of the stale entities.
            czmlDataSource.entities.removeAll();
            scene.requestRender();

            // Schedule a timer to reconnect.
            setTimeout(setupCzmlEventSource, 1000);
        };
    };
    setupCzmlEventSource();

    viewer.homeButton.viewModel.command.beforeExecute.addEventListener(function(commandInfo) {
        // Fly to the current entities in the CZML Data Source.
        viewer.flyTo(czmlDataSource);
        // Tell the home button not to do anything else.
        commandInfo.cancel = true;
    });

    if (endUserOptions.stats) {
        scene.debugShowFramesPerSecond = true;
    }

    var view = endUserOptions.view;
    if (typeof view !== 'undefined') {
        var splitQuery = view.split(/[ ,]+/);
        if (splitQuery.length > 1) {
            var longitude = !isNaN(+splitQuery[0]) ? +splitQuery[0] : 0.0;
            var latitude = !isNaN(+splitQuery[1]) ? +splitQuery[1] : 0.0;
            var height = ((splitQuery.length > 2) && (!isNaN(+splitQuery[2]))) ? +splitQuery[2] : 300.0;
            var heading = ((splitQuery.length > 3) && (!isNaN(+splitQuery[3]))) ? Cesium.Math.toRadians(+splitQuery[3]) : undefined;
            var pitch = ((splitQuery.length > 4) && (!isNaN(+splitQuery[4]))) ? Cesium.Math.toRadians(+splitQuery[4]) : undefined;
            var roll = ((splitQuery.length > 5) && (!isNaN(+splitQuery[5]))) ? Cesium.Math.toRadians(+splitQuery[5]) : undefined;

            viewer.camera.setView({
                destination: Cesium.Cartesian3.fromDegrees(longitude, latitude, height),
                orientation: {heading: heading, pitch: pitch, roll: roll},
            });
        }
    }

    var camera = viewer.camera;
    function saveCamera() {
        var position = camera.positionCartographic;
        var hpr = '';
        if (typeof camera.heading !== 'undefined') {
            hpr = ',' + Cesium.Math.toDegrees(camera.heading) + ',' + Cesium.Math.toDegrees(camera.pitch) + ',' + Cesium.Math.toDegrees(camera.roll);
        }
        endUserOptions.view = Cesium.Math.toDegrees(position.longitude) + ',' + Cesium.Math.toDegrees(position.latitude) + ',' + position.height + hpr;
        history.replaceState(undefined, '', '?' + Cesium.objectToQuery(endUserOptions));
    }

    var timeout;
    if (endUserOptions.saveCamera !== 'false') {
        camera.changed.addEventListener(function() {
            window.clearTimeout(timeout);
            timeout = window.setTimeout(saveCamera, 1000);
        });
    }

    loadingIndicator.style.display = 'none';
})();
