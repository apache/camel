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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestUndertowHttpBindingModeAutoWithXmlTest extends BaseUndertowTest {

    @Test
    public void testBindingMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserJaxbPojo.class);

        String body = "<user name=\"Donald Duck\" id=\"123\"></user>";
        template.sendBody("undertow:http://localhost:{{port}}/users/new", body);

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
                restConfiguration().component("undertow").host("localhost").port(getPort()).bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("new").consumes("application/xml").type(UserJaxbPojo.class)
                        .to("mock:input");
            }
        };
    }

}
