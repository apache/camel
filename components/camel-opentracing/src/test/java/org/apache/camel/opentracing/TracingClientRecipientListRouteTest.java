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

public class TracingClientRecipientListRouteTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("a: log").setOperation("a-log-1")
                    .setParentId(1),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setParentId(8),
            new SpanTestData().setLabel("b: log").setOperation("b-log-2")
                    .setParentId(4),
            new SpanTestData().setLabel("b: delay").setOperation("b-delay-1")
                    .setParentId(4),
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setParentId(8),
            new SpanTestData().setLabel("c: log").setOperation("c-log-3")
                    .setParentId(7),
            new SpanTestData().setLabel("c: delay").setOperation("c-delay-2")
                    .setParentId(7),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setParentId(8),
            new SpanTestData().setLabel("a: recipientList").setOperation("direct-recipientList-1")
                    .setParentId(9),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
    };

    public TracingClientRecipientListRouteTest() {
        super(testdata);
    }

    @Test
    public void testRoute() {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").recipientList(constant("seda:a,seda:b,seda:c")).id("direct-recipientList-1")
                        .routeId("start");

                from("seda:a").routeId("a")
                        .log("routing at ${routeId}").id("a-log-1");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}").id("b-log-2")
                        .delay(simple("${random(1000,2000)}")).id("b-delay-1");

                from("seda:c").routeId("c")
                        .log("routing at ${routeId}").id("c-log-3")
                        .delay(simple("${random(0,100)}")).id("c-delay-2");
            }
        };
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new OpenTracingTracingStrategy(ottracer);
    }
}
