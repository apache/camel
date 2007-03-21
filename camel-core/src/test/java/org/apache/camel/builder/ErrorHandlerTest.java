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

import junit.framework.TestCase;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.TestSupport;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.DeadLetterChannel;

import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
public class ErrorHandlerTest extends TestSupport {
    public void testOverloadingTheDefaultErrorHandler() throws Exception {

        // START SNIPPET: e1
        RouteBuilder<Exchange> builder1 = new RouteBuilder<Exchange>() {
            public void configure() {
                errorHandler(loggingErrorHandler("FOO.BAR"));

                from("queue:a").to("queue:b");
            }
        };
        // END SNIPPET: e1
        RouteBuilder<Exchange> builder = builder1;

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = route.getValue();

            LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);
        }
    }

    public void testOverloadingTheHandlerOnASingleRoute() throws Exception {

        // START SNIPPET: e2
        RouteBuilder<Exchange> builder1 = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").errorHandler(loggingErrorHandler("FOO.BAR")).to("queue:b");

                // this route will use the default error handler, DeadLetterChannel
                from("queue:b").to("queue:c");
            }
        };
        // END SNIPPET: e2
        RouteBuilder<Exchange> builder = builder1;

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        log.info(routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 2, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            String endpointUri = key.getEndpointUri();
            Processor processor = route.getValue();

            if (endpointUri.equals("queue:a")) {
                LoggingErrorHandler loggingProcessor = assertIsInstanceOf(LoggingErrorHandler.class, processor);

                Processor outputProcessor = loggingProcessor.getOutput();
                SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
            else {
                assertEquals("From endpoint", "queue:b", endpointUri);

                DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, processor);
                Processor outputProcessor = deadLetterChannel.getOutput();
                SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, outputProcessor);
            }
        }
    }
}
