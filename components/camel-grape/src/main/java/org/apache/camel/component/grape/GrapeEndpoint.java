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
import groovy.lang.Closure;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

/**
 * The grape component allows you to fetch, load and manage additional jars when
 * CamelContext is running.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "grape", syntax = "grape:defaultCoordinates", title = "Grape", producerOnly = true, label = "management,deployment")
public class GrapeEndpoint extends DefaultEndpoint {

    @UriPath(description = "Maven coordinates to use as default to grab if the message body is empty.")
    @Metadata(required = true)
    private final String defaultCoordinates;

    public GrapeEndpoint(String endpointUri, String defaultCoordinates, GrapeComponent component) {
        super(endpointUri, component);
        this.defaultCoordinates = defaultCoordinates;
    }

    public static List<String> loadPatches(CamelContext camelContext) {
        final ClassLoader classLoader = camelContext.getApplicationContextClassLoader();
        PatchesRepository patchesRepository = camelContext.getComponent("grape", GrapeComponent.class).getPatchesRepository();
        return DefaultGroovyMethods.each(patchesRepository.listPatches(), new Closure<Object>(null, null) {
            public void doCall(String it) {
                MavenCoordinates coordinates = MavenCoordinates.parseMavenCoordinates(it);
                LinkedHashMap<String, Object> map = new LinkedHashMap<>(5);
                map.put("classLoader", classLoader);
                map.put("group", coordinates.getGroupId());
                map.put("module", coordinates.getArtifactId());
                map.put("version", coordinates.getVersion());
                map.put("classifier", coordinates.getClassifier());
                Grape.grab(map);
            }

            public void doCall() {
                doCall(null);
            }

        });
    }

    @Override
    public Producer createProducer() {
        return new GrapeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Grape component supports only the producer side of the route.");
    }

    public String getDefaultCoordinates() {
        return defaultCoordinates;
    }

    @Override
    public GrapeComponent getComponent() {
        return DefaultGroovyMethods.asType(super.getComponent(), GrapeComponent.class);
    }

}
