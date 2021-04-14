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
package org.apache.camel.zipkin;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.camel.Exchange;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinStateTest {

    private ZipkinState state = new ZipkinState();

    @Test
    public void testZipkinState() {
        TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).parentId(3L).build();
        TraceContextOrSamplingFlags sampling = TraceContextOrSamplingFlags.newBuilder(context).build();
        Tracing tracing = Tracing.newBuilder().build();

        Span span1 = tracing.tracer().nextSpan(sampling);
        state.pushServerSpan(span1);

        Span span2 = tracing.tracer().nextSpan(sampling);
        state.pushServerSpan(span2);

        Exchange exchange1 = new DefaultExchange(new SpringCamelContext());
        exchange1.getIn().setHeader(ZipkinConstants.TRACE_ID, context.traceIdString());
        exchange1.getIn().setHeader(ZipkinConstants.PARENT_SPAN_ID, context.spanIdString());
        exchange1.getIn().setHeader(ZipkinConstants.SPAN_ID, span1.context().spanIdString());

        Exchange exchange2 = new DefaultExchange(new SpringCamelContext());
        exchange2.getIn().setHeader(ZipkinConstants.TRACE_ID, context.traceIdString());
        exchange2.getIn().setHeader(ZipkinConstants.PARENT_SPAN_ID, context.spanIdString());
        exchange2.getIn().setHeader(ZipkinConstants.SPAN_ID, span2.context().spanIdString());

        Span retrived = state.peekServerSpan();
        assertThat(retrived.context().spanId()).isEqualTo(span2.context().spanId());
        assertThat(retrived.context().parentId()).isEqualTo(span2.context().parentId());
        assertThat(retrived.context().traceId()).isEqualTo(span2.context().traceId());

        state.popServerSpan();

        retrived = state.peekServerSpan();
        assertThat(retrived.context().spanId()).isEqualTo(span1.context().spanId());
        assertThat(retrived.context().parentId()).isEqualTo(span1.context().parentId());
        assertThat(retrived.context().traceId()).isEqualTo(span1.context().traceId());

    }

    @Test
    public void testZipkinStateSafeCopy() {
        TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).parentId(3L).build();
        TraceContextOrSamplingFlags sampling = TraceContextOrSamplingFlags.newBuilder(context).build();
        Tracing tracing = Tracing.newBuilder().build();

        Span span1 = tracing.tracer().nextSpan(sampling);
        state.pushServerSpan(span1);

        Span span2 = tracing.tracer().nextSpan(sampling);
        state.pushServerSpan(span2);

        Span span3 = tracing.tracer().nextSpan(sampling);
        ZipkinState state2 = state.safeCopy();
        state2.pushServerSpan(span3);

        //original object intact
        assertThat(state.peekServerSpan().context()).isNotEqualTo(span3.context()).usingRecursiveComparison();

        //new object has the new span
        assertThat(state2.peekServerSpan().context()).isEqualTo(span3.context()).usingRecursiveComparison();

    }

}
