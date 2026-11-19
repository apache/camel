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
package org.apache.camel.component.langchain4j.service;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.component.langchain4j.service.support.ModelHelper;
import org.apache.camel.component.langchain4j.service.support.ModelTurn;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conformance contract, run in CI against the in-repo OpenAI mock server.
 * <p>
 * The client side is real: a real {@code OpenAiChatModel} serialises the request LangChain4j built, speaks the
 * tool-call protocol over HTTP and maps the server's error back onto a LangChain4j exception. Only the server is a
 * mock, so the whole suite runs in milliseconds and needs no container, which is what the integration test cannot offer
 * on GitHub Actions.
 * <p>
 * The mock matches an expectation on the last user message, by equality. Declaring a turn's prompt therefore asserts
 * it: a prompt the component failed to bind, or that LangChain4j rendered differently, matches nothing and the request
 * comes back as an error naming what actually arrived.
 * <p>
 * Expectations are registered from the test body rather than from a field initialiser, because each scenario needs its
 * own. The mock holds its expectation list live and the server reads it per request, so adding to it after the server
 * started is enough.
 */
class LangChain4jServiceConformanceTest extends LangChain4jServiceConformanceTestSupport {

    @RegisterExtension
    OpenAIMock openAIMock = new OpenAIMock();

    @Override
    protected ChatModel modelAnswering(ModelTurn... turns) {
        for (ModelTurn turn : turns) {
            openAIMock.builder()
                    .when(turn.prompt())
                    .replyWith(turn.reply())
                    .end();
        }
        return ModelHelper.mockedChatModel(openAIMock.getBaseUrl());
    }

    @Override
    protected ChatModel modelCallingTool(
            String prompt, String toolName, String argumentName, String argumentValue, String finalReply) {
        openAIMock.builder()
                .when(prompt)
                .invokeTool(toolName)
                .withParam(argumentName, argumentValue)
                .replyWith(finalReply)
                .end();
        return ModelHelper.mockedChatModel(openAIMock.getBaseUrl());
    }

    @Override
    protected ChatModel failingModel(String prompt) {
        // The provider maps the 404 onto ModelNotFoundException itself, which is the mapping worth covering
        openAIMock.builder()
                .when(prompt)
                .replyWithError(404, "model_not_found", "The model 'gpt-4o-mini' does not exist")
                .end();
        return ModelHelper.mockedChatModel(openAIMock.getBaseUrl());
    }

    @Override
    protected void assertAnswer(String body, String expectedReply) {
        assertThat(body)
                .as("a mocked model answers exactly what it was told to, so the body must carry it unchanged: "
                    + "anything else means the component did not return the AI Service's own result")
                .isEqualTo(expectedReply);
    }
}
