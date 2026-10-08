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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A node label too long for the trace is cut with an ellipsis, so a tool showing it does not take it for the whole
 * label.
 */
class BacklogTracerLongLabelTest extends ContextTestSupport {

    @Test
    void aLongLabelEndsWithAnEllipsis() {
        BacklogTracer tracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        tracer.setEnabled(true);

        template.sendBody("direct:start", "Hello");

        // a dump takes the messages out of the tracer
        List<BacklogTracerEventMessage> events = tracer.dumpAllTracedMessages();
        BacklogTracerEventMessage setBody = events.stream()
                .filter(e -> "setBody".equals(e.getToNodeShortName())).findFirst().orElseThrow();
        assertThat(setBody.getToNodeLabel()).hasSize(53).endsWith("...");
        BacklogTracerEventMessage log = events.stream()
                .filter(e -> "log".equals(e.getToNodeShortName())).findFirst().orElseThrow();
        assertThat(log.getToNodeLabel()).doesNotEndWith("...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setBacklogTracing(true);

                from("direct:start")
                        .setBody(simple("${body} and a rather long text that makes the label longer than fifty"))
                        .log("short");
            }
        };
    }
}
