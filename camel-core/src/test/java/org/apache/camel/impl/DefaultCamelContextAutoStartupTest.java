/**
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
package org.apache.camel.impl;

import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class DefaultCamelContextAutoStartupTest extends TestSupport {

    // TODO: We should have a JMX test of this as well

    public void testAutoStartupFalseContextStart() throws Exception {
        DefaultCamelContext camel = new DefaultCamelContext(new SimpleRegistry());
        camel.disableJMX();
        camel.setAutoStartup(false);

        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:result");
            }
        });
        camel.start();

        assertEquals(true, camel.isStarted());
        assertEquals(1, camel.getRoutes().size());
        assertEquals(true, camel.getRouteStatus("foo").isStopped());

        // now start all routes
        camel.startAllRoutes();

        assertEquals(true, camel.getRouteStatus("foo").isStarted());

        // and now its started we can test that it works by sending in a message to the route
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
        
        camel.stop();
    }

    public void testAutoStartupFalseRouteStart() throws Exception {
        DefaultCamelContext camel = new DefaultCamelContext(new SimpleRegistry());
        camel.disableJMX();
        camel.setAutoStartup(false);

        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:result");
            }
        });
        camel.start();

        assertEquals(true, camel.isStarted());
        assertEquals(1, camel.getRoutes().size());
        assertEquals(true, camel.getRouteStatus("foo").isStopped());

        // now start the routes
        camel.startRoute("foo");

        assertEquals(true, camel.getRouteStatus("foo").isStarted());

        // and now its started we can test that it works by sending in a message to the route
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        camel.stop();
    }

    public void testAutoStartupTrue() throws Exception {
        DefaultCamelContext camel = new DefaultCamelContext(new SimpleRegistry());
        camel.disableJMX();
        camel.setAutoStartup(true);

        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:result");
            }
        });
        camel.start();

        assertEquals(true, camel.isStarted());
        assertEquals(1, camel.getRoutes().size());
        assertEquals(true, camel.getRouteStatus("foo").isStarted());

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
        
        camel.stop();
    }

    public void testAutoStartupFalseRouteOverride() throws Exception {
        DefaultCamelContext camel = new DefaultCamelContext(new SimpleRegistry());
        camel.disableJMX();
        camel.setAutoStartup(false);

        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").autoStartup(true).to("mock:result");
            }
        });
        camel.start();

        // this is special, when you have auto startup=false on CamelContext, then NO routes is started

        assertEquals(true, camel.isStarted());
        assertEquals(1, camel.getRoutes().size());
        assertEquals(true, camel.getRouteStatus("foo").isStopped());
        assertEquals(false, camel.getRouteStatus("foo").isStarted());

        // now start all the routes
        camel.startAllRoutes();

        assertEquals(true, camel.getRouteStatus("foo").isStarted());

        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        camel.stop();
    }

}
