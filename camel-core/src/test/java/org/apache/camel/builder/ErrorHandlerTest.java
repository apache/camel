/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.SendProcessor;

/**
 * @version $Revision$
 */
public class ErrorHandlerTest extends TestSupport {
    /*
    public void testOverloadingTheDefaultErrorHandler() throws Exception {

        // START SNIPPET: e1
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                errorHandler(loggingErrorHandler("FOO.BAR"));

                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e1

        Map<Endpoint<Exchange>, Processor> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = route.getValue();

            LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);
        }
    }

    public void testOverloadingTheHandlerOnASingleRoute() throws Exception {

        // START SNIPPET: e2
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda:a").errorHandler(loggingErrorHandler("FOO.BAR")).to("seda:b");

                // this route will use the default error handler, DeadLetterChannel
                from("seda:b").to("seda:c");
            }
        };
        // END SNIPPET: e2

        Map<Endpoint<Exchange>, Processor> routeMap = builder.getRouteMap();
        log.debug(routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 2, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            String endpointUri = key.getEndpointUri();
            Processor processor = route.getValue();

            if (endpointUri.equals("seda:a")) {
                LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);

                Processor outputProcessor = loggingProcessor.getOutput();
                SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
            else {
                assertEquals("From endpoint", "seda:b", endpointUri);

                DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, processor);
                Processor outputProcessor = deadLetterChannel.getOutput();
                SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
        }
    }

    public void testConfigureDeadLetterChannel() throws Exception {

        // START SNIPPET: e3
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                errorHandler(deadLetterChannel("seda:errors"));

                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e3

        Map<Endpoint<Exchange>, Processor> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = route.getValue();

            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, processor);
            Endpoint deadLetterEndpoint = assertIsInstanceOf(Endpoint.class, deadLetterChannel.getDeadLetter());
            assertEndpointUri(deadLetterEndpoint, "seda:errors");
        }
    }

    public void testConfigureDeadLetterChannelWithCustomRedeliveryPolicy() throws Exception {

        // START SNIPPET: e4
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                errorHandler(deadLetterChannel("seda:errors").maximumRedeliveries(2).useExponentialBackOff());

                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e4

        Map<Endpoint<Exchange>, Processor> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = route.getValue();

            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, processor);
            Endpoint deadLetterEndpoint = assertIsInstanceOf(Endpoint.class, deadLetterChannel.getDeadLetter());
            assertEndpointUri(deadLetterEndpoint, "seda:errors");
            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();
            assertEquals("getMaximumRedeliveries()", 2, redeliveryPolicy.getMaximumRedeliveries());
            assertEquals("isUseExponentialBackOff()", true, redeliveryPolicy.isUseExponentialBackOff());
        }
    }         */

    public void testDisablingInheritenceOfErrorHandlers() throws Exception {

        // START SNIPPET: e5
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {

                from("seda:a").
                        errorHandler(loggingErrorHandler("FOO.BAR")).
                        filter(body().isInstanceOf(String.class)).
                        inheritErrorHandler(false).
                        to("seda:b");
            }
        };
        // END SNIPPET: e5

        List<Route> routes = builder.getRouteList();
        assertEquals("Number routes created", 1, routes.size());
        for (Route<Exchange> route : routes) {
            Endpoint<Exchange> key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);
            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, loggingProcessor.getOutput());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, filterProcessor.getProcessor());
        }
    }
}
