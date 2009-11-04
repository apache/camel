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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class MulticastExecutorTest extends ContextTestSupport {

    protected static final String DEFAULT_EXECUTOR_ENDPOINT = "seda:inputDefaultExecutor";

    protected Endpoint<Exchange> startEndpoint;
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    public void testSendingAMessageUsingMulticastAdequateExecutorPool() throws Exception {
        this.x.expectedBodiesReceived("input");
        this.x.expectedMessageCount(40);
        this.y.expectedBodiesReceived("input");
        this.y.expectedMessageCount(40);
        this.z.expectedBodiesReceived("input");
        this.z.expectedMessageCount(40);

        (new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 40; i++) {
                    template.sendBody(DEFAULT_EXECUTOR_ENDPOINT, "input");
                }
            }
        })).start();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        this.x = getMockEndpoint("mock:x");
        this.y = getMockEndpoint("mock:y");
        this.z = getMockEndpoint("mock:z");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from(DEFAULT_EXECUTOR_ENDPOINT + "?concurrentConsumers=5").
                        multicast().
                        parallelProcessing().
                        to("mock:x", "mock:y").
                        to("mock:z");
                // END SNIPPET: example
            }
        };
    }
}
