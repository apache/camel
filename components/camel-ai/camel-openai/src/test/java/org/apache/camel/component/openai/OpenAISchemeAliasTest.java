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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAISchemeAliasTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .replyWith("Hi from mock")
            .end()
            .build();

    @Test
    void llmAndOpenaiComponentsResolveToOpenAIComponent() {
        assertThat(context.getComponent("llm")).isInstanceOf(OpenAIComponent.class);
        assertThat(context.getComponent("openai")).isInstanceOf(OpenAIComponent.class);
    }

    @Test
    void llmAndOpenaiComponentsAreIndependentInstances() {
        OpenAIComponent llm = context.getComponent("llm", OpenAIComponent.class);
        OpenAIComponent openai = context.getComponent("openai", OpenAIComponent.class);

        assertThat(llm).isNotSameAs(openai);

        llm.setModel("llm-model");
        openai.setModel("openai-model");

        assertThat(llm.getModel()).isEqualTo("llm-model");
        assertThat(openai.getModel()).isEqualTo("openai-model");
    }

    @Test
    void independentComponentConfigurationIsAppliedToEndpoints() throws Exception {
        OpenAIComponent llm = context.getComponent("llm", OpenAIComponent.class);
        OpenAIComponent openai = context.getComponent("openai", OpenAIComponent.class);

        llm.setBaseUrl(openAIMock.getBaseUrl() + "/v1");
        llm.setApiKey("llm-key");
        llm.setModel("llm-model");

        openai.setBaseUrl(openAIMock.getBaseUrl() + "/v1");
        openai.setApiKey("openai-key");
        openai.setModel("openai-model");

        OpenAIEndpoint llmEndpoint = (OpenAIEndpoint) llm.createEndpoint("llm:chat-completion");
        OpenAIEndpoint openaiEndpoint = (OpenAIEndpoint) openai.createEndpoint("openai:chat-completion");

        assertThat(llmEndpoint.getConfiguration().getApiKey()).isEqualTo("llm-key");
        assertThat(openaiEndpoint.getConfiguration().getApiKey()).isEqualTo("openai-key");
        assertThat(llmEndpoint.getConfiguration().getModel()).isEqualTo("llm-model");
        assertThat(openaiEndpoint.getConfiguration().getModel()).isEqualTo("openai-model");
    }

    @ParameterizedTest
    @ValueSource(strings = { "llm", "openai" })
    void chatCompletionWorksForBothSchemes(String scheme) {
        Exchange result = template.request("direct:" + scheme, e -> e.getIn().setBody("hello"));
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Hi from mock");
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID)).isNotNull();
    }

    @Test
    void bothSchemesCreateChatCompletionEndpoints() throws Exception {
        OpenAIEndpoint llmEndpoint
                = (OpenAIEndpoint) context
                        .getEndpoint("llm:chat-completion?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
        OpenAIEndpoint openaiEndpoint
                = (OpenAIEndpoint) context
                        .getEndpoint("openai:chat-completion?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");

        assertThat(llmEndpoint.getOperation()).isEqualTo(OpenAIOperations.chatCompletion);
        assertThat(openaiEndpoint.getOperation()).isEqualTo(OpenAIOperations.chatCompletion);
        // getDefaultName() returns the first scheme in @Component("llm,openai"), not the URI scheme used
        assertThat(llmEndpoint.getComponent().getDefaultName()).isEqualTo("llm");
        assertThat(openaiEndpoint.getComponent().getDefaultName()).isEqualTo("llm");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String base = openAIMock.getBaseUrl() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:llm")
                        .to("llm:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + base);

                from("direct:openai")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + base);
            }
        };
    }
}
