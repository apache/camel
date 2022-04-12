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
package org.apache.camel.opentracing;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.jupiter.api.Test;

public class EIPTracingRouteTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("a-log-1 server").setOperation("a-log-1")
                    .setParentId(11),
            new SpanTestData().setLabel("b-log server").setOperation("b-log")
                    .setParentId(3),
            new SpanTestData().setLabel("b-delay server").setOperation("b-delay")
                    .setParentId(3),
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setParentId(4).addLogMessage("routing at b"),
            new SpanTestData().setLabel("a-to-1 server").setOperation("a-to-1")
                    .setParentId(11),
            new SpanTestData().setLabel("a-delay server").setOperation("a-delay")
                    .setParentId(11),
            new SpanTestData().setLabel("c-to server").setOperation("c-to")
                    .setParentId(8).addLogMessage("Exchange[ExchangePattern: InOut, BodyType: String, Body: Hello]"),
            new SpanTestData().setLabel("c-delay server").setOperation("c-delay")
                    .setParentId(8),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setParentId(9),
            new SpanTestData().setLabel("a-to-2 server").setOperation("a-to-2")
                    .setParentId(11),
            new SpanTestData().setLabel("a-log-2 server").setOperation("a-log-2")
                    .setParentId(11),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setParentId(12).addLogMessage("routing at a").addLogMessage("End of routing"),
            new SpanTestData().setLabel("direct-to server").setOperation("direct-to")
                    .setParentId(13),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
    };

    public EIPTracingRouteTest() {
        super(testdata);
    }

    @Test
    public void testRoute() {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new OpenTracingTracingStrategy(ottracer);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .to("seda:a").id("direct-to");

                from("seda:a").routeId("a")
                        .log("routing at ${routeId}").id("a-log-1")
                        .to("seda:b").id("a-to-1")
                        .delay(2000).id("a-delay")
                        .to("seda:c").id("a-to-2")
                        .log("End of routing").id("a-log-2");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}").id("b-log")
                        .delay(simple("${random(1000,2000)}")).id("b-delay");

                from("seda:c").routeId("c")
                        .to("log:test").id("c-to")
                        .delay(simple("${random(0,100)}")).id("c-delay");
            }
        };
    }
}
