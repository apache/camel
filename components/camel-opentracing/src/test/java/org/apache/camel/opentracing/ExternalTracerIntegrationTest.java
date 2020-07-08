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
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ExternalTracerIntegrationTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("camel-direct").setUri("direct://foo").setOperation("customSpan")
                    .setKind(Tags.SPAN_KIND_CLIENT).setParentId(1),
            new SpanTestData().setLabel("direct:foo server").setUri("direct://foo").setOperation("foo")
                    .setKind(Tags.SPAN_KIND_SERVER).setParentId(2),
            new SpanTestData().setLabel("direct:foo client").setUri("direct://foo").setOperation("foo")
                    .setKind(Tags.SPAN_KIND_CLIENT)
    };

    public ExternalTracerIntegrationTest() {
        super(testdata);
    }

    @Test
    public void testSingleInvocationsOfRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBody("direct:foo", "Hello World");

        assertTrue(notify.matches(30, TimeUnit.SECONDS));

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context().getRegistry().bind("customSpanAdder", CustomSpanAdder.class);

                from("direct:foo").routeId("customSpanTest")
                        .log("routing at ${routeId}")
                        .bean("customSpanAdder", "addSpan");
            }
        };
    }
}
