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
}
