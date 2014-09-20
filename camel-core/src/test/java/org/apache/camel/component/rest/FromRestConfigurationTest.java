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
package org.apache.camel.component.rest;

import org.apache.camel.builder.RouteBuilder;

public class FromRestConfigurationTest extends FromRestGetTest {

    @Override
    public void testFromRestModel() throws Exception {
        super.testFromRestModel();

        assertEquals("dummy-rest", context.getRestConfiguration().getComponent());
        assertEquals("localhost", context.getRestConfiguration().getHost());
        assertEquals(9090, context.getRestConfiguration().getPort());
        assertEquals("bar", context.getRestConfiguration().getComponentProperties().get("foo"));
        assertEquals("stuff", context.getRestConfiguration().getComponentProperties().get("other"));
        assertEquals("200", context.getRestConfiguration().getEndpointProperties().get("size"));
        assertEquals("1000", context.getRestConfiguration().getConsumerProperties().get("pollTimeout"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("dummy-rest").host("localhost").port(9090)
                    .componentProperty("foo", "bar")
                    .componentProperty("other", "stuff")
                    .endpointProperty("size", "200")
                    .consumerProperty("pollTimeout", "1000");

                rest("/say/hello")
                        .get().to("direct:hello");

                rest("/say/bye")
                        .get().consumes("application/json").to("direct:bye")
                        .post().to("mock:update");

                from("direct:hello")
                    .transform().constant("Hello World");

                from("direct:bye")
                    .transform().constant("Bye World");
            }
        };
    }
}
