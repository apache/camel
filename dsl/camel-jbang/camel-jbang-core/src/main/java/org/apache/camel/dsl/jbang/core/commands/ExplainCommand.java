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

package org.apache.camel.dsl.jbang.core.commands;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import picocli.CommandLine;

public class ExplainCommand extends CamelCommand {

    @CommandLine.Option(names = {"--url"}, description = "The API URL", defaultValue = "http://localhost:8000/v1/", arity = "0..1", required = true)
    private String url;

    @CommandLine.Option(names = {"--api-key"}, description = "The API key", defaultValue = "no_key", arity = "0..1", required = true)
    private String apiKey;

    @CommandLine.Option(names = {"--model-name"}, description = "The model name to use", arity = "0..1", required = true)
    private String modelName;

    @CommandLine.Option(names = {"--user-prompt"}, description = "The user prompt for the activity", arity = "0..1",
            defaultValue = "Please explain this", required = true)
    private String userPrompt;

    @CommandLine.Option(names = {"--system-prompt"}, description = "An optional system prompt to use",
            defaultValue = "You are a coding assistant specialized in Apache Camel", arity = "0..1")
    private String systemPrompt;

    @CommandLine.Parameters(paramLabel = "what", description = "What to explain")
    private String what;


    public ExplainCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        OpenAiStreamingChatModel chatModel = buildModel(modelName);

        List<ChatMessage> messages;
        if (systemPrompt != null) {
            SystemMessage systemMessage = SystemMessage.systemMessage(systemPrompt);
            messages = List.of(systemMessage,
                    UserMessage.userMessage(userPrompt + ": " + what));
        } else {
            messages = List.of(UserMessage.userMessage(userPrompt + ": " + what));
        }

        CountDownLatch latch = new CountDownLatch(1);

        chatModel.generate(messages, new StreamingResponseHandler<>() {
            @Override
            public void onNext(String s) {
                System.out.print(s);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    StreamingResponseHandler.super.onComplete(response);
                } finally {
                    latch.countDown();
                }
            }
        });


        latch.await(2, TimeUnit.MINUTES);
        return 0;
    }

    public OpenAiStreamingChatModel buildModel(String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(url)
                .apiKey(apiKey)
                .timeout(Duration.ofMinutes(2))
                .maxTokens(Integer.MAX_VALUE)
                .modelName(modelName).build();
    }
}
