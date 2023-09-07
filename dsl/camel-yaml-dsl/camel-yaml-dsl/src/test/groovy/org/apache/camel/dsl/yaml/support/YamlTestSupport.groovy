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
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersionDetector
import groovy.util.logging.Slf4j
import org.apache.camel.CamelContext
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.KameletRoutesBuilderLoader
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.model.RouteTemplateDefinition
import org.apache.camel.spi.HasCamelContext
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.apache.camel.support.ResourceHelper
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@Slf4j
class YamlTestSupport extends Specification implements HasCamelContext {
    static def MAPPER = new ObjectMapper(new YAMLFactory())
    static def SCHEMA_NODE = MAPPER.readTree(ResourceHelper.getResourceAsStream('/schema/camelYamlDsl.json'));
    static def FACTORY = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(SCHEMA_NODE));
    static def SCHEMA = FACTORY.getSchema(SCHEMA_NODE);

    @AutoCleanup
    def context = new DefaultCamelContext()

    def loadRoutes(Collection<Resource> resources, boolean validate = true) {
        if (validate) {
            for (def resource : resources) {
                def target = MAPPER.readTree(resource.inputStream)
                def report = SCHEMA.validate(target)
                if (!report.isEmpty()) {
                    throw new IllegalArgumentException("${report}")
                }
            }
        }

        PluginHelper.getRoutesLoader(context).loadRoutes(resources)
    }

    def addTemplate(String name, @DelegatesTo(RouteTemplateDefinition) Closure<?> closure) {
        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {
                closure.resolveStrategy = Closure.DELEGATE_FIRST
                closure.delegate = routeTemplate(name)
                closure.call()
            }
        });
    }

    def loadRoutes(Resource... resources) {
        loadRoutes(resources.toList())
    }

    def loadRoutes(String... resources) {
        loadRoutesExt("yaml", resources)
    }

    def loadRoutesExt(String ext, String... resources) {
        int index = 0

        loadRoutes(
                resources.collect {
                    it -> ResourceHelper.fromString("route-${index++}." + ext, it.stripIndent())
                }
        )
    }

    def loadRoutesNoValidate(String... resources) {
        int index = 0

        loadRoutes(
            resources.collect {
                it -> ResourceHelper.fromString("route-${index++}.yaml", it.stripIndent())
            }, false
        )
    }

    def loadKamelets(Resource... resources) {
        loadKamelets(resources.toList())
    }

    def loadKamelets(Collection<Resource> resources) {
        KameletRoutesBuilderLoader kl = new KameletRoutesBuilderLoader()
        kl.setCamelContext(context)
        kl.start()
        resources.forEach(r -> kl.loadRoutesBuilder(r))
    }

    def loadKamelets(String... resources) {
        int index = 0

        PluginHelper.getRoutesLoader(context).loadRoutes(
            resources.collect {
                it -> ResourceHelper.fromString("route-${index++}.kamelet.yaml", it.stripIndent())
            }
        )
    }

    def loadIntegrations(String... resources) {
        int index = 0

        PluginHelper.getRoutesLoader(context).loadRoutes(
            resources.collect {
                it -> ResourceHelper.fromString("integration-${index++}.yaml", it.stripIndent())
            }
        )
    }

    def loadBindings(String... resources) {
        int index = 0

        PluginHelper.getRoutesLoader(context).loadRoutes(
            resources.collect {
                it -> ResourceHelper.fromString("binding-${index++}.yaml", it.stripIndent())
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
        return new Resource() {

            @Override
            String getScheme() {
                return "mem"
            }

            @Override
            String getLocation() {
                return location.endsWith('.yaml') ? location : location + '.yaml'
            }

            @Override
            boolean exists() {
                return true
            }

            @Override
            InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(content.stripIndent().getBytes(StandardCharsets.UTF_8))
            }

            @Override
            String toString() {
                return location
            }
        }
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
