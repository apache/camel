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
package org.apache.camel.component.openai;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIResponsesObservabilityTest extends CamelTestSupport {

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello-responses")
            .replyWith("Hi from responses mock")
            .end()
            .build();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return OpenAIObservabilityTestSupport.registerRecordingTracer(super.createCamelContext());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:responses")
                        .to("openai:responses?model=gpt-4o&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void shouldEmitGenAiSpanFromResponsesProducer() {
        template.sendBody("direct:responses", "hello-responses");

        OpenAIObservabilityTestSupport.RecordingTracer tracer
                = OpenAIObservabilityTestSupport.tracer(context);
        assertThat(tracer.genAiSpans()).hasSize(1);
        Map<String, String> tags = tracer.genAiSpans().get(0).tags();
        assertThat(tags.get(GenAiAttributes.OPERATION_NAME)).isEqualTo("generate_content");
        assertThat(tags.get(GenAiAttributes.SYSTEM)).isEqualTo("openai");
        assertThat(tags.get(GenAiAttributes.REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(tags.get(GenAiAttributes.CAMEL_COMPONENT)).isEqualTo("openai");
    }
}
