/**
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
package org.apache.camel.component.grape

import org.apache.camel.CamelContext
import org.apache.camel.Consumer
import org.apache.camel.Processor
import org.apache.camel.Producer
import org.apache.camel.impl.DefaultEndpoint
import org.apache.camel.spi.Metadata
import org.apache.camel.spi.UriEndpoint
import org.apache.camel.spi.UriPath

import static groovy.grape.Grape.grab
import static org.apache.camel.component.grape.MavenCoordinates.parseMavenCoordinates

/**
 * The grape component allows you to fetch, load and manage additional jars when CamelContext is running.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "grape", syntax = "grape:defaultCoordinates", title = "Grape", producerOnly = true, label = "management,deployment")
class GrapeEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private final String defaultCoordinates

    GrapeEndpoint(String endpointUri, String defaultCoordinates, GrapeComponent component) {
        super(endpointUri, component)
        this.defaultCoordinates = defaultCoordinates
    }

    static def loadPatches(CamelContext camelContext) {
        def classLoader = camelContext.applicationContextClassLoader
        def patchesRepository = camelContext.getComponent('grape', GrapeComponent.class).patchesRepository
        patchesRepository.listPatches().each {
            def coordinates = parseMavenCoordinates(it)
            grab(classLoader: classLoader,
                 group: coordinates.groupId, module: coordinates.artifactId, version: coordinates.version, classifier: coordinates.classifier)
        }
    }

    @Override
    Producer createProducer() {
        new GrapeProducer(this)
    }

    @Override
    Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException('Grape component supports only the producer side of the route.')
    }

    @Override
    boolean isSingleton() {
        true
    }

    String getDefaultCoordinates() {
        defaultCoordinates
    }

    @Override
    GrapeComponent getComponent() {
        super.getComponent() as GrapeComponent
    }

}