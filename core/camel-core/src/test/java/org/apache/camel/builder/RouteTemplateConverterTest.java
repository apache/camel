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

import java.util.stream.IntStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteTemplateConverterTest extends ContextTestSupport {
    @Test
    public void testCreateRouteFromRouteTemplateWithDefaultConverter() throws Exception {
        context.addRouteTemplateDefinitionConverter("myTemplate1", RouteTemplateDefinition.Converter.DEFAULT_CONVERTER);
        context.addRouteFromTemplate("first", "myTemplate1", mapOf("foo", "one", "bar", "cheese"));

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());

        assertEquals("direct:{{foo}}", context.getRouteDefinition("first").getInput().getEndpointUri());
        assertEquals("direct://one", context.getRoute("first").getEndpoint().getEndpointUri());
    }

    @Test
    public void testCreateRouteFromRouteTemplateWithCustomConverter() throws Exception {
        context.addRouteTemplateDefinitionConverter("myTemplate1", (template, params) -> {
            final RouteDefinition def = template.asRouteDefinition();
            final String inUri = def.getInput().getEndpointUri();
            def.setInput(null);
            def.setInput(new FromDefinition(inUri + "?timeout=60s"));
            return def;
        });

        context.addRouteFromTemplate("first", "myTemplate1", mapOf("foo", "one", "bar", "cheese"));

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());

        assertEquals("direct:{{foo}}?timeout=60s", context.getRouteDefinition("first").getInput().getEndpointUri());
        assertEquals("direct://one?timeout=60s", context.getRoute("first").getEndpoint().getEndpointUri());
    }

    @Test
    public void testCreateRouteFromRouteTemplateWithCustomConverterAndProperties() throws Exception {
        context.addRouteTemplateDefinitionConverter("myTemplate1", (template, params) -> {
            Object timeout = params.remove("timeout");

            assertNotNull(timeout);

            final RouteDefinition def = template.asRouteDefinition();
            final String inUri = def.getInput().getEndpointUri();
            def.setInput(null);
            def.setInput(new FromDefinition(inUri + "?timeout=" + timeout));
            return def;
        });

        context.addRouteFromTemplate("first", "myTemplate1", mapOf("foo", "one", "bar", "cheese", "timeout", "60s"));

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());

        assertEquals("direct:{{foo}}?timeout=60s", context.getRouteDefinition("first").getInput().getEndpointUri());
        assertEquals("direct://one?timeout=60s", context.getRoute("first").getEndpoint().getEndpointUri());
    }

    @Test
    public void testCreateRouteFromRouteTemplateWithCustomConverterPatter() {
        context.addRouteTemplateDefinitionConverter("myTemplate[12]", (template, params) -> {
            final RouteDefinition def = template.asRouteDefinition();
            final String inUri = def.getInput().getEndpointUri();
            def.setInput(null);
            def.setInput(new FromDefinition(inUri + "?timeout=60s"));
            return def;
        });

        IntStream.of(1, 2, 3).mapToObj(Integer::toString).forEach(index -> {
            try {
                context.addRouteFromTemplate(index, "myTemplate" + index, mapOf("foo", index, "bar", "cheese"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(3, context.getRouteDefinitions().size());
        assertEquals(3, context.getRoutes().size());

        assertEquals("direct:{{foo}}?timeout=60s", context.getRouteDefinition("1").getInput().getEndpointUri());
        assertEquals("direct://1?timeout=60s", context.getRoute("1").getEndpoint().getEndpointUri());
        assertEquals("direct:{{foo}}?timeout=60s", context.getRouteDefinition("2").getInput().getEndpointUri());
        assertEquals("direct://2?timeout=60s", context.getRoute("2").getEndpoint().getEndpointUri());
        assertEquals("direct:{{foo}}", context.getRouteDefinition("3").getInput().getEndpointUri());
        assertEquals("direct://3", context.getRoute("3").getEndpoint().getEndpointUri());
    }

    @Test
    public void testCreateRouteFromRouteTemplateWithCustomConverterGlob() {
        context.addRouteTemplateDefinitionConverter("*", (template, params) -> {
            final RouteDefinition def = template.asRouteDefinition();
            final String inUri = def.getInput().getEndpointUri();
            def.setInput(null);
            def.setInput(new FromDefinition(inUri + "?timeout=60s"));
            return def;
        });

        IntStream.of(1, 2, 3).mapToObj(Integer::toString).forEach(index -> {
            try {
                context.addRouteFromTemplate(index, "myTemplate" + index, mapOf("foo", index, "bar", "cheese"));

                assertEquals("direct:{{foo}}?timeout=60s", context.getRouteDefinition(index).getInput().getEndpointUri());
                assertEquals("direct://" + index + "?timeout=60s", context.getRoute(index).getEndpoint().getEndpointUri());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate1")
                        .templateParameter("foo")
                        .templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
                routeTemplate("myTemplate2")
                        .templateParameter("foo")
                        .templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
                routeTemplate("myTemplate3")
                        .templateParameter("foo")
                        .templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        };
    }
}
