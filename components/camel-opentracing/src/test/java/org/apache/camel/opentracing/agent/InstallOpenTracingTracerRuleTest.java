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
package org.apache.camel.opentracing.agent;

import java.util.List;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.mock.MockTracer.Propagator;
import io.opentracing.tag.Tags;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class InstallOpenTracingTracerRuleTest extends CamelTestSupport {

    @BindToRegistry("tracer")
    private static MockTracer tracer = new MockTracer(Propagator.TEXT_MAP);

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @BeforeEach
    public void init() {
        tracer.reset();
    }

    public static MockTracer getTracer() {
        return tracer;
    }

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        resultEndpoint.assertIsSatisfied();

        List<MockSpan> spans = getTracer().finishedSpans();
        assertEquals(3, spans.size());
        assertEquals("mock", spans.get(0).operationName());
        assertEquals("start", spans.get(1).operationName());
        assertEquals("start", spans.get(2).operationName());
        assertEquals(Tags.SPAN_KIND_CLIENT, spans.get(0).tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, spans.get(1).tags().get(Tags.SPAN_KIND.getKey()));
        assertEquals(Tags.SPAN_KIND_CLIENT, spans.get(2).tags().get(Tags.SPAN_KIND.getKey()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").filter(header("foo").isEqualTo("bar")).to("mock:result");
            }
        };
    }
}
