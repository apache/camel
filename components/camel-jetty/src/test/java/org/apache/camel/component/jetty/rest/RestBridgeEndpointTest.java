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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

public class RestBridgeEndpointTest extends BaseJettyTest {

    @Test
    public void testJettyBridgeEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBody("http://localhost:" + getPort() + "/api/123/", null, String.class);
        assertEquals("Bye 123", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort());

                rest("/api/").get("/{id}/").to("jetty:http://localhost:" + getPort2() + "?bridgeEndpoint=true");

                from("jetty:http://localhost:" + getPort2() + "?matchOnUriPrefix=true")
                    .to("mock:result")
                    .transform().simple("Bye ${header.id}");
            }
        };
    }

}
