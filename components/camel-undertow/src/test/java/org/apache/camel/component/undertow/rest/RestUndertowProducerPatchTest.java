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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.Test;

public class RestUndertowProducerPatchTest extends BaseUndertowTest {

    @Test
    public void testUndertowProducerPatch() throws Exception {
        String body = "Donald Duck";
        String id = "123";

        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.message(0).body().isEqualTo(body);
        mock.message(0).header("id").isEqualTo(id);

        fluentTemplate.withBody(body).withHeader("id", id).to("direct:start").send();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort());

                from("direct:start")
                        .to("rest:patch:users/{id}");

                // use the rest DSL to define the rest services
                rest("/users/")
                        .patch("{id}")
                        .to("mock:input");
            }
        };
    }
}
