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
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.Test;

import java.lang.reflect.Field;

public class EIPTracingActiveSpanTest extends CamelOpenTracingTestSupport {

    private static SpanTestData[] testdata = {
        new SpanTestData().setLabel("active-span server").setOperation("using-active-span")
            .setParentId(1),
        new SpanTestData().setLabel("process server").setOperation("direct-processor")
            .setParentId(2),
        new SpanTestData().setLabel("direct:start server").setUri("direct://start").setOperation("start")
            .setKind(Tags.SPAN_KIND_SERVER)
    };

    public EIPTracingActiveSpanTest() {
        super(testdata);
    }

    @Override
    public void setUp() throws Exception {
        GlobalTracerTestUtil.resetGlobalTracer();
        super.setUp();
    }

    @Test
    public void testRoute() throws Exception {
        template.requestBody("direct:start", "Hello");

        verify();
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new OpenTracingTracingStrategy(getTracer());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            from("direct:start").routeId("start")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // here you can use GlobalTracer if it's in your classpath or
                        // use the exchange context to look for a tracer in the registry
                        GlobalTracer.get().buildSpan("using-active-span")
                                .withTag(Tags.COMPONENT, "custom-component")
                                .asChildOf(GlobalTracer.get().activeSpan())
                                .start().finish();
                    }
                }).id("direct-processor");
            }
        };
    }
}
