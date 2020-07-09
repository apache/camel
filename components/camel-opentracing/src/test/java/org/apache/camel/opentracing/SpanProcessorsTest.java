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
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

public class SpanProcessorsTest extends CamelOpenTracingTestSupport {

    private static final SpanTestData[] testdata = {
            new SpanTestData().setLabel("seda:b server").setUri("seda://b").setOperation("b")
                    .setKind(Tags.SPAN_KIND_SERVER).setParentId(1).addLogMessage("routing at b")
                    .addTag("b-tag", "request-header-value"),
            new SpanTestData().setLabel("seda:b client").setUri("seda://b").setOperation("b")
                    .setKind(Tags.SPAN_KIND_CLIENT).setParentId(4),
            new SpanTestData().setLabel("seda:c server").setUri("seda://c").setOperation("c")
                    .setKind(Tags.SPAN_KIND_SERVER).setParentId(3).addLogMessage("Exchange[ExchangePattern: InOut, BodyType: String, Body: Hello]"),
            new SpanTestData().setLabel("seda:c client").setUri("seda://c").setOperation("c")
                    .setKind(Tags.SPAN_KIND_CLIENT).setParentId(4),
            new SpanTestData().setLabel("seda:a server").setUri("seda://a").setOperation("a")
                    .setKind(Tags.SPAN_KIND_SERVER).setParentId(5).addLogMessage("routing at a").addLogMessage("End of routing")
                    .addBaggage("a-baggage", "request-header-value"),
            new SpanTestData().setLabel("seda:a client").setUri("seda://a").setOperation("a")
                    .setKind(Tags.SPAN_KIND_CLIENT).setParentId(6),
            new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
                    .setKind(Tags.SPAN_KIND_SERVER).setParentId(7),
            new SpanTestData().setLabel("direct:start client").setUri("direct://start").setOperation("start")
                    .setKind(Tags.SPAN_KIND_CLIENT)
    };

    public SpanProcessorsTest() {
        super(testdata);
    }

    @Test
    public void testRoute() throws Exception {
        Exchange result = template.request("direct:start",
                exchange -> {
                    exchange.getIn().setBody("Hello");
                    exchange.getIn().setHeader("request-header", simple("request-header-value"));
                });

        verify();
        assertEquals("request-header-value", result.getMessage().getHeader("baggage-header", String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:a").routeId("start");

                from("seda:a").routeId("a")
                        .log("routing at ${routeId}")
                        .process(new SetBaggageProcessor("a-baggage", simple("${header.request-header}")))
                        .to("seda:b")
                        .delay(2000)
                        .to("seda:c")
                        .log("End of routing");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .process(new TagProcessor("b-tag", simple("${header.request-header}")))
                        .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                        .to("log:test")
                        .process(new GetBaggageProcessor("a-baggage", "baggage-header"))
                        .delay(simple("${random(0,100)}"));
            }
        };
    }
}
