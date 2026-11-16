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
package org.apache.camel.util;

import java.io.StringReader;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exclude patterns given to removeHeaders and removeProperties in Java are dumped in the excludePattern option, so
 * the dumped route keeps the same headers and properties.
 */
public class DumpModelRemoveHeadersExcludePatternsTest extends ContextTestSupport {

    private static final Map<String, Object> HEADERS = Map.of("header1", "a", "header2", "b", "CamelFoo", "c",
            "BeerHeineken", "d", "BeerCarlsberg", "e");

    @Test
    public void testDumpModelAsXml() throws Exception {
        assertThat(xml("one")).contains("pattern=\"*\" excludePattern=\"keep\"/>");
        assertThat(xml("headers"))
                .contains("pattern=\"*\" excludePattern=\"header1|Camel.*|(?:Beer(Heineken|Tuborg))\"/>");
        assertThat(xml("properties")).contains("pattern=\"p*\" excludePattern=\"p1|p3\"/>");
    }

    @Test
    public void testDumpModelAsYaml() throws Exception {
        assertThat(yaml("one")).contains("excludePattern: keep");
        assertThat(yaml("headers")).contains("excludePattern: header1|Camel.*|(?:Beer(Heineken|Tuborg))");
        assertThat(yaml("properties")).contains("excludePattern: p1|p3");
    }

    @Test
    public void testDumpedRoutesKeepTheSameHeadersAndProperties() throws Exception {
        assertKept(context, template);

        RoutesDefinition dumped = new RoutesDefinition();
        for (String id : new String[] { "headers", "properties" }) {
            dumped.getRoutes().addAll(new ModelParser(new StringReader(xml(id))).parseRoutesDefinition().orElseThrow()
                    .getRoutes());
        }
        try (CamelContext other = new DefaultCamelContext()) {
            ((ModelCamelContext) other).addRouteDefinitions(dumped.getRoutes());
            other.start();
            assertKept(other, other.createProducerTemplate());
        }
    }

    private static void assertKept(CamelContext camelContext, ProducerTemplate producer) throws Exception {
        MockEndpoint headers = camelContext.getEndpoint("mock:headers", MockEndpoint.class);
        headers.expectedMessageCount(1);
        MockEndpoint properties = camelContext.getEndpoint("mock:properties", MockEndpoint.class);
        properties.expectedMessageCount(1);

        producer.sendBodyAndHeaders("direct:headers", "Hello", HEADERS);
        producer.sendBody("direct:properties", "Hello");
        MockEndpoint.assertIsSatisfied(camelContext);

        assertThat(headers.getReceivedExchanges().get(0).getMessage().getHeaders())
                .containsOnlyKeys("header1", "CamelFoo", "BeerHeineken");
        Exchange exchange = properties.getReceivedExchanges().get(0);
        assertThat(exchange.getProperties()).containsKeys("p1", "p3").doesNotContainKey("p2");
    }

    private String xml(String routeId) throws Exception {
        return PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition(routeId));
    }

    private String yaml(String routeId) throws Exception {
        return PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, context.getRouteDefinition(routeId));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:one").routeId("one")
                        .removeHeaders("*", "keep")
                        .to("mock:one");

                from("direct:headers").routeId("headers")
                        .removeHeaders("*", "header1", "Camel*", "Beer(Heineken|Tuborg)")
                        .to("mock:headers");

                from("direct:properties").routeId("properties")
                        .setProperty("p1", constant("a"))
                        .setProperty("p2", constant("b"))
                        .setProperty("p3", constant("c"))
                        .removeProperties("p*", "p1", "p3")
                        .to("mock:properties");
            }
        };
    }
}
