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
package org.apache.camel.component.direct;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TwoCamelContextDirectEndpointTest extends Assert {
    private DefaultCamelContext camel1;
    private DefaultCamelContext camel2;

    @Before
    public void setUp() throws Exception {

        camel1 = new DefaultCamelContext();
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:foo");
                from("direct:foo").to("mock:a");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:foo");
                from("direct:foo").to("mock:b");
            }
        });
        camel2.start();
    }

    @After
    public void tearDown() throws Exception {
        camel1.stop();
        camel2.stop();

    }

    @Test
    public void testTwoCamelContextDirectEndpoint() throws Exception {
        Endpoint start1 = camel1.getEndpoint("direct:start");
        Endpoint start2 = camel2.getEndpoint("direct:start");
        assertNotSame(start1, start2);
        Endpoint foo1 = camel1.getEndpoint("direct:foo");
        Endpoint foo2 = camel2.getEndpoint("direct:foo");
        assertNotSame(foo1, foo2);

        MockEndpoint mock1 = camel1.getEndpoint("mock:a", MockEndpoint.class);
        mock1.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = camel2.getEndpoint("mock:b", MockEndpoint.class);
        mock2.expectedBodiesReceived("Bye World");

        camel1.createProducerTemplate().sendBody("direct:start", "Hello World");
        camel2.createProducerTemplate().sendBody("direct:start", "Bye World");

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }
}
