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
package org.apache.camel.component.rest;

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RestDslEdgeCasesTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("dummy-rest", new DummyRestConsumerFactory());
        return registry;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private RouteDefinition restRoute() {
        return context.getRouteDefinitions().stream().filter(r -> Boolean.TRUE.equals(r.isRest())).findFirst()
                .orElseThrow();
    }

    @Test
    public void testResponseMessageWithIntCode() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost");
                rest("/r").get().responseMessage(404, "Not found").to("mock:r");
            }
        });
        assertThat(context.getRestDefinitions().get(0).getVerbs().get(0).getResponseMsgs())
                .singleElement().satisfies(m -> {
                    assertThat(m.getCode()).isEqualTo("404");
                    assertThat(m.getMessage()).isEqualTo("Not found");
                });
    }

    @Test
    public void testBindingModeWithPlaceholder() throws Exception {
        Properties prop = new Properties();
        prop.put("myMode", "off");
        context.getPropertiesComponent().setInitialProperties(prop);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost");
                rest("/b").bindingMode("{{myMode}}").get().to("mock:b");
            }
        });
        context.start();

        assertThat(context.getRestDefinitions().get(0).getBindingMode()).isEqualTo("{{myMode}}");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello");
        template.sendBody("seda:get-b", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumesAndProducesFromBindingModeOfRestConfiguration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").bindingMode(RestBindingMode.json);
                rest("/p").post().type(String.class).outType(String.class).to("mock:p");
            }
        });
        assertThat(restRoute().getRestBindingDefinition().getConsumes()).isEqualTo("application/json");
        assertThat(restRoute().getRestBindingDefinition().getProduces()).isEqualTo("application/json");
    }

    @Test
    public void testInlinedRouteKeepsStreamCacheOfVerb() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").inlineRoutes(true);
                rest("/s").get().to("direct:s");
                getRestCollection().getRests().get(0).getVerbs().get(0).setStreamCache("true");

                from("direct:s").to("mock:s");
            }
        });
        assertThat(restRoute().getStreamCache()).isEqualTo("true");
    }

    @Test
    public void testAcceptWithParametersAndClientRequestValidation() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").clientRequestValidation(true);
                rest("/q").produces("application/json").get().to("mock:q");
            }
        });
        context.start();

        getMockEndpoint("mock:q").expectedMessageCount(1);
        Exchange out = template.request("seda:get-q",
                e -> e.getMessage().setHeader("Accept", "application/xml;q=0.9, application/json"));
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isNull();
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testClientResponseValidationWithBindingOff() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").clientResponseValidation(true);
                rest("/h").get()
                        .responseMessage().code(200).header("X-Id").endHeader().endResponseMessage()
                        .to("direct:h");

                from("direct:h").setBody(constant("Hello"));
            }
        });
        context.start();

        // the response is validated also when binding is off
        Exchange out = template.request("seda:get-h", e -> {
        });
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(500);
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Some of the response HTTP headers are missing.");
    }

    @Test
    public void testClientResponseValidationHeadersPerResponseCode() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").clientResponseValidation(true);
                rest("/c").get()
                        .responseMessage().code(200).header("X-Id").endHeader().endResponseMessage()
                        .responseMessage().code(404).message("Not found").endResponseMessage()
                        .to("direct:c");

                from("direct:c")
                        .choice()
                        .when(header("found"))
                        .setHeader("X-Id", constant("123")).setBody(constant("Found"))
                        .otherwise()
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).setBody(constant("Not found"));
            }
        });
        context.start();

        // X-Id is only required on the 200 response
        Exchange out = template.request("seda:get-c", e -> {
        });
        System.out.println(
                "DEBUG " + out.getMessage().getHeaders() + " body=" + out.getMessage().getBody() + " ex=" + out.getException());
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(404);
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Not found");

        out = template.request("seda:get-c", e -> e.getMessage().setHeader("found", true));
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isNull();
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Found");
    }

    @Test
    public void testTypeRequiresBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").clientRequestValidation(true);
                rest("/t").post().type(String.class).to("mock:t");
            }
        });
        context.start();

        // the body parameter from type(...) is required (by default), so a missing body is a bad request
        getMockEndpoint("mock:t").expectedMessageCount(0);
        Exchange out = template.request("seda:post-t", e -> e.getMessage().setBody(null));
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("The request body is missing.");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDuplicateVerbId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost");
                rest("/d")
                        .get("/a").id("dup").to("mock:a")
                        .get("/b").id("dup").to("mock:b");
            }
        });

        // the duplicate id is reported (and not replaced by a generated id)
        assertThatThrownBy(() -> context.start()).hasStackTraceContaining("Duplicate id detected: dup");
    }

    @Test
    public void testSameDirectUsedByTwoRests() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").inlineRoutes(true);
                rest("/a").get().to("direct:x");
                rest("/b").get().to("direct:x");

                from("direct:x").routeId("x").to("mock:x");
            }
        });
        context.start();

        // the direct route is used by two rest services, so it is not inlined (into both of them)
        assertThat(context.getRouteDefinition("x")).isNotNull();
        assertThat(context.getRouteDefinitions()).hasSize(3);

        getMockEndpoint("mock:x").expectedBodiesReceivedInAnyOrder("A", "B");
        template.sendBody("seda:get-a", "A");
        template.sendBody("seda:get-b", "B");
        assertMockEndpointsSatisfied();
    }
}
