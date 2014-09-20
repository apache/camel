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
package org.apache.camel.component.netty.http.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.http.BaseNettyTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestNettyHttpPostJsonJaxbPojoTest extends BaseNettyTest {

    @Test
    public void testPostJaxbPojo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserJaxbPojo.class);

        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        template.sendBody("netty-http:http://localhost:" + getPort() + "/users/new", body);

        assertMockEndpointsSatisfied();

        UserJaxbPojo user = mock.getReceivedExchanges().get(0).getIn().getBody(UserJaxbPojo.class);
        assertNotNull(user);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use netty-http on localhost with the given port
                // and enable auto binding mode
                restConfiguration().component("netty-http").host("localhost").port(getPort()).bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("new").type(UserJaxbPojo.class)
                        .to("mock:input");
            }
        };
    }

}
