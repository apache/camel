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
package org.apache.camel.component.undertow;

import java.net.ConnectException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class UndertowConsumerUnregisterTest extends BaseUndertowTest {

    @Test
    public void testUnregisterUndertowConsumersForPort() throws Exception {
        UndertowComponent component = context.getComponent("undertow", UndertowComponent.class);
        UndertowConsumer consumerFoo = (UndertowConsumer) context.getRoute("route-foo").getConsumer();
        UndertowConsumer consumerBar = (UndertowConsumer) context.getRoute("route-bar").getConsumer();

        component.unregisterConsumer(consumerFoo);
        component.unregisterConsumer(consumerBar);

        try {
            template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);
            fail("Expected exception when connecting to undertow endpoint");
        } catch (CamelExecutionException e) {
            // Expected because unregistering all consumers should shut down the Undertow server
            assertTrue(e.getExchange().getException() instanceof ConnectException);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/foo").id("route-foo").to("mock:foo");
                from("undertow:http://localhost:{{port}}/bar").id("route-bar").to("mock:bar");
            }
        };
    }
}
