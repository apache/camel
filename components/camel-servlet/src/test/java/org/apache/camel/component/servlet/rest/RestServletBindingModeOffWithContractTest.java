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
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestServletBindingModeOffWithContractTest extends ServletCamelRouterTestSupport {

    @Test
    public void testBindingModeOffWithContract() throws Exception {
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

        MockEndpoint.assertIsSatisfied(context);

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
                restConfiguration().component("servlet").bindingMode(RestBindingMode.off);

                JsonDataFormat jsondf = new JsonDataFormat();
                jsondf.setLibrary(JsonLibrary.Jackson);
                jsondf.setAllowUnmarshallType(Boolean.toString(true));
                jsondf.setUnmarshalType(UserPojoEx.class);
                transformer()
                        .fromType("json")
                        .toType(UserPojoEx.class)
                        .withDataFormat(jsondf);
                transformer()
                        .fromType(UserPojoEx.class)
                        .toType("json")
                        .withDataFormat(jsondf);

                rest("/users/")
                        // REST binding does nothing
                        .post("new").to("direct:new");
                from("direct:new")
                        // contract advice converts between JSON and UserPojoEx directly
                        .inputType(UserPojoEx.class)
                        .outputType("json")
                        .process(ex -> ex.getIn().getBody(UserPojoEx.class).setActive(true))
                        .to("mock:input");
            }
        };
    }

}
