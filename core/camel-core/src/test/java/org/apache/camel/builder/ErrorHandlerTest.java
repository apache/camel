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

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorHandlerTest extends TestSupport {

    @Test
    public void testOverloadingTheDefaultErrorHandler() throws Exception {
        // START SNIPPET: e1
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                // use logging error handler
                errorHandler(deadLetterChannel("log:com.mycompany.foo"));

                // here is our regular route
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e1

        List<Route> list = getRouteList(builder);
        assertEquals(1, list.size(), "Number routes created" + list);
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("seda://a", key.getEndpointUri(), "From endpoint");

            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Channel channel = unwrapChannel(consumerRoute.getProcessor());

            assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());

            Processor processor = unwrap(channel.getNextProcessor());
            assertIsInstanceOf(SendProcessor.class, processor);
        }
    }

    @Test
    public void testOverloadingTheHandlerOnASingleRoute() throws Exception {

        // START SNIPPET: e2
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                // this route is using a nested logging error handler
                from("seda:a")
                        // here we configure the logging error handler
                        .errorHandler(deadLetterChannel("log:com.mycompany.foo"))
                        // and we continue with the routing here
                        .to("seda:b");

                // this route will use the default error handler
                // (DeadLetterChannel)
                from("seda:b").to("seda:c");
            }
        };
        // END SNIPPET: e2

        List<Route> list = getRouteList(builder);
        assertEquals(2, list.size(), "Number routes created" + list);
    }

    @Test
    public void testConfigureDeadLetterChannel() throws Exception {
        // START SNIPPET: e3
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                // using dead letter channel with a seda queue for errors
                errorHandler(deadLetterChannel("seda:errors"));

                // here is our route
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e3

        List<Route> list = getRouteList(builder);
        assertEquals(1, list.size(), "Number routes created" + list);
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("seda://a", key.getEndpointUri(), "From endpoint");

            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Channel channel = unwrapChannel(consumerRoute.getProcessor());

            assertIsInstanceOf(SendProcessor.class, channel.getNextProcessor());
        }
    }

    @Test
    public void testConfigureDeadLetterChannelWithCustomRedeliveryPolicy() throws Exception {
        // START SNIPPET: e4
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                // configures dead letter channel to use seda queue for errors
                // and use at most 2 redelveries
                // and exponential backoff
                errorHandler(deadLetterChannel("seda:errors").maximumRedeliveries(2).useExponentialBackOff());

                // here is our route
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e4

        List<Route> list = getRouteList(builder);
        assertEquals(1, list.size(), "Number routes created" + list);
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("seda://a", key.getEndpointUri(), "From endpoint");

            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Processor processor = consumerRoute.getProcessor();
            Channel channel = unwrapChannel(processor);

            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());
            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();

            assertEquals(2, redeliveryPolicy.getMaximumRedeliveries(), "getMaximumRedeliveries()");
            assertTrue(redeliveryPolicy.isUseExponentialBackOff(), "isUseExponentialBackOff()");
        }
    }

    @Test
    public void testLoggingErrorHandler() throws Exception {

        // START SNIPPET: e5
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").errorHandler(deadLetterChannel("log:FOO.BAR")).filter(body().isInstanceOf(String.class))
                        .to("seda:b");
            }
        };
        // END SNIPPET: e5

        List<Route> routes = getRouteList(builder);
        assertEquals(1, routes.size(), "Number routes created");
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("seda://a", key.getEndpointUri(), "From endpoint");
            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Channel channel = unwrapChannel(consumerRoute.getProcessor());

            assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());
            assertIsInstanceOf(FilterProcessor.class, channel.getNextProcessor());
        }
    }

}
