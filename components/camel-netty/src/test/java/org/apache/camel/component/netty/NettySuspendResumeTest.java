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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettySuspendResumeTest extends BaseNettyTest {
    
    @Test
    public void testSuspendResume() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Camel", "Again");

        String out = template.requestBody("netty:tcp://localhost:{{port}}?sync=true&disconnect=true", "Camel", String.class);
        assertEquals("Bye Camel", out);

        context.suspendRoute("foo");

        try {
            template.requestBody("netty:tcp://localhost:{{port}}?sync=true&disconnect=true", "World", String.class);
            fail("Should not allow connecting as its suspended");
        } catch (Exception e) {
            // expected
        }

        context.resumeRoute("foo");

        out = template.requestBody("netty:tcp://localhost:{{port}}?sync=true&disconnect=true", "Again", String.class);
        assertEquals("Bye Again", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}?sync=true").routeId("foo")
                    .to("log:result")
                    .to("mock:result")
                    .transform(body().prepend("Bye "));
            }
        };
    }

}
