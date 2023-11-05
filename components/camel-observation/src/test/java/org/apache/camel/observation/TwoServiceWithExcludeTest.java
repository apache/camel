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

class TwoServiceWithExcludeTest extends CamelMicrometerObservationTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("ServiceA server").setUri("direct://ServiceA").setOperation("ServiceA")
                    .setParentId(1)
                    .setKind(SpanKind.SERVER),
            new SpanTestData().setLabel("ServiceA server").setUri("direct://ServiceA").setOperation("ServiceA")
                    .setKind(SpanKind.CLIENT)
    };

    TwoServiceWithExcludeTest() {
        super(testdata);
    }

    @Override
    protected String getExcludePatterns() {
        return "direct:ServiceB";
    }

    @Test
    void testRoute() {
        template.requestBody("direct:ServiceA", "Hello");

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ServiceA")
                        .log("ServiceA has been called")
                        .delay(simple("${random(1000,2000)}"))
                        .to("direct:ServiceB");

                from("direct:ServiceB")
                        .log("ServiceB has been called")
                        .delay(simple("${random(0,500)}"));
            }
        };
    }
}
