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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REST binding keeps the settings of a mapper of your own, here one that accepts unknown properties.
 */
public class RestJettyPostJsonPojoOwnObjectMapperTest extends BaseJettyTest {

    @BindToRegistry("myMapper")
    public JsonMapper myMapper() {
        return JsonMapper.builder().build();
    }

    @Test
    public void testUnknownPropertyIsAccepted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserPojo.class);

        String body = "{\"id\": 123, \"name\": \"Donald Duck\", \"donotexists\": \"foo\"}";
        template.sendBody("http://localhost:" + getPort() + "/users/new", body);

        MockEndpoint.assertIsSatisfied(context);
        UserPojo user = mock.getReceivedExchanges().get(0).getIn().getBody(UserPojo.class);
        assertEquals("Donald Duck", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("jetty").host("localhost").port(getPort()).bindingMode(RestBindingMode.json)
                        .dataFormatProperty("json.in.objectMapper", "#myMapper");

                rest("/users/").post("new").type(UserPojo.class).to("mock:input");
            }
        };
    }
}
