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
package org.apache.camel.component.vertx.http;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class VertxHttpMultiRouteTest extends VertxHttpTestSupport {
    @Test
    void testHttpConsumerToHttpProducer() throws Exception {
        String expectedBody = "Hello World";
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived(expectedBody);
        mockEndpoint.expectedHeaderReceived(Exchange.CONTENT_LENGTH, expectedBody.length());
        mockEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");
        mockEndpoint.expectedMessagesMatches(exchange -> {
            Object header = exchange.getMessage().getHeader("User-Agent");
            return header instanceof String && ((String) header).startsWith("Vert.x-WebClient");
        });

        template.requestBodyAndHeader("vertx-http:http://localhost:" + port + "/greeting", "World", Exchange.CONTENT_TYPE,
                "text/plain");

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().port(port);

                rest()
                        .post("/greeting")
                        .to("direct:greet")

                        .post("/hello")
                        .to("direct:hello");

                from("direct:greet")
                        .removeHeaders("CamelHttp*")
                        .toF("vertx-http:http://localhost:%d/hello?httpMethod=POST", port)
                        .to("mock:result");

                from("direct:hello")
                        .setBody().simple("Hello ${body}");
            }
        };
    }
}
