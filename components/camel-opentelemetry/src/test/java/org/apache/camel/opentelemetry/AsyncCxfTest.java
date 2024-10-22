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
package org.apache.camel.opentelemetry;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class AsyncCxfTest extends CamelOpenTelemetryTestSupport {

    private static int port1 = CXFTestSupport.getPort1();

    private static SpanTestData[] testdata = {}; // not used yet, fix context leak first

    AsyncCxfTest() {
        super(testdata);
    }

    @Test
    void testRoute() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(4);
        int num = 4;
        for (int i = 0; i < num; i++) {
            template.requestBody("direct:start", "foo");
        }
        mock.assertIsSatisfied(5000);
        verifyTraceSpanNumbers(num, 9);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .to("direct:send")
                        .end();

                from("direct:send")
                        .log("message")
                        .to("cxfrs:http://localhost:" + port1
                            + "/rest/helloservice/sayHello?synchronous=false");

                restConfiguration()
                        .port(port1);

                rest("/rest/helloservice")
                        .post("/sayHello").routeId("rest-GET-say-hi")
                        .to("direct:sayHi");

                from("direct:sayHi")
                        .routeId("mock-GET-say-hi")
                        .log("example")
                        .to("mock:end");
            }
        };
    }
}
