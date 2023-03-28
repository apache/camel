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
package org.apache.camel.observation;

import io.opentelemetry.api.trace.SpanKind;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

class CustomComponentNameRouteTest extends CamelMicrometerObservationTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("myseda:b server").setUri("myseda://b").setOperation("b")
                    .setKind(SpanKind.SERVER)
                    .setParentId(1),
            new SpanTestData().setLabel("myseda:b server").setUri("myseda://b").setOperation("b").setKind(SpanKind.CLIENT)
                    .setParentId(4),
            new SpanTestData().setLabel("myseda:c server").setUri("myseda://c").setOperation("c")
                    .setKind(SpanKind.SERVER)
                    .setParentId(3),
            new SpanTestData().setLabel("myseda:c server").setUri("myseda://c").setOperation("c").setKind(SpanKind.CLIENT)
                    .setParentId(4),
            new SpanTestData().setLabel("myseda:a server").setUri("myseda://a").setOperation("a")
                    .setKind(SpanKind.SERVER)
                    .setParentId(5),
            new SpanTestData().setLabel("myseda:a server").setUri("myseda://a").setOperation("a")
                    .setParentId(6)
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
                    .setKind(SpanKind.SERVER).setParentId(7),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
                    .setKind(SpanKind.CLIENT)
    };

    CustomComponentNameRouteTest() {
        super(testdata);
    }

    @Test
    void testRoute() {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("myseda", context.getComponent("seda"));

                from("direct:start").to("myseda:a").routeId("start");

                from("myseda:a").routeId("a")
                        .log("routing at ${routeId}")
                        .to("myseda:b")
                        .delay(2000)
                        .to("myseda:c")
                        .log("End of routing");

                from("myseda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));

                from("myseda:c").routeId("c")
                        .to("log:test")
                        .delay(simple("${random(0,100)}"));
            }
        };
    }
}
