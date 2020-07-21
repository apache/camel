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
package org.apache.camel.component.log;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogListenerTest {
    private static boolean listenerFired;

    @Test
    public void testLogMask() throws Exception {
        listenerFired = false;
        CamelContext context = createCamelContext();
        MockEndpoint mock = context.getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMessageCount(1);
        context.adapt(ExtendedCamelContext.class).addLogListener((exchange, camelLogger, message) -> {
            assertEquals("Exchange[ExchangePattern: InOnly, BodyType: String, Body: hello]", message);
            listenerFired = true;
            return message + " - modified by listener";
        });
        context.start();
        context.createProducerTemplate().sendBody("direct:foo", "hello");
        mock.assertIsSatisfied();
        assertEquals(true, listenerFired);
        context.stop();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        return context;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("log:foo").to("mock:foo");
            }
        };
    }

}
