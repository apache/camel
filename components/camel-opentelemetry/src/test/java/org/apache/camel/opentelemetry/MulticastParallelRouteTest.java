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

import io.opentelemetry.api.trace.SpanKind;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

class MulticastParallelRouteTest extends CamelOpenTelemetryTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setParentId(1).addLogMessage("routing at b"),
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setParentId(4)
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setParentId(3).addLogMessage("routing at c"),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setParentId(4)
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setParentId(5).addLogMessage("routing at a").addLogMessage("End of routing"),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setParentId(6)
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
                    .setParentId(7),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
                    .setKind(SpanKind.CLIENT)
    };

    MulticastParallelRouteTest() {
        super(testdata);
    }

    @Test
    void testRoute() {
        template.requestBody("direct:start", "Hello");
        verify(true);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:a").routeId("start");

                from("seda:a").routeId("a")
                        .log("routing at ${routeId}")
                        .multicast().parallelProcessing()
                        .to("seda:b", "seda:c")
                        .end()
                        .log("End of routing");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(0,100)}"));
            }
        };
    }
}
