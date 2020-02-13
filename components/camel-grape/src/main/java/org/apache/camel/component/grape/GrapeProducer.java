/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.grape;

import java.util.LinkedHashMap;
import java.util.List;

import groovy.grape.Grape;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

public class GrapeProducer extends DefaultProducer {

    public GrapeProducer(GrapeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        GrapeCommand command = exchange.getIn().getHeader(GrapeConstants.GRAPE_COMMAND, GrapeCommand.grab, GrapeCommand.class);
        switch (command) {
            case grab:
                ClassLoader classLoader = exchange.getContext().getApplicationContextClassLoader();
                String rawCoordinates = exchange.getIn().getBody(String.class);
                LinkedHashMap<String, Object> map = new LinkedHashMap<>(5);
                try {
                    MavenCoordinates coordinates = MavenCoordinates.parseMavenCoordinates(rawCoordinates);
                    map.put("classLoader", classLoader);
                    map.put("group", coordinates.getGroupId());
                    map.put("module", coordinates.getArtifactId());
                    map.put("version", coordinates.getVersion());
                    map.put("classifier", coordinates.getClassifier());
                    Grape.grab(map);
                    getEndpoint().getComponent().getPatchesRepository().install(rawCoordinates);
                } catch (IllegalArgumentException ex) {
                    MavenCoordinates coordinates = MavenCoordinates.parseMavenCoordinates(getEndpoint().getDefaultCoordinates());
                    map.put("classLoader", classLoader);
                    map.put("group", coordinates.getGroupId());
                    map.put("module", coordinates.getArtifactId());
                    map.put("version", coordinates.getVersion());
                    map.put("classifier", coordinates.getClassifier());
                    Grape.grab(map);
                    getEndpoint().getComponent().getPatchesRepository().install(getEndpoint().getDefaultCoordinates());
                }
                break;

            case listPatches:
                List<String> patches = getEndpoint().getComponent().getPatchesRepository().listPatches();
                exchange.getIn().setBody(patches);
                break;

            case clearPatches:
                getEndpoint().getComponent().getPatchesRepository().clear();
                break;

            default:
                break;
        }
    }

    @Override
    public GrapeEndpoint getEndpoint() {
        return (GrapeEndpoint)super.getEndpoint();
    }

}
