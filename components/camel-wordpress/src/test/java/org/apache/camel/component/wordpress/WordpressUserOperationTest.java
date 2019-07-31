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
package org.apache.camel.component.wordpress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.wordpress.api.model.User;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class WordpressUserOperationTest extends WordpressComponentTestSupport {

    @Test
    public void testUserSingleRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultSingle");
        mock.expectedMinimumMessageCount(1);
        mock.allMessages().body().isInstanceOf(User.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUserListRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultList");
        mock.expectedMinimumMessageCount(1);
        mock.allMessages().body().isInstanceOf(User.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInsertUser() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultInsert");
        mock.expectedBodyReceived().body(User.class);
        mock.expectedMessageCount(1);

        final User request = new User();
        request.setEmail("bill.denbrough@derry.com");
        request.setUsername("bdenbrough");
        request.setFirstName("Bill");
        request.setLastName("Denbrough");
        request.setNickname("Big Bill");

        final User response = (User)template.requestBody("direct:insertUser", request);
        assertThat(response.getId(), is(3));
        assertThat(response.getSlug(), is("bdenbrough"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdateUser() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultUpdate");
        mock.expectedBodyReceived().body(User.class);
        mock.expectedMessageCount(1);

        final User request = new User();
        request.setEmail("admin@email.com");

        final User response = (User)template.requestBody("direct:updateUser", request);
        assertThat(response.getId(), is(1));
        assertThat(response.getEmail(), is("admin@email.com"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDeleteUser() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultDelete");
        mock.expectedBodyReceived().body(User.class);
        mock.expectedMessageCount(1);

        final User response = (User)template.requestBody("direct:deleteUser", "");
        assertThat(response.getId(), is(4));
        assertThat(response.getUsername(), is("bmarsh"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                final WordpressComponentConfiguration configuration = new WordpressComponentConfiguration();
                final WordpressComponent component = new WordpressComponent();
                configuration.setUrl(getServerBaseUrl());
                component.setConfiguration(configuration);
                getContext().addComponent("wordpress", component);

                from("wordpress:user?criteria.perPage=10&criteria.orderBy=name&criteria.roles=admin,editor").to("mock:resultList");

                from("wordpress:user?id=114913").to("mock:resultSingle");

                from("direct:deleteUser").to("wordpress:user:delete?id=9&user=ben&password=password123").to("mock:resultDelete");
                from("direct:insertUser").to("wordpress:user?user=ben&password=password123").to("mock:resultInsert");
                from("direct:updateUser").to("wordpress:user?id=9&user=ben&password=password123").to("mock:resultUpdate");
            }
        };
    }

}
