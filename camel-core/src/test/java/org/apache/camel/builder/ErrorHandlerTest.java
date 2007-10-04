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
package org.apache.camel.builder;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.SendProcessor;

import java.util.List;

/**
 * @version $Revision$
 */
public class ErrorHandlerTest extends TestSupport {

    // TODO
    public void TODO_testOverloadingTheDefaultErrorHandler() throws Exception {
        // START SNIPPET: e1
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(loggingErrorHandler("FOO.BAR"));
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e1

        List<Route> list = getRouteList(builder);
        assertEquals("Number routes created " + list, 1, list.size());
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());

            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, loggingProcessor.getOutput());
            log.debug("Found sendProcessor: " + sendProcessor);
        }
    }

    public void   testOverloadingTheHandlerOnASingleRoute() throws Exception {

        // START SNIPPET: e2
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").errorHandler(loggingErrorHandler("FOO.BAR")).to("seda:b");
                // this route will use the default error handler, DeadLetterChannel
                from("seda:b").to("seda:c");
            }
        };
        // END SNIPPET: e2

        List<Route> list = getRouteList(builder);
        assertEquals("Number routes created " + list, 2, list.size());
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            String endpointUri = key.getEndpointUri();
            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = unwrap(consumerRoute.getProcessor());

            SendProcessor sendProcessor = null;
            if (endpointUri.equals("seda:a")) {
                LoggingErrorHandler
                        loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class,
                        processor);
                Processor outputProcessor = loggingProcessor.getOutput();
                sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
            else {
                assertEquals("From endpoint", "seda:b",
                        endpointUri);
                DeadLetterChannel deadLetterChannel =
                        assertIsInstanceOf(DeadLetterChannel.class, processor);
                Processor
                        outputProcessor = deadLetterChannel.getOutput();
                sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
            log.debug("For " + endpointUri + " using: " + sendProcessor);
        }
    }

    public void testConfigureDeadLetterChannel() throws Exception {
        // START SNIPPET: e3
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("seda:errors"));
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e3

        List<Route> list = getRouteList(builder);
        assertEquals("Number routes created " + list, 1, list.size());
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());

            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = unwrap(consumerRoute.getProcessor());

            assertIsInstanceOf(DeadLetterChannel.class, processor);
        }
    }

    // TODO
    public void TODO_testConfigureDeadLetterChannelWithCustomRedeliveryPolicy() throws Exception {
        // START SNIPPET: e4
        RouteBuilder builder = new
                RouteBuilder() {
                    public void configure() {
                        errorHandler(deadLetterChannel("seda:errors").maximumRedeliveries(2).useExponentialBackOff());
                        from("seda:a").to("seda:b");
                    }
                };
        // END SNIPPET: e4

        List<Route> list = getRouteList(builder);
        assertEquals("Number routes created " + list, 1, list.size());
        for (Route route : list) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());

            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = consumerRoute.getProcessor();

            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, processor);

            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();

            assertEquals("getMaximumRedeliveries()", 2, redeliveryPolicy.getMaximumRedeliveries());
            assertEquals("isUseExponentialBackOff()", true, redeliveryPolicy.isUseExponentialBackOff());
        }
    }

    public void testDisablingInheritenceOfErrorHandlers() throws Exception {

        // START SNIPPET: e5
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {

                from("seda:a").errorHandler(loggingErrorHandler("FOO.BAR")).filter(body().isInstanceOf(String.class)).inheritErrorHandler(false).to("seda:b");
            }
        };
        // END SNIPPET: e5

        List<Route> routes = getRouteList(builder);
        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Processor processor = unwrap(consumerRoute.getProcessor());

            LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);
            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, loggingProcessor.getOutput());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, filterProcessor.getProcessor());
            log.debug("Found sendProcessor: " + sendProcessor);
        }
    }
}
