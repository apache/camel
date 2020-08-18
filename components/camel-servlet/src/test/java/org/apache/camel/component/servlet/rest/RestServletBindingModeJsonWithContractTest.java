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

import org.apache.camel.Converter;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestServletBindingModeJsonWithContractTest extends ServletCamelRouterTestSupport {

    @Test
    public void testBindingModeJsonWithContract() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserPojoEx.class);

        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/new",
                new ByteArrayInputStream(body.getBytes()), "application/json");
        WebResponse response = query(req, false);
        assertEquals(200, response.getResponseCode());
        String answer = response.getText();
        assertTrue(answer.contains("\"active\":true"), "Unexpected response: " + answer);

        assertMockEndpointsSatisfied();

        Object obj = mock.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(UserPojoEx.class, obj.getClass());
        UserPojoEx user = (UserPojoEx) obj;
        assertNotNull(user);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
        assertEquals(true, user.isActive());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
                restConfiguration().component("servlet").bindingMode(RestBindingMode.json);

                rest("/users/")
                        // REST binding converts from JSON to UserPojo
                        .post("new").type(UserPojo.class)
                        .route()
                        // then contract advice converts from UserPojo to UserPojoEx
                        .inputType(UserPojoEx.class)
                        .to("mock:input");
            }
        };
    }

    public static class MyTypeConverters implements TypeConverters {
        @Converter
        public UserPojoEx toEx(UserPojo user) {
            UserPojoEx ex = new UserPojoEx();
            ex.setId(user.getId());
            ex.setName(user.getName());
            ex.setActive(true);
            return ex;
        }
    }
}
