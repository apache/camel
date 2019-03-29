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
import java.util.List;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestServletPostJsonPojoListTest extends ServletCamelRouterTestSupport {

    @Test
    public void testPostJaxbPojo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);

        String body = "[ {\"id\": 123, \"name\": \"Donald Duck\"}, {\"id\": 456, \"name\": \"John Doe\"} ]";
        WebRequest req = new PostMethodWebRequest(CONTEXT_URL + "/services/users/new", new ByteArrayInputStream(body.getBytes()), "application/json");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);

        assertEquals(200, response.getResponseCode());
        assertEquals("application/json", response.getContentType());

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertNotNull(list);
        assertEquals(2, list.size());

        UserPojo user = (UserPojo) list.get(0);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
        user = (UserPojo) list.get(1);
        assertEquals(456, user.getId());
        assertEquals("John Doe", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("servlet").bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("new").type(UserPojo[].class)
                        .to("mock:input");
            }
        };
    }

}
