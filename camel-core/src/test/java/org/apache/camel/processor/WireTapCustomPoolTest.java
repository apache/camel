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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Wire tap unit test
 *
 * @version 
 */
public class WireTapCustomPoolTest extends ContextTestSupport {
    protected MockEndpoint tap;
    protected MockEndpoint result;
    protected ExecutorService pool;
    
    protected void tearDown() throws Exception {
        super.tearDown();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    public void testSend() throws Exception {
        // hello must come first, as we have delay on the tapped route
        result.expectedBodiesReceived("Hello World", "Tapped");
        tap.expectedBodiesReceived("Tapped");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tap = getMockEndpoint("mock:tap");
        result = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                // use a custom thread pool for sending tapped messages
                ExecutorService pool = Executors.newFixedThreadPool(2);

                from("direct:start")
                    .to("log:foo")
                    // pass in the custom pool to the wireTap DSL
                    .wireTap("direct:tap").executorService(pool)
                    .to("mock:result");
                // END SNIPPET: e1

                from("direct:tap")
                    .delay(1000).setBody().constant("Tapped")
                    .to("mock:result", "mock:tap");
            }
        };
    }
}