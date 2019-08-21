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
package org.apache.camel.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.or;

public class ChoiceCompoundPredicateTest extends ContextTestSupport {

    @Test
    public void testGuest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:guest");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUser() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:user");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "username", "goofy");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdmin() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:admin");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("username", "donald");
        headers.put("admin", "true");
        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:god");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("username", "pluto");
        headers.put("admin", "true");
        headers.put("type", "god");
        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRiderGod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:god");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("username", "Camel");
        headers.put("admin", "true");
        template.sendBodyAndHeaders("direct:start", "Hello Camel Rider", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                // We define 3 predicates based on some user roles
                // we have static imported and/or from
                // org.apache.camel.builder.PredicateBuilder

                // First we have a regular user that is just identified having a
                // username header
                Predicate user = header("username").isNotNull();

                // The admin user must be a user AND have a admin header as true
                Predicate admin = and(user, header("admin").isEqualTo("true"));

                // And God must be an admin and (either have type god or a
                // special message containing Camel Rider)
                Predicate god = and(admin, or(body().contains("Camel Rider"), header("type").isEqualTo("god")));

                // As you can see with the predicates above we can stack them to
                // build compound predicates

                // In our route below we can create a nice content based router
                // based on the predicates we
                // have defined. Then the route is easy to read and understand.
                // We encourage you to define complex predicates outside the
                // fluent router builder as
                // it will just get a bit complex for humans to read
                from("direct:start").choice().when(god).to("mock:god").when(admin).to("mock:admin").when(user).to("mock:user").otherwise().to("mock:guest").end();
                // END SNIPPET: e1
            }
        };
    }

}
