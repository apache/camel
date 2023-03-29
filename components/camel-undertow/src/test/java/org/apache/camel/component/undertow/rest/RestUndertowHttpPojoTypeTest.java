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
package org.apache.camel.component.undertow.rest;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.model.Model;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestUndertowHttpPojoTypeTest extends BaseUndertowTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testUndertowPojoTypeValidateModel() {
        // Wasn't clear if there's a way to put this test into camel-core just to test the model
        // perhaps without starting the Camel Context?

        List<RestDefinition> restDefinitions
                = context().getCamelContextExtension().getContextPlugin(Model.class).getRestDefinitions();
        assertNotNull(restDefinitions);
        assertTrue(restDefinitions.size() > 0);

        RestDefinition restDefinition = restDefinitions.get(0);
        List<VerbDefinition> verbs = restDefinition.getVerbs();
        assertNotNull(verbs);

        Map<String, VerbDefinition> mapVerb = new TreeMap<>();

        verbs.forEach(verb -> mapVerb.put(verb.getId(), verb));

        assertEquals(UserPojo[].class.getCanonicalName(), mapVerb.get("getUsers").getOutType());
        assertEquals(UserPojo[].class.getCanonicalName(), mapVerb.get("getUsersList").getOutType());
        assertEquals(UserPojo.class.getCanonicalName(), mapVerb.get("getUser").getOutType());

        assertEquals(UserPojo.class.getCanonicalName(), mapVerb.get("putUser").getType());
        assertEquals(UserPojo[].class.getCanonicalName(), mapVerb.get("putUsers").getType());
        assertEquals(UserPojo[].class.getCanonicalName(), mapVerb.get("putUsersList").getType());
    }

    @Test
    public void testUndertowPojoTypeGetUsers() throws Exception {
        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
        });

        assertNotNull(outExchange);
        assertEquals("application/json", outExchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        String out = outExchange.getMessage().getBody(String.class);
        assertNotNull(out);

        UserPojo[] users = mapper.readValue(out, UserPojo[].class);
        assertEquals(2, users.length);
        assertEquals("Scott", users[0].getName());
        assertEquals("Claus", users[1].getName());
    }

    @Test
    public void testUndertowPojoTypePutUser() {
        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users/1", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            UserPojo user = new UserPojo();
            user.setId(1);
            user.setName("Scott");

            String body = mapper.writeValueAsString(user);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testUndertowPojoTypePutUserFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:putUser");
        mock.expectedMessageCount(0);

        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users/1", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            CountryPojo country = new CountryPojo();
            country.setIso("US");
            country.setCountry("United States");

            String body = mapper.writeValueAsString(country);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(400, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUndertowPojoTypePutUsers() throws Exception {
        UserPojo user1 = new UserPojo();
        user1.setId(1);
        user1.setName("Scott");

        UserPojo user2 = new UserPojo();
        user2.setId(2);
        user2.setName("Claus");

        final UserPojo[] users = new UserPojo[] { user1, user2 };

        MockEndpoint mock = getMockEndpoint("mock:putUsers");
        mock.expectedMessageCount(1);
        mock.message(0).body(UserPojo[].class);

        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            String body = mapper.writeValueAsString(users);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.assertExchangeReceived(0);
        UserPojo[] receivedUsers = exchange.getIn().getBody(UserPojo[].class);
        assertEquals(2, receivedUsers.length);
        assertEquals(user1.getName(), receivedUsers[0].getName());
        assertEquals(user2.getName(), receivedUsers[1].getName());
    }

    @Test
    public void testUndertowPojoTypePutUsersFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:putUsers");
        mock.expectedMessageCount(0);

        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            UserPojo user = new UserPojo();
            user.setId(1);
            user.setName("Scott");

            String body = mapper.writeValueAsString(user);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(400, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUndertowPojoTypePutUsersList() throws Exception {
        UserPojo user1 = new UserPojo();
        user1.setId(1);
        user1.setName("Scott");

        UserPojo user2 = new UserPojo();
        user2.setId(2);
        user2.setName("Claus");

        final UserPojo[] users = new UserPojo[] { user1, user2 };

        MockEndpoint mock = getMockEndpoint("mock:putUsersList");
        mock.expectedMessageCount(1);
        mock.message(0).body(UserPojo[].class);

        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users/list", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            String body = mapper.writeValueAsString(users);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.assertExchangeReceived(0);
        UserPojo[] receivedUsers = exchange.getIn().getBody(UserPojo[].class);
        assertEquals(2, receivedUsers.length);
        assertEquals(user1.getName(), receivedUsers[0].getName());
        assertEquals(user2.getName(), receivedUsers[1].getName());
    }

    @Test
    public void testUndertowPojoTypePutUsersListFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:putUsersList");
        mock.expectedMessageCount(0);

        Exchange outExchange = template.request("undertow:http://localhost:{{port}}/users/list", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

            UserPojo user = new UserPojo();
            user.setId(1);
            user.setName("Scott");

            String body = mapper.writeValueAsString(user);
            exchange.getIn().setBody(body);
        });

        assertNotNull(outExchange);
        assertEquals(400, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody().simple("${exchange.message}");

                // configure to use undertow on localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort())
                        .bindingMode(RestBindingMode.json);

                // use the rest DSL to define the rest services
                rest()
                        .get("/users").id("getUsers").outType(UserPojo[].class).to("direct:users")
                        .get("/users/list").id("getUsersList").outType(UserPojo[].class).to("direct:list")
                        .get("/users/{id}").id("getUser").outType(UserPojo.class).to("direct:id")
                        .put("/users/{id}").id("putUser").type(UserPojo.class).to("mock:putUser")
                        .put("/users").id("putUsers").type(UserPojo[].class).to("mock:putUsers")
                        .put("/users/list").id("putUsersList").type(UserPojo[].class).to("mock:putUsersList");

                from("direct:users")
                        .process(exchange -> {
                            UserPojo user1 = new UserPojo();
                            user1.setId(1);
                            user1.setName("Scott");

                            UserPojo user2 = new UserPojo();
                            user2.setId(2);
                            user2.setName("Claus");

                            exchange.getOut().setBody(new UserPojo[] { user1, user2 });
                        });

                from("direct:list")
                        .process(exchange -> {
                            UserPojo user1 = new UserPojo();
                            user1.setId(1);
                            user1.setName("Scott");

                            UserPojo user2 = new UserPojo();
                            user2.setId(2);
                            user2.setName("Claus");

                            exchange.getMessage().setBody(new UserPojo[] { user1, user2 });
                        });

                from("direct:id")
                        .process(exchange -> {
                            UserPojo user1 = new UserPojo();
                            user1.setId(exchange.getIn().getHeader("id", int.class));
                            user1.setName("Scott");
                            exchange.getMessage().setBody(user1);
                        });

            }
        };
    }

}
