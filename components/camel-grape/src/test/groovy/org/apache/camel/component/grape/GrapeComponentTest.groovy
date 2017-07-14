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

import org.apache.camel.ServiceStatus
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import static org.apache.camel.component.grape.GrapeComponent.grapeCamelContext
import static org.apache.camel.component.grape.GrapeEndpoint.loadPatches
import static org.apache.camel.ServiceStatus.Stopped

class GrapeComponentTest extends Assert {

    def pathesRepository = new FilePatchesRepository()

    def camelContext = new DefaultCamelContext()

    static boolean canTest() {
        // we cannot test on CI
        System.getenv("BUILD_ID") == null;
    }

    @Before
    void before() {
        if (canTest()) {
            grapeCamelContext(camelContext)
        }
    }

    @Test
    void shouldLoadStreamComponent() {
        if (!canTest()) {
            return;
        }

        pathesRepository.clear()
        camelContext.start()
        camelContext.createProducerTemplate().sendBody('grape:org.apache.camel/camel-stream/2.15.2', 'msg')
        camelContext.createProducerTemplate().sendBody('stream:out', 'msg')
    }

    @Test
    void shouldLoadStreamComponentViaBodyRequest() {
        if (!canTest()) {
            return;
        }

        pathesRepository.clear()
        camelContext.start()
        camelContext.createProducerTemplate().sendBody('grape:grape', 'org.apache.camel/camel-stream/2.15.2')
        camelContext.createProducerTemplate().sendBody('stream:out', 'msg')
    }

    @Test
    void shouldLoadBeanAtRuntime() {
        if (!canTest()) {
            return;
        }

        pathesRepository.clear()
        camelContext.start()
        camelContext.createProducerTemplate().sendBody('grape:grape', 'org.apache.camel/camel-stream/2.15.2')
        def status = camelContext.createProducerTemplate().requestBody('bean:org.apache.camel.component.stream.StreamComponent?method=getStatus', null, ServiceStatus.class)
        assertEquals(Stopped, status)
    }

    @Test
    void shouldLoadPatchesAtStartup() {
        if (!canTest()) {
            return;
        }

        // Given
        pathesRepository.clear()
        camelContext.start()
        camelContext.createProducerTemplate().sendBody('grape:grape', 'org.apache.camel/camel-stream/2.15.2')
        camelContext.stop()

        camelContext = new DefaultCamelContext()
        camelContext.setApplicationContextClassLoader(new GroovyClassLoader())
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            void configure() {
                loadPatches(camelContext)
            }
        })

        // When
        camelContext.start()
        def status = camelContext.createProducerTemplate().requestBody('bean:org.apache.camel.component.stream.StreamComponent?method=getStatus', null, ServiceStatus.class)

        // Then
        assertEquals(Stopped, status)
    }

    @Test
    void shouldListPatches() {
        if (!canTest()) {
            return;
        }

        pathesRepository.clear()
        camelContext.start()
        camelContext.createProducerTemplate().sendBody('grape:grape', 'org.apache.camel/camel-stream/2.15.2')
        def patches = pathesRepository.listPatches()
        assertEquals(['org.apache.camel/camel-stream/2.15.2'], patches)
    }

}
