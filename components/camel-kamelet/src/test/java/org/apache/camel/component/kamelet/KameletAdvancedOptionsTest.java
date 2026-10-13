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
package org.apache.camel.component.kamelet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KameletAdvancedOptionsTest extends CamelTestSupport {

    @Test
    void advancedSourceOptionsAreApplied() {
        RouteDefinition route = getRoute("sourceTrigger");
        assertNotNull(route, "Expected the source caller route to be created");

        String inputUri = findExpandedInputUri("direct:source?");
        assertTrue(inputUri.contains("block=true"),
                () -> "Expected block=true on the expanded source endpoint, but was: " + inputUri);
    }

    @Test
    void advancedSourceOptionsOverrideExistingUriParameters() {
        RouteDefinition route = getRoute("sourceOverrideTrigger");
        assertNotNull(route, "Expected the source override caller route to be created");

        String inputUri = findExpandedInputUri("direct:source-with-options?");
        assertTrue(inputUri.contains("block=true"),
                () -> "Expected block=true to override block=false: " + inputUri);
        assertFalse(inputUri.contains("block=false"),
                () -> "The old block=false value must not remain: " + inputUri);
    }

    @Test
    void advancedSourceOptionsPreservePropertyPlaceholders() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("source-with-placeholder")
                            .from("direct:source?block=false&timeout={{timeout}}")
                            .to("kamelet:sink");

                    from("direct:trigger")
                            .to("kamelet:source-with-placeholder/test?source.block=true");
                }
            });

            Properties properties = new Properties();
            properties.put("timeout", "1000");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = testContext.getRouteDefinitions().stream()
                    .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                    .filter(route -> route.getInput() != null)
                    .filter(route -> route.getInput().getEndpointUri() != null
                            && route.getInput().getEndpointUri().startsWith("direct:source?"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expanded source route not found"));

            String uri = expandedRoute.getInput().getEndpointUri();
            assertTrue(uri.contains("block=true"),
                    () -> "Expected advanced option, but was: " + uri);
            assertTrue(uri.contains("timeout={{timeout}}"),
                    () -> "Expected the property placeholder to remain intact: " + uri);
        }
    }

    @Test
    void advancedSourceOptionsPreserveRawExpressions() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("source-with-raw")
                            .from("direct:source?timeout=RAW({{secret}})")
                            .to("kamelet:sink");

                    from("direct:trigger")
                            .to("kamelet:source-with-raw/test?source.block=true");
                }
            });

            Properties properties = new Properties();
            properties.put("secret", "1000");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = testContext.getRouteDefinitions().stream()
                    .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                    .filter(route -> route.getInput() != null)
                    .filter(route -> route.getInput().getEndpointUri() != null
                            && route.getInput().getEndpointUri().startsWith("direct:source?"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expanded source route not found"));

            String uri = expandedRoute.getInput().getEndpointUri();
            assertTrue(uri.contains("block=true"),
                    () -> "Expected advanced option, but was: " + uri);
            assertTrue(uri.contains("timeout=RAW({{secret}})"),
                    () -> "Expected the RAW expression to remain intact: " + uri);
        }
    }

    @Test
    void advancedSinkOptionsPreservePropertyPlaceholders() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-with-placeholder")
                            .from("kamelet:source")
                            .to("mock:{{topic}}?retain-first=0");

                    from("direct:trigger")
                            .kamelet("sink-with-placeholder?sink.retain-first=1");
                }
            });

            Properties properties = new Properties();
            properties.put("topic", "placeholder-sink");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = testContext.getRouteDefinitions().stream()
                    .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                    .filter(route -> findToDefinition(route, "mock:{{topic}}") != null)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expanded sink route not found"));

            ToDefinition sink = findToDefinition(expandedRoute, "mock:{{topic}}");
            assertNotNull(sink, "Expected the placeholder sink endpoint");

            String uri = sink.getEndpointUri();
            assertTrue(uri.contains("{{topic}}"),
                    () -> "Expected the property placeholder to remain intact: " + uri);
            assertTrue(uri.contains("retain-first=1"),
                    () -> "Expected the advanced option on the sink endpoint: " + uri);
            assertFalse(uri.contains("%7B%7Btopic%7D%7D"),
                    () -> "The placeholder must not be URL-encoded: " + uri);
        }
    }

    @Test
    void advancedSinkOptionsPreserveRawExpressions() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-with-raw")
                            .from("kamelet:source")
                            .to("mock:{{bucket}}?secretKey=RAW({{secret}})");

                    from("direct:trigger")
                            .kamelet("sink-with-raw?sink.retain-first=1");
                }
            });

            Properties properties = new Properties();
            properties.put("bucket", "placeholder-bucket");
            properties.put("secret", "secret-value");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = testContext.getRouteDefinitions().stream()
                    .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                    .filter(route -> route.getOutputs() != null)
                    .filter(route -> route.getOutputs().stream()
                            .anyMatch(output -> output instanceof ToDefinition
                                    && ((ToDefinition) output).getEndpointUri() != null
                                    && ((ToDefinition) output).getEndpointUri().startsWith("mock:{{bucket}}")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expanded RAW sink route not found"));

            ToDefinition sink = findToDefinition(expandedRoute, "mock:{{bucket}}");
            assertNotNull(sink, "Expected the RAW sink endpoint");

            String uri = sink.getEndpointUri();
            assertTrue(uri.contains("{{bucket}}"),
                    () -> "Expected the bucket placeholder to remain intact: " + uri);
            assertTrue(uri.contains("secretKey=RAW({{secret}})"),
                    () -> "Expected the RAW expression to remain intact: " + uri);
            assertTrue(uri.contains("retain-first=1"),
                    () -> "Expected the advanced option on the sink endpoint: " + uri);
            assertFalse(uri.contains("%7B%7B"),
                    () -> "Property placeholders must not be URL-encoded: " + uri);
        }
    }

    @Test
    void advancedSinkOptionsAreAppliedAndMessageIsDelivered() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:advanced-sink-result");
        sink.expectedBodiesReceived("hello");

        template.sendBody("direct:sink-trigger", "hello");

        sink.assertIsSatisfied();
        assertEquals(1, sink.getRetainFirst(),
                "Expected retain-first=1 on the configured sink endpoint");
        assertEquals(5, sink.getRetainLast(),
                "Expected retain-last=5 to be preserved on the configured sink endpoint");
    }

    @Test
    void noAdvancedOptionsRetainsExistingBehavior() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:basic-sink-result");
        sink.expectedBodiesReceived("hello");

        template.sendBody("direct:basic-trigger", "hello");

        sink.assertIsSatisfied();
        assertEquals(-1, sink.getRetainFirst(),
                "The basic sink should retain the default retainFirst value");
        assertEquals(-1, sink.getRetainLast(),
                "The basic sink should retain the default retainLast value");
    }

    @Test
    void sourceKameletRejectsSinkOptions() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("source-with-invalid-options")
                            .from("direct:source")
                            .to("kamelet:sink");

                    from("direct:trigger")
                            .to("kamelet:source-with-invalid-options/test?sink.block=true");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for sink.* on a source Kamelet, but got: "
                          + exception);
        }
    }

    @Test
    void sinkKameletRejectsSourceOptions() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-with-invalid-options")
                            .from("kamelet:source")
                            .to("mock:result");

                    from("direct:trigger")
                            .kamelet("sink-with-invalid-options?source.block=true");
                }
            });

            Exception ex = assertThrows(Exception.class, testContext::start);
            assertTrue(hasIllegalArgumentException(ex),
                    "Expected IllegalArgumentException for source.* on a sink Kamelet");
        }
    }

    @Test
    void advancedSinkOptionsRejectMultipleTopLevelStaticDestinations() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("mock-multi-sink")
                            .from("kamelet:source")
                            .to("mock:first-advanced-sink?retain-first=0")
                            .to("mock:second-advanced-sink?retain-first=0");

                    from("direct:multi-sink-trigger")
                            .kamelet("mock-multi-sink?sink.retain-first=1");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for multiple destinations, but got: " + exception);
            assertTrue(exceptionChainContains(exception, "multiple top-level static to() destinations"),
                    () -> "Expected a clear ambiguity error, but got: " + exception);
        }
    }

    @Test
    void advancedSinkOptionsRejectNestedChoiceDestinations() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("choice-sink")
                            .from("kamelet:source")
                            .choice()
                            .when(header("selected").isEqualTo(true))
                            .to("mock:choice-selected?retain-first=0")
                            .otherwise()
                            .to("mock:choice-default?retain-first=0")
                            .end();

                    from("direct:trigger")
                            .kamelet("choice-sink?sink.retain-first=1");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for nested destinations, but got: " + exception);
            assertTrue(exceptionChainContains(exception, "no top-level static to() destination"),
                    () -> "Expected a clear no-static-sink error, but got: " + exception);
        }
    }

    @Test
    void sinkOptionsNormalizeDuplicateCamelCaseAndKebabCaseNames() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-with-duplicate-options")
                            .from("kamelet:source")
                            .to("mock:duplicate-options?retain-first=0");

                    from("direct:trigger")
                            .kamelet(
                                    "sink-with-duplicate-options?sink.retainFirst=1&sink.retain-first=2");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(
                    testContext, "mock:duplicate-options");
            ToDefinition sink = findToDefinition(expandedRoute, "mock:duplicate-options");

            assertNotNull(sink, "Expected duplicate-options sink endpoint");

            String uri = sink.getEndpointUri();
            assertEquals(1, countOccurrences(uri, "retain-first="),
                    () -> "Expected only one retain-first option: " + uri);
            assertTrue(uri.matches(".*retain-first=[^&]+.*"),
                    () -> "Expected a canonical retain-first option: " + uri);
            assertFalse(uri.contains("retainFirst="),
                    () -> "The camelCase alias should not remain in the URI: " + uri);
        }
    }

    @Test
    void testSinkOptionsWithDynamicOutputOnly() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("dynamic-sink")
                            .from("kamelet:source")
                            .toD("log:${header.destination}");

                    from("direct:trigger")
                            .kamelet("dynamic-sink?sink.block=true");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException, but got: " + exception);

            assertTrue(
                    exceptionChainContains(exception,
                            "sink.* advanced options cannot be applied"),
                    () -> "Expected a clear explanation for dynamic endpoints, but got: " + exception);
        }
    }

    @Test
    void testSinkOptionsWithFixedAndDynamicOutputs() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("mixed-sink")
                            .from("kamelet:source")
                            .to("mock:fixed-sink?retain-first=0")
                            .toD("mock:${header.destination}");

                    from("direct:trigger")
                            .kamelet("mixed-sink?sink.retain-first=1");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for mixed static/dynamic destinations, but got: "
                          + exception);
            assertTrue(exceptionChainContains(exception, "contains a dynamic destination"),
                    () -> "Expected a clear dynamic-destination error, but got: " + exception);
        }
    }

    @Test
    void testSinkOptionsWithEnrichOnly() throws Exception {
        assertDynamicSinkOptionsRejected("enrich", "enrich");
    }

    @Test
    void testSinkOptionsWithPollEnrichOnly() throws Exception {
        assertDynamicSinkOptionsRejected("pollEnrich", "pollEnrich");
    }

    @Test
    void wireTapIsNotTreatedAsTheMainSinkDestination() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("wiretap-sink")
                            .from("kamelet:source")
                            .wireTap("mock:side-effect")
                            .to("mock:main-sink?retain-first=0");

                    from("direct:trigger")
                            .kamelet("wiretap-sink?sink.retain-first=1");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "mock:main-sink");
            ToDefinition mainSink = findToDefinition(expandedRoute, "mock:main-sink");

            assertNotNull(mainSink, "Expected main sink endpoint");
            assertTrue(mainSink.getEndpointUri().contains("retain-first=1"),
                    () -> "Expected advanced option on main sink: " + mainSink.getEndpointUri());

            boolean wireTapHasAdvancedOption = expandedRoute.getOutputs().stream()
                    .filter(output -> output.getClass().getSimpleName().contains("WireTap"))
                    .anyMatch(output -> output.toString().contains("retain-first=1"));

            assertFalse(wireTapHasAdvancedOption,
                    "The wireTap destination should not receive sink options");
        }
    }

    @Test
    void testDynamicOutputWithoutSinkOptions() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("dynamic-sink")
                            .from("kamelet:source")
                            .toD("mock:${header.destination}");

                    from("direct:trigger")
                            .kamelet("dynamic-sink");
                }
            });

            testContext.start();

            MockEndpoint dynamicSink = testContext.getEndpoint("mock:dynamic-sink", MockEndpoint.class);
            dynamicSink.expectedBodiesReceived("hello");

            testContext.createProducerTemplate().sendBodyAndHeader(
                    "direct:trigger", "hello", "destination", "dynamic-sink");

            dynamicSink.assertIsSatisfied();
        }
    }

    @Test
    void routeScopedPropertyDoesNotClashWithAdvancedOptions() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-template")
                            .from("kamelet:source")
                            .to("mock:result?retain-first=0");

                    from("direct:trigger")
                            .kamelet("sink-template/testRoute?sink.retain-first=1");
                }
            });

            Properties properties = new Properties();
            // This is a route-scoped property that should NOT be treated as an advanced option
            properties.put("camel.kamelet.sink-template.testRoute.source.retainFirst", "999");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "mock:result");
            ToDefinition sink = findToDefinition(expandedRoute, "mock:result");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            assertTrue(uri.contains("retain-first=1"),
                    () -> "Expected URI parameter sink.retain-first=1 from advanced option: " + uri);
            assertFalse(uri.contains("retain-first=999"),
                    () -> "Route-scoped property should not be treated as advanced option: " + uri);
        }
    }

    @Test
    void routeScopedPropertyCannotInjectInternalPrefix() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-template")
                            .from("kamelet:source")
                            .to("mock:result?retain-first=0");

                    from("direct:trigger")
                            .kamelet("sink-template/testRoute");
                }
            });

            Properties properties = new Properties();
            // Attempt to inject via route-scoped property - should NOT work
            properties.put("camel.kamelet.sink-template.testRoute.internal.advanced.sink.retain-first", "999");
            testContext.getPropertiesComponent().setInitialProperties(properties);
            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "mock:result");
            ToDefinition sink = findToDefinition(expandedRoute, "mock:result");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            // The internal prefix should be ignored when coming from route-scoped properties
            assertFalse(uri.contains("retain-first=999"),
                    () -> "Route-scoped property with internal prefix should be ignored: " + uri);
            assertTrue(uri.contains("retain-first=0"),
                    () -> "Original value should remain: " + uri);
        }
    }

    @Test
    void removeQueryParameterPreservesRawAmpersands() throws Exception {
        String uri = "kamelet:my-source?param=RAW(value&with&ampersands)&other=123";

        var method = Kamelet.class.getDeclaredMethod(
                "removeQueryParameter", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, uri, "other");

        assertEquals(
                "kamelet:my-source?param=RAW(value&with&ampersands)",
                result);
    }

    @Test
    void sinkOptionsRejectAmbiguousLogAndStaticDestinations() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("sink-with-log")
                            .from("kamelet:source")
                            .to("log:before-sink")
                            .to("mock:actual-sink?retain-first=0");

                    from("direct:trigger")
                            .kamelet("sink-with-log?sink.retain-first=1");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for multiple static destinations, but got: "
                          + exception);
            assertTrue(exceptionChainContains(exception, "multiple top-level static to() destinations"),
                    () -> "Expected a clear ambiguity error, but got: " + exception);
        }
    }

    @Test
    void sinkOptionsPreserveOriginalCamelCaseSpelling() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("spelling-sink")
                            .from("kamelet:source")
                            .to("http://localhost/unused-page-size");

                    from("direct:trigger")
                            .kamelet("spelling-sink?sink.pageSize=10");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "http://localhost/unused-page-size");
            ToDefinition sink = findToDefinition(expandedRoute, "http://localhost/unused-page-size");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            assertTrue(uri.contains("pageSize=10"),
                    () -> "Expected original camelCase spelling to be preserved: " + uri);
            assertFalse(uri.contains("page-size="),
                    () -> "Parameter name must not be rewritten to kebab-case: " + uri);

            Map<String, Object> query = parseUriQuery(uri);
            assertEquals("10", String.valueOf(query.get("pageSize")));
            assertFalse(query.containsKey("page-size"),
                    () -> "Kebab-case alias must not be forwarded: " + query);
        }
    }

    @Test
    void sinkOptionsPreserveRawValuesWithUriSensitiveCharacters() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("password-sink")
                            .from("kamelet:source")
                            .to("http://localhost/unused-password");

                    from("direct:trigger")
                            .kamelet("password-sink?sink.password=RAW(p@ss&word)");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "http://localhost/unused-password");
            ToDefinition sink = findToDefinition(expandedRoute, "http://localhost/unused-password");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            Map<String, Object> query = parseUriQuery(uri);

            assertEquals("p@ss&word", rawOrPlain(query.get("password")),
                    () -> "Expected the password value to remain intact: " + uri);
            assertFalse(query.containsKey("word"),
                    () -> "Ampersand in the password must not create a spurious parameter: " + uri);

            HttpEndpoint http = findHttpEndpoint(testContext, "/unused-password");
            assertNotNull(http, "Expected the HTTP sink endpoint to be created");
            String rawQuery = http.getHttpUri().getRawQuery();
            assertNotNull(rawQuery, "Expected HTTP URI query parameters");
            assertFalse(rawQuery.contains("&word"),
                    () -> "The HTTP endpoint must not receive a spurious word parameter: " + http.getHttpUri());
            Map<String, Object> httpQuery = URISupport.parseQuery(rawQuery);
            assertEquals("p@ss&word", urlDecode(rawOrPlain(httpQuery.get("password"))),
                    () -> "Expected the HTTP endpoint to receive the password as one value: " + http.getHttpUri());
            assertFalse(httpQuery.containsKey("word"),
                    () -> "The HTTP endpoint must not receive a spurious word parameter: " + http.getHttpUri());
        }
    }

    @Test
    void advancedSinkOptionsSkipNestedKameletWhenSelectingMainSink() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("inner-sink")
                            .from("kamelet:source")
                            .to("mock:inner-sink");

                    routeTemplate("nested-and-main")
                            .from("kamelet:source")
                            .to("kamelet:inner-sink")
                            .to("mock:main-sink?retain-first=0");

                    from("direct:trigger")
                            .kamelet("nested-and-main?sink.retain-first=1");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(testContext, "mock:main-sink");
            ToDefinition mainSink = findToDefinition(expandedRoute, "mock:main-sink");
            ToDefinition nested = findToDefinition(expandedRoute, "kamelet:inner-sink");

            assertNotNull(mainSink, "Expected main sink endpoint");
            assertTrue(mainSink.getEndpointUri().contains("retain-first=1"),
                    () -> "Expected advanced option on the non-Kamelet sink: " + mainSink.getEndpointUri());

            assertNotNull(nested, "Expected nested Kamelet destination");
            assertFalse(nested.getEndpointUri().contains("retain-first="),
                    () -> "Nested kamelet: destination must not receive sink options: " + nested.getEndpointUri());
        }
    }

    @Test
    void advancedSinkOptionsRejectNestedKameletOnlyDestinations() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("inner-sink")
                            .from("kamelet:source")
                            .to("mock:inner-sink");

                    routeTemplate("nested-kamelet-sink")
                            .from("kamelet:source")
                            .to("kamelet:inner-sink");

                    from("direct:trigger")
                            .kamelet("nested-kamelet-sink?sink.retain-first=1");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);
            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException, but got: " + exception);
            assertTrue(exceptionChainContains(exception, "no top-level static to() destination"),
                    () -> "Expected no-static-sink error, but got: " + exception);
        }
    }

    @Test
    void sinkOptionsPreservePercentEncodedSecretThroughRouteTemplate() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("percent-password-sink")
                            .from("kamelet:source")
                            .to("http://localhost/unused-percent-secret");

                    from("direct:trigger")
                            .kamelet("percent-password-sink?sink.password=RAW(se+cret%41)");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(
                    testContext, "http://localhost/unused-percent-secret");
            ToDefinition sink = findToDefinition(expandedRoute, "http://localhost/unused-percent-secret");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            Map<String, Object> query = parseUriQuery(uri);
            String password = rawOrPlain(query.get("password"));

            assertEquals("se+cret%41", password,
                    () -> "Expected the original literal password to remain intact: " + uri);
            assertNotEquals("se cretA", password,
                    () -> "+ and % must not be URI-decoded: " + uri);
            assertNotEquals("se cret", password,
                    () -> "+ must not be decoded as a space: " + uri);
            assertNotEquals("secretA", password,
                    () -> "%41 must not be decoded as A: " + uri);

            HttpEndpoint http = findHttpEndpoint(testContext, "/unused-percent-secret");
            assertNotNull(http, "Expected the HTTP sink endpoint to be created");
            String rawQuery = http.getHttpUri().getRawQuery();
            assertNotNull(rawQuery, "Expected HTTP URI query parameters");
            Map<String, Object> httpQuery = URISupport.parseQuery(rawQuery);
            String httpPassword = urlDecode(rawOrPlain(httpQuery.get("password")));
            assertEquals("se+cret%41", httpPassword,
                    () -> "Expected the HTTP endpoint to receive the original literal password: "
                          + http.getHttpUri());
            assertNotEquals("se cretA", httpPassword,
                    () -> "+ and % must not be URI-decoded on the HTTP endpoint: " + http.getHttpUri());
        }
    }

    @Test
    void sinkOptionsPreserveJsonRawValueWithoutPropertyPlaceholder() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("json-filter-sink")
                            .from("kamelet:source")
                            .to("http://localhost/unused-json-filter");

                    from("direct:trigger")
                            .kamelet("json-filter-sink?sink.filter=RAW({\"a\":\"b&c\"})");
                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(
                    testContext, "http://localhost/unused-json-filter");
            ToDefinition sink = findToDefinition(expandedRoute, "http://localhost/unused-json-filter");

            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            assertFalse(uri.contains("RAW{{"),
                    () -> "JSON must not be wrapped as RAW{{...}} or it becomes a property placeholder: "
                          + uri);
            assertTrue(uri.contains("RAW("),
                    () -> "Expected RAW(...) wrapping for the JSON value: " + uri);

            Map<String, Object> query = parseUriQuery(uri);
            assertEquals("{\"a\":\"b&c\"}", rawOrPlain(query.get("filter")),
                    () -> "Expected the JSON value to remain intact: " + uri);
            assertFalse(query.containsKey("c\"}"),
                    () -> "Ampersand in the JSON must not create a spurious parameter: " + uri);
        }
    }

    @Disabled("Requires a shared URIScanner fix; RAW values containing )& inside quoted JSON are truncated during query parsing")
    @Test
    void sinkOptionsPreserveJsonRawValueWithClosingParenthesisAndAmpersand() throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("json-edge-case-sink")
                            .from("kamelet:source")
                            .to("http://localhost/unused-json-edge-case");

                    from("direct:trigger-json-edge-case")
                            .kamelet("json-edge-case-sink?sink.filter=RAW({\"filter\":\"(x)&y\"})");

                }
            });

            testContext.start();

            RouteDefinition expandedRoute = findExpandedRouteWithOutput(
                    testContext, "http://localhost/unused-json-edge-case");
            ToDefinition sink = findToDefinition(
                    expandedRoute, "http://localhost/unused-json-edge-case");

            assertNotNull(sink, "Expected sink endpoint for JSON edge case");

            String uri = sink.getEndpointUri();
            assertFalse(uri.contains("RAW{{"),
                    () -> "JSON must not be wrapped as RAW{{...}}: " + uri);
            Map<String, Object> query = parseUriQuery(uri);

            assertEquals("{\"filter\":\"(x)&y\"}", rawOrPlain(query.get("filter")),
                    () -> "Expected JSON containing )& to remain intact: " + uri);
            assertFalse(query.containsKey("y}"),
                    () -> "Ampersand in the JSON must not create a spurious parameter: " + uri);

            HttpEndpoint http = findHttpEndpoint(testContext, "/unused-json-edge-case");
            assertNotNull(http, "Expected the HTTP sink endpoint to be created");
            Map<String, Object> httpQuery = URISupport.parseQuery(http.getHttpUri().getRawQuery());
            assertEquals("{\"filter\":\"(x)&y\"}", urlDecode(rawOrPlain(httpQuery.get("filter"))),
                    () -> "Expected the HTTP endpoint to receive the complete JSON: " + http.getHttpUri());
            assertFalse(httpQuery.containsKey("y}"),
                    () -> "The HTTP endpoint must not receive a spurious y} parameter: " + http.getHttpUri());
        }
    }

    @Test
    void sinkOptionsPreserveMultipleAmpersands() throws Exception {
        assertHttpSinkOption("/unused-multi-amp", "sink.password=RAW(a&b&c)", "password", "a&b&c", "b", "c");
    }

    @Test
    void sinkOptionsPreserveUnquotedCloseParenAmpersand() throws Exception {
        assertHttpSinkOption("/unused-unquoted-paren-amp", "sink.password=RAW{p@ss)&word}", "password", "p@ss)&word",
                "word)");
    }

    @Test
    void sinkOptionsPreservePlusEqualsAndQuestionMark() throws Exception {
        assertHttpSinkOption("/unused-plus-eq-q", "sink.filter=RAW(a+b=c?d)", "filter", "a+b=c?d", new String[0]);
    }

    private void assertHttpSinkOption(
            String path, String sinkQuery, String optionName, String expectedValue, String... forbiddenKeys)
            throws Exception {
        String templateId = path.substring(1);
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate(templateId)
                            .from("kamelet:source")
                            .to("http://localhost" + path);

                    from("direct:trigger")
                            .kamelet(templateId + "?" + sinkQuery);
                }
            });

            testContext.start();

            ToDefinition sink = findToDefinition(
                    findExpandedRouteWithOutput(testContext, "http://localhost" + path),
                    "http://localhost" + path);
            assertNotNull(sink, "Expected sink endpoint");
            String uri = sink.getEndpointUri();
            Map<String, Object> query = parseUriQuery(uri);
            assertEquals(expectedValue, rawOrPlain(query.get(optionName)),
                    () -> "Expected option " + optionName + " to remain intact: " + uri);
            for (String forbidden : forbiddenKeys) {
                assertFalse(query.containsKey(forbidden),
                        () -> "Must not treat " + forbidden + " as a separate parameter: " + uri);
            }
        }
    }

    private Map<String, Object> parseUriQuery(String uri) throws Exception {
        String query = URISupport.extractQuery(uri);
        assertNotNull(query, () -> "Expected a query string on URI: " + uri);
        return URISupport.parseQuery(query);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String rawOrPlain(Object value) {
        assertNotNull(value, "Expected a query parameter value");
        String text = String.valueOf(value);
        if (text.startsWith("RAW(") && text.endsWith(")")) {
            return text.substring(4, text.length() - 1);
        }
        if (text.startsWith("RAW{") && text.endsWith("}")) {
            return text.substring(4, text.length() - 1);
        }
        return text;
    }

    private HttpEndpoint findHttpEndpoint(CamelContext testContext, String path) {
        return testContext.getEndpoints().stream()
                .filter(HttpEndpoint.class::isInstance)
                .map(HttpEndpoint.class::cast)
                .filter(endpoint -> endpoint.getHttpUri() != null
                        && endpoint.getHttpUri().getPath() != null
                        && endpoint.getHttpUri().getPath().contains(path))
                .findFirst()
                .orElse(null);
    }

    private void assertDynamicSinkOptionsRejected(String processor, String description) throws Exception {
        try (DefaultCamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    if ("enrich".equals(processor)) {
                        routeTemplate("dynamic-enricher")
                                .from("kamelet:source")
                                .enrich("mock:enrich-destination");
                    } else if ("pollEnrich".equals(processor)) {
                        routeTemplate("dynamic-enricher")
                                .from("kamelet:source")
                                .pollEnrich("mock:poll-enrich-destination");
                    } else {
                        throw new IllegalArgumentException("Unsupported processor: " + processor);
                    }

                    from("direct:trigger")
                            .kamelet("dynamic-enricher?sink.block=true");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);
            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for " + description + ", but got: " + exception);
            assertTrue(exceptionChainContains(exception,
                    "sink.* advanced options cannot be applied"),
                    () -> "Expected a clear explanation for " + description + ", but got: " + exception);
        }
    }

    private RouteDefinition findExpandedRouteWithOutput(CamelContext testContext, String uriPrefix) {
        return ((ModelCamelContext) testContext).getRouteDefinitions().stream()
                .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                .filter(route -> findToDefinition(route, uriPrefix) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expanded Kamelet route not found for " + uriPrefix));
    }

    private ToDefinition findToDefinition(RouteDefinition route, String uriPrefix) {
        return findToDefinitionInOutputs(route, uriPrefix);
    }

    private ToDefinition findToDefinitionInOutputs(
            ProcessorDefinition<?> parent, String uriPrefix) {
        if (parent.getOutputs() == null) {
            return null;
        }

        for (ProcessorDefinition<?> output : parent.getOutputs()) {
            if (output instanceof ToDefinition) {
                ToDefinition to = (ToDefinition) output;
                if (to.getEndpointUri() != null && to.getEndpointUri().startsWith(uriPrefix)) {
                    return to;
                }
            }

            ToDefinition nested = findToDefinitionInOutputs(output, uriPrefix);
            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private long countOccurrences(String value, String target) {
        return Pattern.compile(Pattern.quote(target))
                .matcher(value)
                .results()
                .count();
    }

    private boolean exceptionChainContains(Throwable exception, String text) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasIllegalArgumentException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private String findExpandedInputUri(String prefix) {
        String uri = context.getRouteDefinitions().stream()
                .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                .filter(route -> route.getInput() != null)
                .map(route -> route.getInput().getEndpointUri())
                .filter(endpointUri -> endpointUri != null)
                .filter(endpointUri -> endpointUri.startsWith(prefix))
                .findFirst()
                .orElse(null);

        assertNotNull(uri, () -> {
            String available = context.getRouteDefinitions().stream()
                    .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                    .filter(route -> route.getInput() != null)
                    .map(route -> route.getId() + " -> " + route.getInput().getEndpointUri())
                    .collect(java.util.stream.Collectors.joining(", "));
            return "No expanded Kamelet route input found with prefix " + prefix
                   + ". Available Kamelet inputs: " + available;
        });
        return uri;
    }

    private RouteDefinition getRoute(String routeId) {
        return context.getRouteDefinitions().stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("direct-source")
                        .from("direct:source?block=false")
                        .to("kamelet:sink");

                routeTemplate("direct-source-with-options")
                        .from("direct:source-with-options?block=false")
                        .to("kamelet:sink");

                from("direct:source-trigger")
                        .routeId("sourceTrigger")
                        .to("kamelet:direct-source/sourceTest?source.block=true");

                from("direct:source-override-trigger")
                        .routeId("sourceOverrideTrigger")
                        .to("kamelet:direct-source-with-options/sourceOverride?source.block=true");

                routeTemplate("mock-sink-with-options")
                        .from("kamelet:source")
                        .to("mock:advanced-sink-result?retain-first=0");

                from("direct:sink-trigger")
                        .routeId("sinkTrigger")
                        .kamelet("mock-sink-with-options?sink.retain-first=1&sink.retain-last=5");

                routeTemplate("mock-sink")
                        .from("kamelet:source")
                        .to("mock:basic-sink-result");

                from("direct:basic-trigger")
                        .routeId("basicTrigger")
                        .kamelet("mock-sink");
            }
        };
    }
}
