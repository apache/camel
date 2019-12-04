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

import io.opentracing.tag.Tags;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class CustomComponentNameRouteTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
        new SpanTestData().setLabel("myseda:b server").setUri("myseda://b").setOperation("b")
            .setKind(Tags.SPAN_KIND_SERVER).setParentId(1).addLogMessage("routing at b"),
        new SpanTestData().setLabel("myseda:b client").setUri("myseda://b").setOperation("b")
            .setKind(Tags.SPAN_KIND_CLIENT).setParentId(4),
        new SpanTestData().setLabel("myseda:c server").setUri("myseda://c").setOperation("c")
            .setKind(Tags.SPAN_KIND_SERVER).setParentId(3).addLogMessage("Exchange[ExchangePattern: InOut, BodyType: String, Body: Hello]"),
        new SpanTestData().setLabel("myseda:c client").setUri("myseda://c").setOperation("c")
            .setKind(Tags.SPAN_KIND_CLIENT).setParentId(4),
        new SpanTestData().setLabel("myseda:a server").setUri("myseda://a").setOperation("a")
            .setKind(Tags.SPAN_KIND_SERVER).setParentId(5).addLogMessage("routing at a").addLogMessage("End of routing"),
        new SpanTestData().setLabel("myseda:a client").setUri("myseda://a").setOperation("a")
            .setKind(Tags.SPAN_KIND_CLIENT).setParentId(6),
        new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
            .setKind(Tags.SPAN_KIND_SERVER).setParentId(7),
        new SpanTestData().setLabel("direct:start client").setUri("direct://start").setOperation("start")
            .setKind(Tags.SPAN_KIND_CLIENT)
    };

    public CustomComponentNameRouteTest() {
        super(testdata);
    }

    @Test
    public void testRoute() throws Exception {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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
