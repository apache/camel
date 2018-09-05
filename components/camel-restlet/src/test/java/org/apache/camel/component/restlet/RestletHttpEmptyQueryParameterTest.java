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
package org.apache.camel.component.restlet;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class RestletHttpEmptyQueryParameterTest extends RestletTestSupport {

    @Test
    public void testEmpty() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived("id", 123);
        String out = fluentTemplate.to("restlet:http://localhost:" + portNum + "/foo?id=123").request(String.class);
        assertEquals("Header: 123", out);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:input").expectedHeaderReceived("id", "");
        out = fluentTemplate.to("restlet:http://localhost:" + portNum + "/foo?id=").request(String.class);
        assertEquals("Header: ", out);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://0.0.0.0:" + portNum + "/foo")
                    .to("mock:input")
                    .transform().simple("Header: ${header.id}");
            }
        };
    }
}
