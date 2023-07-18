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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XmlLoadRestTest {
    @RegisterExtension
    private static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private CamelContext context;

    @BeforeEach
    void setupTest() {
        context = camelContextExtension.getContext();

        context.getCamelContextExtension().getRegistry().bind("dummy-rest", new DummyRestConsumerFactory());
    }

    @RouteFixture
    public void setupRoute(CamelContext camelContext) throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .host("localhost")
                        .component("dummy-rest");

                from("direct:foo")
                        .routeId("foo")
                        .to("mock:foo");
            }
        });
    }

    @Test
    public void testLoadRoutesBuilderFromXml() throws Exception {
        assertNotNull(context.getRoute("foo"), "Existing foo route should be there");
        assertEquals(1, context.getRoutes().size());

        // test that existing route works
        MockEndpoint foo = context.getEndpoint("mock:foo", MockEndpoint.class);
        foo.expectedBodiesReceived("Hello World");
        context.createProducerTemplate().sendBody("direct:foo", "Hello World");
        foo.assertIsSatisfied();

        // load rest from XML and add them to the existing camel context
        Resource resource = PluginHelper.getResourceLoader(context).resolveResource(
                "/org/apache/camel/dsl/xml/io/barRest.xml");

        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        assertEquals(2, context.getRoutes().size());

        // test that loaded route works
        MockEndpoint bar = context.getEndpoint("mock:bar", MockEndpoint.class);
        bar.expectedBodiesReceived("Bye World");
        context.createProducerTemplate().sendBody("seda:get-say-hello-bar", "Bye World");
        bar.assertIsSatisfied();
    }
}
