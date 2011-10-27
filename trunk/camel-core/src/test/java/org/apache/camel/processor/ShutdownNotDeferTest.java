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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.ShutdownRoute.Default;

/**
 * @version 
 */
public class ShutdownNotDeferTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/deferred");
        super.setUp();
    }

    public void testShutdownNotDeferred() throws Exception {
        // give it 20 seconds to shutdown
        context.getShutdownStrategy().setTimeout(20);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(1);

        template.sendBody("seda:foo", "A");
        template.sendBody("seda:foo", "B");
        template.sendBody("seda:foo", "C");
        template.sendBody("seda:foo", "D");
        template.sendBody("seda:foo", "E");

        assertMockEndpointsSatisfied();

        context.stop();

        // should not route all 5
        assertTrue("Should NOT complete all messages, was " + bar.getReceivedCounter(), bar.getReceivedCounter() < 5);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo")
                    .startupOrder(1)
                    .delay(1000).to("file://target/deferred");

                // use file component to transfer files from route 1 -> route 2
                from("file://target/deferred")
                    // do NOT defer it but use default for testing this
                    .startupOrder(2).shutdownRoute(Default)
                    .to("mock:bar");
            }
        };
    }
}