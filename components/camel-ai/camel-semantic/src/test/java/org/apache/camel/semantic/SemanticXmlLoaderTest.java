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
package org.apache.camel.semantic;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticXmlLoaderTest {
    @Test
    void loaderDiscoveryIsReusedAndRefreshedOnReload() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        SimpleRegistry registry = new SimpleRegistry() {
            @Override
            public <T> Set<T> findByType(Class<T> type) {
                if (type == RoutesBuilderLoader.class) {
                    lookups.incrementAndGet();
                }
                return super.findByType(type);
            }
        };
        try (var context = new DefaultCamelContext(registry)) {
            context.getRegistry().bind("fixture", new SemanticLanguageTest.CountingAdapter());
            context.start();
            SemanticXmlLoader loader = registry.lookupByNameAndType(SemanticXmlLoader.REGISTRY_KEY, SemanticXmlLoader.class);
            lookups.set(0);
            for (String extension : new String[] { "xml", "tickets.xml", "semantic.xml", "xml" }) {
                assertThat(loader.isSupportedExtension(extension)).isTrue();
            }
            assertThat(loader.isSupportedExtension("yaml")).isFalse();
            assertThat(lookups).hasValue(1);

            registry.bind("customXml", new XmlRoutesBuilderLoader());
            var plugins = context.getCamelContextExtension().getContextPlugin(ContextServiceLoaderPluginResolver.class);
            plugins.onReload();
            assertThat(loader.isSupportedExtension("xml")).isFalse();
            assertThat(loader.isSupportedExtension("xml")).isFalse();
            assertThat(lookups).hasValue(2);

            registry.unbind("customXml");
            plugins.onReload();
            assertThat(loader.isSupportedExtension("xml")).isTrue();
            assertThat(loader.isSupportedExtension("tickets.xml")).isTrue();
            assertThat(lookups).hasValue(3);
        }
    }

    @Test
    void startupRefreshesDiscoveryAfterApplicationRegistersLoader() throws Exception {
        try (var context = new DefaultCamelContext()) {
            SemanticXmlLoader loader = context.getRegistry()
                    .lookupByNameAndType(SemanticXmlLoader.REGISTRY_KEY, SemanticXmlLoader.class);
            assertThat(loader.isSupportedExtension("xml")).isTrue();
            XmlRoutesBuilderLoader custom = new XmlRoutesBuilderLoader();
            context.getRegistry().bind("customXml", custom);

            context.getRegistry().bind("fixture", new SemanticLanguageTest.CountingAdapter());
            context.start();

            assertThat(loader.isSupportedExtension("xml")).isFalse();
            assertThat(PluginHelper.getRoutesLoader(context).getRoutesLoader("xml")).isSameAs(custom);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void nestedRoutesDoNotHideFollowingDeclarations(boolean declarations) throws Exception {
        String routes = """
                <routes>
                  <route id="nested">
                    <from uri="direct:nested"/>
                    <choice>
                      <when><simple>${body} != null</simple>
                        <setBody><constant><![CDATA[<semantic/>]]></constant></setBody>
                      </when>
                    </choice>
                  </route>
                  <route id="other"><from uri="direct:other"/><log message="Hello"/></route>
                  %s
                </routes>
                """.formatted(declarations ? """
                <semantic>
                  <question name="urgent" type="boolean"><instructions>Urgent?</instructions></question>
                </semantic>
                """ : "");
        try (var context = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("nested.routes.xml", routes));
            context.getRegistry().bind("fixture", new SemanticLanguageTest.CountingAdapter());
            context.start();
            assertThat(context.getRouteDefinitions()).hasSize(2);
            if (declarations) {
                assertThat(SemanticQuestions.get(context).get("urgent").getInstructions()).isEqualTo("Urgent?");
            } else {
                assertThat(context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class)).isNull();
            }
            try (var template = context.createProducerTemplate()) {
                assertThat(template.requestBody("direct:nested", "hello", String.class)).isEqualTo("<semantic/>");
            }
        }
    }
}
