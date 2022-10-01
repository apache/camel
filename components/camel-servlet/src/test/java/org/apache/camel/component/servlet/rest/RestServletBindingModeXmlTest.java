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
package org.apache.camel.component.servlet.rest;

import java.io.ByteArrayInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestServletBindingModeXmlTest extends ServletCamelRouterTestSupport {

    @Test
    public void testBindingMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserJaxbPojo.class);

        String body = "<user name=\"Donald Duck\" id=\"123\"></user>";
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/new",
                new ByteArrayInputStream(body.getBytes()), "application/xml");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());

        MockEndpoint.assertIsSatisfied(context);

        UserJaxbPojo user = mock.getReceivedExchanges().get(0).getIn().getBody(UserJaxbPojo.class);
        assertNotNull(user);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
    }

    @Test
    public void testBindingModeWrong() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(0);

        // we bind to xml, but send in json, which is not possible
        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/new",
                new ByteArrayInputStream(body.getBytes()), "application/json");
        WebResponse response = query(req, false);

        assertEquals(500, response.getResponseCode());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("servlet").bindingMode(RestBindingMode.xml);

                // use the rest DSL to define the rest services
                rest("/users/")
                        .post("new").type(UserJaxbPojo.class)
                        .to("mock:input");
            }
        };
    }

}
