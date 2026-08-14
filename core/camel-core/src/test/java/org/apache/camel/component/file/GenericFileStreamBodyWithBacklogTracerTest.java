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
package org.apache.camel.component.file;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a GenericFile with a non-resettable InputStream body is preserved when the backlog tracer is active.
 */
class GenericFileStreamBodyWithBacklogTracerTest extends ContextTestSupport {

    @Test
    void testGenericFileStreamBodyPreservedWithBacklogTracer() throws Exception {
        assertThat(context.isBacklogTracingStandby()).as("BacklogTracingStandby").isTrue();
        assertThat(context.isMessageHistory()).as("MessageHistory").isTrue();

        BacklogTracer tracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        assertThat(tracer).as("BacklogTracer").isNotNull();
        assertThat(tracer.isStandby()).as("BacklogTracer standby").isTrue();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        GenericFile<Object> file = new GenericFile<>();
        file.setFileName("test.txt");
        file.setBody(nonResettableStream("Hello World Body"));
        template.sendBody("direct:start", file);

        assertMockEndpointsSatisfied();

        String body = getMockEndpoint("mock:result").getReceivedExchanges().get(0)
                .getMessage().getBody(String.class);
        assertThat(body).isEqualTo("Hello World Body");
    }

    private static InputStream nonResettableStream(String content) {
        return new FilterInputStream(new ByteArrayInputStream(content.getBytes())) {
            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public synchronized void mark(int readlimit) {
            }

            @Override
            public synchronized void reset() {
                throw new UnsupportedOperationException("reset not supported");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);
                context.setBacklogTracingStandby(true);
                context.setMessageHistory(true);

                from("direct:start").routeId("myRoute").streamCaching("true")
                        .to("mock:result").id("result");
            }
        };
    }
}
