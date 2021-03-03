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
package org.apache.camel.dsl.yaml.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchemaFactory
import groovy.util.logging.Slf4j
import org.apache.camel.CamelContext
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.spi.HasCamelContext
import org.apache.camel.spi.Resource
import org.apache.camel.support.ResourceHelper
import spock.lang.AutoCleanup
import spock.lang.Specification

@Slf4j
class YamlTestSupport extends Specification implements HasCamelContext {
    static def MAPPER = new ObjectMapper(new YAMLFactory())
    static def SCHEMA_RES = JsonLoader.fromResource('/camel-yaml-dsl.json')
    static def SCHEMA = JsonSchemaFactory.byDefault().getJsonSchema(SCHEMA_RES)

    @AutoCleanup
    def context = new DefaultCamelContext()

    def loadRoutes(Collection<Resource> resources) {
        for (def resource: resources) {
            def target = MAPPER.readTree(resource.inputStream)
            def report = SCHEMA.validate(target)

            if (!report.isSuccess()) {

                throw new IllegalArgumentException("${report}")
            }
        }

        context.routesLoader.loadRoutes(resources)
    }

    def loadRoutes(Resource... resources) {
        loadRoutes(resources.toList())
    }

    def loadRoutes(String... resources) {
        int index = 0

        loadRoutes(
            resources.collect {
                it -> ResourceHelper.fromString("route-${index++}.yaml", it.stripIndent())
            }
        )
    }

    def withMock(
            String uri,
            @DelegatesTo(MockEndpoint) Closure<?> closure) {

        def mock = context.getEndpoint(uri, MockEndpoint.class)

        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = mock
        closure.call()

        return mock
    }

    def withTemplate(
            @DelegatesTo(FluentProducerTemplate) Closure<?> closure) {

        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = context.createFluentProducerTemplate()
        closure.call()
    }

    static Resource asResource(String location, String content) {
        return ResourceHelper.fromString(
                location.endsWith('.yaml') ? location : location + '.yaml',
                content.stripIndent()
        )
    }


    // ***********************************
    //
    // Lifecycle
    //
    // ***********************************

    def setup() {
        context.disableJMX()
        context.setStreamCaching(true)

        doSetup()
    }

    def cleanup() {
        doCleanup()
    }

    def doSetup() {
    }

    def doCleanup() {
    }

    // ***********************************
    //
    // HasCamelContext
    //
    // ***********************************

    @Override
    CamelContext getCamelContext() {
        return null
    }
}
