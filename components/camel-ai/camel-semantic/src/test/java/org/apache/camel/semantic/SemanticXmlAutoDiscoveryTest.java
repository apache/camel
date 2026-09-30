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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.main.Main;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.xml.io.XmlPullParserLocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticXmlAutoDiscoveryTest {
    private static final String LOCATED_ROUTES = """
            <?xml version="1.0"?>
            <routes xmlns="http://camel.apache.org/schema/xml-io">
              <semantic xmlns="http://camel.apache.org/schema/semantic">
                <question name="urgent" type="boolean">
                  <instructions>Urgent?</instructions>
                </question>
              </semantic>
              <route id="located">
                <from uri="direct:located"/>
                <filter>
                  <simple>${body} != null</simple>
                  <log message="Hello"/>
                </filter>
              </route>
            </routes>
            """;

    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({
            "false,routes.xml", "true,routes.xml", "false,routes.camel.xml", "true,routes.camel.xml",
            "false,my.tickets.xml", "true,my.tickets.xml", "false,my.tickets.semantic.xml", "true,my.tickets.semantic.xml" })
    void mainLoadsOrdinaryXmlWithoutLoaderRegistration(boolean standalone, String filename) throws Exception {
        String declarations = """
                <semantic xmlns="http://camel.apache.org/schema/semantic">
                  <question name="department" type="choice">
                    <instructions>Which department?</instructions>
                    <criterion key="billing" value="Invoices"/>
                  </question>
                </semantic>
                """;
        String route = """
                <route id="classify"><from uri="direct:tickets"/>
                  <setBody><language language="semantic">ref:department</language></setBody>
                </route>
                """;
        Path routes = directory.resolve(filename);
        String files;
        if (standalone) {
            Path questions = directory.resolve("my.questions.xml");
            Files.writeString(questions, declarations);
            Files.writeString(routes, "<routes>" + route + "</routes>");
            // The consumer is deliberately listed first.
            files = routes.toUri() + "," + questions.toUri();
        } else {
            Files.writeString(routes, "<routes>" + declarations + route + "</routes>");
            files = routes.toUri().toString();
        }
        Main main = new Main();
        main.bind("classifier", new SemanticLanguageTest.LabelAdapter());
        main.addProperty("camel.language.semantic.adapter", "classifier");
        main.configure().setRoutesIncludePattern(files);
        try {
            main.start();
            try (var template = main.getCamelContext().createProducerTemplate()) {
                assertThat(template.requestBody("direct:tickets", "invoice", String.class)).isEqualTo("billing");
            }
        } finally {
            main.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "tickets.xml", "tickets.semantic.xml", "my.tickets.xml", "my.tickets.semantic.xml" })
    void declarationsPreserveOriginalRouteSourceLocations(String filename) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.setSourceLocationEnabled(true);
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString(filename, LOCATED_ROUTES));
            var route = context.getRouteDefinitions().get(0);
            assertThat(route.getLocation()).isEqualTo(filename);
            assertThat(route.getLineNumber()).isEqualTo(8);
            assertThat(route.getInput().getLocation()).isEqualTo(filename);
            assertThat(route.getInput().getLineNumber()).isEqualTo(9);
            var filter = route.getOutputs().get(0);
            assertThat(filter.getLocation()).isEqualTo(filename);
            assertThat(filter.getLineNumber()).isEqualTo(10);
            assertThat(filter.getOutputs().get(0).getLocation()).isEqualTo(filename);
            assertThat(filter.getOutputs().get(0).getLineNumber()).isEqualTo(12);
        }
    }

    @Test
    void nestedSemanticElementIsRejectedWithOriginalSourceLocation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.setSourceLocationEnabled(true);
            String xml = LOCATED_ROUTES.replace("<log message=\"Hello\"/>", "<semantic/>");
            assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context)
                    .loadRoutes(ResourceHelper.fromString("invalid.tickets.xml", xml)))
                    .isInstanceOfSatisfying(XmlPullParserLocationException.class, error -> {
                        assertThat(error.getResource().getLocation()).isEqualTo("invalid.tickets.xml");
                        assertThat(error.getLineNumber()).isEqualTo(12);
                        assertThat(error).hasMessageContaining("invalid.tickets.xml, line 12")
                                .hasMessageContaining("<semantic/>");
                    });
            assertThat(SemanticQuestions.get(context).isEmpty()).isTrue();
        }
    }

    @Test
    void applicationLoaderForDottedExtensionTakesPrecedence() throws Exception {
        XmlRoutesBuilderLoader custom = new XmlRoutesBuilderLoader() {
            @Override
            public boolean isSupportedExtension(String extension) {
                return "tickets.xml".equals(extension);
            }
        };
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("ticketsLoader", custom);
            assertThat(PluginHelper.getRoutesLoader(context).getRoutesLoader("tickets.xml")).isSameAs(custom);
        }
    }

    @Test
    void ordinaryXmlRetainsBeansRouteConfigurationsAndDelegateLifecycle() throws Exception {
        AtomicReference<RoutesBuilderLoader> delegate = new AtomicReference<>();
        try (var context = new DefaultCamelContext()) {
            context.getCamelContextExtension().addContextPlugin(RoutesLoader.class, new DefaultRoutesLoader(context) {
                @Override
                public void initRoutesBuilderLoader(RoutesBuilderLoader loader) {
                    delegate.set(loader);
                }
            });
            context.start();
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("ordinary.xml", """
                    <camel xmlns="http://camel.apache.org/schema/xml-io">
                      <bean name="counter" type="java.util.concurrent.atomic.AtomicInteger"/>
                      <routeConfiguration id="config">
                        <onException><exception>java.lang.IllegalArgumentException</exception>
                          <handled><constant>true</constant></handled>
                          <setBody><constant>handled</constant></setBody>
                        </onException>
                      </routeConfiguration>
                      <route id="ordinary" routeConfigurationId="config">
                        <from uri="direct:ordinary"/>
                        <bean ref="counter" method="incrementAndGet"/>
                      </route>
                    </camel>
                    """));
            assertThat(context.getRouteConfigurationDefinitions()).hasSize(1);
            assertThat(context.getRegistry().lookupByName("counter")).isInstanceOf(AtomicInteger.class);
            assertThat(context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class)).isNull();
            try (var template = context.createProducerTemplate()) {
                assertThat(template.requestBody("direct:ordinary", "test", Integer.class)).isEqualTo(1);
            }
            assertThat(delegate.get()).isInstanceOf(XmlRoutesBuilderLoader.class);
            assertThat(((ServiceSupport) delegate.get()).isStarted()).isTrue();
        }
        assertThat(((ServiceSupport) delegate.get()).isStopped()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "routes-builder-loader-xml", "customXmlLoader" })
    void applicationRegisteredXmlLoaderTakesPrecedence(String name) throws Exception {
        XmlRoutesBuilderLoader custom = new XmlRoutesBuilderLoader();
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind(name, custom);
            context.build();
            assertThat(PluginHelper.getRoutesLoader(context).getRoutesLoader("xml")).isSameAs(custom);
            assertThat(context.getRegistry().findByType(SemanticXmlLoader.class))
                    .allMatch(loader -> !loader.isSupportedExtension("xml"));
        }
    }

    @Test
    void contextWithoutOptionalXmlSupportDoesNotInstallWrapper() throws Exception {
        try (var context = new DefaultCamelContext(false)) {
            context.setClassResolver(new DefaultClassResolver(context) {
                @Override
                public Class<?> resolveClass(String name) {
                    return "org.apache.camel.xml.in.ModelParser".equals(name) ? null : super.resolveClass(name);
                }
            });
            context.build();
            assertThat(context.getRegistry().lookupByName(SemanticXmlLoader.REGISTRY_KEY)).isNull();
            assertThat(context.resolveLanguage("semantic")).isNotNull();
        }
    }

    @Test
    void contextWithApplicationRegistryDiscoversXmlDeclarations() throws Exception {
        try (var context = new DefaultCamelContext(new SimpleRegistry())) {
            context.start();
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("questions.xml", """
                    <semantic><question name="urgent" type="boolean"><instructions>Urgent?</instructions></question></semantic>
                    """));
            assertThat(SemanticQuestions.get(context).get("urgent").getInstructions()).isEqualTo("Urgent?");
        }
    }

    @Test
    void contextRestartReinstallsAutomaticLoader() throws Exception {
        try (var context = new DefaultCamelContext()) {
            for (int i = 0; i < 2; i++) {
                context.start();
                PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("questions.xml",
                        """
                                <semantic><question name="urgent" type="boolean"><instructions>Urgent?</instructions></question></semantic>
                                """));
                assertThat(SemanticQuestions.get(context).get("urgent").getInstructions()).isEqualTo("Urgent?");
                context.stop();
                assertThat(context.getRegistry().lookupByName(SemanticXmlLoader.REGISTRY_KEY)).isNull();
            }
        }
    }
}
