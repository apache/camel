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

public class TracingMulticastParallelRouteTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("a: a-log-1").setOperation("a-log-1")
                    .setParentId(11),
            new SpanTestData().setLabel("b: b-log-3").setOperation("b-log-3")
                    .setParentId(7),
            new SpanTestData().setLabel("c: c-log-4").setOperation("c-log-4")
                    .setParentId(4),
            new SpanTestData().setLabel("c: c-delay-2").setOperation("c-delay-2")
                    .setParentId(4),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setParentId(5).addLogMessage("routing at c"),
            new SpanTestData().setLabel("a:multicast: a-to-3").setOperation("a-to-3")
                    .setParentId(9),
            new SpanTestData().setLabel("b: b-delay-1").setOperation("b-delay-1")
                    .setParentId(7),
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setParentId(8).addLogMessage("routing at b"),
            new SpanTestData().setLabel("a:multicast: a-to-2").setOperation("a-to-2")
                    .setParentId(9),
            new SpanTestData().setLabel("a: multicast").setOperation("a-multicast-1")
                    .setParentId(11),
            new SpanTestData().setLabel("a: a-log-2").setOperation("a-log-2")
                    .setParentId(11),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setParentId(12).addLogMessage("routing at a").addLogMessage("End of routing"),
            new SpanTestData().setLabel("direct:start server").setOperation("direct-to-1")
                    .setParentId(13),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
    };

    public TracingMulticastParallelRouteTest() {
        super(testdata);
    }

    @Test
    public void testRoute() {
        template.requestBody("direct:start", "Hello");

        verify(true);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:a").id("direct-to-1").routeId("start");

                from("seda:a").routeId("a")
                        .log("routing at ${routeId}").id("a-log-1")
                        .multicast().parallelProcessing().id("a-multicast-1")
                        // to(String...) only sets the .id(String) on the first element, others will be dynamic
                        .to("seda:b").id("a-to-2")
                        .to("seda:c").id("a-to-3")
                        .end()
                        .log("End of routing").id("a-log-2");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}").id("b-log-3")
                        .delay(simple("${random(1000,2000)}")).id("b-delay-1");

                from("seda:c").routeId("c")
                        .log("routing at ${routeId}").id("c-log-4")
                        .delay(simple("${random(0,100)}")).id("c-delay-2");
            }
        };
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new OpenTracingTracingStrategy(ottracer);
    }
}
