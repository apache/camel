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
package org.apache.camel.component.platform.http.vertx;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class VertxPlatformEventNotifierTest {

    private final List<String> events = new ArrayList<>();

    @Test
    void testEventNotifierOk() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new MyEventListener());
        events.clear();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/camel/ok")
                            .setBody().constant("Bye World");
                }
            });

            context.start();

            given()
                    .body("Hello World")
                    .post("/camel/ok")
                    .then()
                    .statusCode(200)
                    .body(is("Bye World"));

            Assertions.assertEquals(2, events.size());
            Assertions.assertEquals("ExchangeCreated (failed:false)", events.get(0));
            Assertions.assertEquals("ExchangeCompleted (failed:false)", events.get(1));
        } finally {
            context.stop();
        }
    }

    @Test
    void testEventNotifierError() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new MyEventListener());
        events.clear();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/camel/fail")
                            .throwException(new IllegalArgumentException("Forced error"));
                }
            });

            context.start();

            given()
                    .body("Hello World")
                    .post("/camel/fail")
                    .then()
                    .statusCode(500)
                    .body(is(""));

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
                events.add(event.getType().name() + " (failed:" + ex.isFailed() + ")");
            }
        }
    }

}
