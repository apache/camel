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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

public class RestJettyMethodNotAllowedTest extends BaseJettyTest {

    @Test
    public void testMethodNotAllowed() {
        try {
            template.sendBody("http://localhost:" + getPort() + "/users/123/basic", "body");
            fail("Shall not pass!");
        } catch (Exception e) {
            HttpOperationFailedException hofe = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(405, hofe.getStatusCode());
        }
    }

    @Test
    public void testMethodAllowed() {
        try {
            template.sendBodyAndHeader("http://localhost:" + getPort() + "/users/123/basic", "body", Exchange.HTTP_METHOD, "GET");
        } catch (Exception e) {
            fail("Shall pass with GET http method!");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use jetty on localhost
                restConfiguration().component("jetty").host("localhost").port(getPort());

                // use the rest DSL to define the rest services
                rest("/users/").get("{id}/basic").route().to("mock:input").process(exchange -> {
                    String id = exchange.getIn().getHeader("id", String.class);
                    exchange.getOut().setBody(id + ";Donald Duck");
                });
            }
        };
    }
}
