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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DirectRequestReplyAndSedaInOnlyTest extends ContextTestSupport {

    @Test
    public void testInOut() throws Exception {
        getMockEndpoint("mock:log").expectedBodiesReceived("Logging: Bye World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
        log.info("Got reply " + out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // send the message as InOnly to SEDA as we want to continue
                // routing
                // (as we don't want to do request/reply over SEDA)
                // In EIP patterns the WireTap pattern is what this would be
                from("direct:start").transform(constant("Bye World")).inOnly("seda:log");

                from("seda:log").transform(body().prepend("Logging: ")).to("log:log", "mock:log");
            }
        };
    }
}
