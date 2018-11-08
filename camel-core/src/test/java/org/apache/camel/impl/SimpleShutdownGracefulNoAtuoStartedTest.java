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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class SimpleShutdownGracefulNoAtuoStartedTest extends ContextTestSupport {

    private static String foo = "";

    @Test
    public void testShutdownGraceful() throws Exception {
        context.startRoute("foo");

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBody("seda:foo", "Hello World");
        assertMockEndpointsSatisfied();

        // now stop the route before its complete
        foo = foo + "stop";
        context.stop();

        // it should wait as there was 1 inflight exchange when asked to stop
        assertEquals("Should graceful shutdown", "stopHello World", foo);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").noAutoStartup().to("mock:foo").delay(3000).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        foo = foo + exchange.getIn().getBody(String.class);
                    }
                });
            }
        };
    }
}