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

import static org.apache.camel.ShutdownRoute.Defer;

/**
 * @version $Revision$
 */
public class ShutdownDeferTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/deferred");
        super.setUp();
    }

    public void testShutdownDeferred() throws Exception {
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

        Thread.sleep(50);

        context.stop();

        // should route all 5
        assertEquals(5, bar.getReceivedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() throws Exception {
                from("seda:foo")
                    .startupOrder(1)
                    .to("file://target/deferred");

                // use file component to transfer files from route 1 -> route 2 as it
                // will normally suspend, but by deferring this we can let route 1
                // complete while shutting down
                from("file://target/deferred")
                    // defer shutting down this route as the 1st route depends upon it
                    .startupOrder(2).shutdownRoute(Defer)
                    .to("mock:bar");
            }
            // END SNIPPET: e1
        };
    }
}
