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
package org.apache.camel.component.jetty;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JettyEventNotifierTest extends BaseJettyTest {

    private final List<String> events = new ArrayList<>();

    @Test
    void testEventNotifierOk() throws Exception {
        context.getManagementStrategy().addEventNotifier(new MyEventListener());
        events.clear();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jetty:http://0.0.0.0:{{port}}/camel/ok").routeId("jetty")
                            .setBody().constant("Bye World");
                }
            });

            context.start();

            String out = template.requestBodyAndHeader("http://localhost:{{port}}/camel/ok", "Hello World",
                    Exchange.HTTP_METHOD, "GET", String.class);
            assertEquals("Bye World", out);

            Assertions.assertEquals(2, events.size());
            Assertions.assertEquals("ExchangeCreated (failed:false)", events.get(0));
            Assertions.assertEquals("ExchangeCompleted (failed:false)", events.get(1));
        } finally {
            context.stop();
        }
    }

    @Test
    void testEventNotifierError() throws Exception {
        context.getManagementStrategy().addEventNotifier(new MyEventListener());
        events.clear();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jetty:http://0.0.0.0:{{port}}/camel/fail").routeId("jetty")
                            .throwException(new IllegalArgumentException("Forced error"));
                }
            });

            context.start();

            String out = template.requestBodyAndHeader("http://localhost:{{port}}/camel/fail?throwExceptionOnFailure=false",
                    "Hello World",
                    Exchange.HTTP_METHOD, "GET", String.class);
            assertEquals("", out);

            Assertions.assertEquals(2, events.size());
            Assertions.assertEquals("ExchangeCreated (failed:false)", events.get(0));
            Assertions.assertEquals("ExchangeFailed (failed:true)", events.get(1));
        } finally {
            context.stop();
        }
    }

    class MyEventListener extends EventNotifierSupport {

        public void notify(CamelEvent event) throws Exception {
            if (event.getSource() instanceof Exchange) {
                Exchange ex = (Exchange) event.getSource();
                if ("jetty".equals(ex.getFromRouteId())) {
                    events.add(event.getType().name() + " (failed:" + ex.isFailed() + ")");
                }
            }
        }
    }

}
