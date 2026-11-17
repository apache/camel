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
package org.apache.camel.component.langchain4j.agent;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A custom {@link Agent} does not build its AI service through {@code AbstractAgent}, so its model calls cannot be
 * observed one by one: at level detailed the run is recorded as one model call, with its token usage.
 */
class LangChain4jAgentCustomAgentObservabilityTest extends CamelTestSupport {

    private RecordingGenAiTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        tracer = new RecordingGenAiTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, "detailed");
        return context;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("customAgent", new CustomAgent());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:custom").to("langchain4j-agent:custom?agent=#customAgent");
            }
        };
    }

    @Test
    void shouldRecordTheRunAsOneModelCall() {
        assertThat(template.requestBody("direct:custom", "Hello", String.class)).isEqualTo("Hi");

        assertThat(tracer.genAiSpans()).hasSize(1);
        assertThat(tracer.genAiSpans().get(0).name()).isEqualTo("invoke_agent custom");
        assertThat(tracer.genAiSpans().get(0).tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "generate_content")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 7L)
                .containsEntry(GenAiAttributes.OUTPUT_TOKENS, 2L);
    }

    private static final class CustomAgent implements Agent {
        @Override
        public Result<String> chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
            return Result.<String> builder()
                    .content("Hi")
                    .tokenUsage(new TokenUsage(7, 2, 9))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }
}
