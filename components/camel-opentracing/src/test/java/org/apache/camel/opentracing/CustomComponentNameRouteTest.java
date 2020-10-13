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
import org.junit.jupiter.api.Test;

public class CustomComponentNameRouteTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("myseda:b server").setUri("myseda://b").setOperation("b")
                    .setParentId(2).addLogMessage("routing at b"),
            new SpanTestData().setLabel("myseda:c server").setUri("myseda://c").setOperation("c")
                    .setParentId(2).addLogMessage("Exchange[ExchangePattern: InOut, BodyType: String, Body: Hello]"),
            new SpanTestData().setLabel("myseda:a server").setUri("myseda://a").setOperation("a")
                    .setParentId(3).addLogMessage("routing at a").addLogMessage("End of routing"),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
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
