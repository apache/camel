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
package org.apache.camel.component.netty4.http.rest;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty4.http.BaseNettyTest;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.Test;

public class RestNettyHttpBindingModeOffWithContractTest extends BaseNettyTest {

    @Test
    public void testBindingModeOffWithContract() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserPojoEx.class);

        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        Object answer = template.requestBodyAndHeader("netty4-http:http://localhost:" + getPort() + "/users/new", body, Exchange.CONTENT_TYPE, "application/json");
        assertNotNull(answer);
        String answerString = new String((byte[])answer);
        assertTrue("Unexpected response: " + answerString, answerString.contains("\"active\":true"));

        assertMockEndpointsSatisfied();

        Object obj = mock.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(UserPojoEx.class, obj.getClass());
        UserPojoEx user = (UserPojoEx)obj;
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
                restConfiguration().component("netty4-http").host("localhost").port(getPort()).bindingMode(RestBindingMode.off);

                JsonDataFormat jsondf = new JsonDataFormat();
                jsondf.setLibrary(JsonLibrary.Jackson);
                jsondf.setAllowUnmarshallType(true);
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
                    .post("new")
                    .route()
                        // contract advice converts betweeen JSON and UserPojoEx directly
                        .inputType(UserPojoEx.class)
                        .outputType("json")
                        .process(ex -> {
                            ex.getIn().getBody(UserPojoEx.class).setActive(true);
                        })
                        .to("mock:input");
            }
        };
    }

}
