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
package org.apache.camel.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateCustomSourceTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("mySource", new MyRouteTemplateParameterSource());
        return context;
    }

    @Test
    public void testCreateRouteFromRouteTemplateMissingParameter() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        assertEquals(2, context.getRoutes().size());

        MockEndpoint mock = getMockEndpoint("mock:cheese");
        mock.expectedBodiesReceived("Hello Foo", "Hello Bar");

        template.sendBody("direct:one", "Hello Foo");
        template.sendBody("direct:two", "Hello Bar");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        };
    }

    private static class MyRouteTemplateParameterSource implements RouteTemplateParameterSource {

        @Override
        public Map<String, Object> parameters(String routeId) {
            Map<String, Object> map = new HashMap<>();
            map.put(TEMPLATE_ID, "myTemplate");
            map.put("bar", "cheese");
            if ("A".equals(routeId)) {
                map.put("foo", "one");
            } else {
                map.put("foo", "two");
            }
            return map;
        }

        @Override
        public Set<String> routeIds() {
            return Stream.of("A", "B").collect(Collectors.toSet());
        }
    }
}
