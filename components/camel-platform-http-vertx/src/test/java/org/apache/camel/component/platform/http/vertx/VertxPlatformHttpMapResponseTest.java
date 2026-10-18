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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25751: a Map or a List as the reply - a body unmarshalled from JSON, XML or CSV - says to marshal it first, in
 * the data format the Content-Type names.
 */
public class VertxPlatformHttpMapResponseTest {

    @Test
    public void testMapOrListReply() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        final RecordingExceptionHandler handler = new RecordingExceptionHandler();
        context.getRegistry().bind("recording", handler);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/stock?exceptionHandler=#recording")
                            .process(exchange -> {
                                Map<String, Object> stock = new LinkedHashMap<>();
                                stock.put("sku", "A1");
                                exchange.getMessage().setBody(stock);
                            });
                    from("platform-http:/lines?exceptionHandler=#recording")
                            .setHeader(Exchange.CONTENT_TYPE, constant("text/csv"))
                            .process(exchange -> exchange.getMessage().setBody(List.of(List.of("A1", "2"))));
                    from("platform-http:/order?exceptionHandler=#recording")
                            .setHeader(Exchange.CONTENT_TYPE, constant("application/xml"))
                            .process(exchange -> exchange.getMessage().setBody(Map.of("orderId", "1001")));
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given().when().get("/stock").then().statusCode(500);
            given().when().get("/lines").then().statusCode(500);
            given().when().get("/order").then().statusCode(500);

            assertEquals(3, handler.handled.size(), () -> "Handled: " + handler.handled);
            // no Content-Type: the usual data formats
            String map = assertInstanceOf(NoTypeConversionAvailableException.class, handler.handled.get(0)).getMessage();
            assertTrue(map.startsWith("No type converter available to convert from type: java.util.LinkedHashMap"
                                      + " to the required type: java.io.InputStream"),
                    map);
            assertTrue(map.contains("a Map is not an HTTP response body: marshal it first with the data format of the reply"
                                    + " (such as marshal: json, marshal: jacksonXml or marshal: csv) as the last step of the"
                                    + " route, or let the REST binding write it (bindingMode: json or xml)"),
                    map);
            // the Content-Type names the data format; the REST binding does not write csv
            String csv = handler.handled.get(1).getMessage();
            assertTrue(csv.contains("a List is not an HTTP response body: marshal it first (marshal: csv, for the Content-Type"
                                    + " text/csv) as the last step of the route"),
                    csv);
            assertFalse(csv.contains("REST binding"), csv);
            String xml = handler.handled.get(2).getMessage();
            assertTrue(xml.contains("marshal it first (marshal: jacksonXml, for the Content-Type application/xml) as the last"
                                    + " step of the route, or let the REST binding write it (bindingMode: xml)"),
                    xml);
        } finally {
            context.stop();
        }
    }

    private static final class RecordingExceptionHandler implements ExceptionHandler {
        private final List<Throwable> handled = new CopyOnWriteArrayList<>();

        @Override
        public void handleException(Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handled.add(exception);
        }
    }
}
