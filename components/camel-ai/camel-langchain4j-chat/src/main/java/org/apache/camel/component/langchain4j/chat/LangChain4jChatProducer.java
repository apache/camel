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
package org.apache.camel.component.langchain4j.chat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.langchain4j.chat.tool.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.chat.tool.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChat.Headers.AUGMENTED_DATA;

public class LangChain4jChatProducer extends DefaultProducer {

    private final LangChain4jChatEndpoint endpoint;

    private ChatLanguageModel chatLanguageModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LangChain4jChatProducer(LangChain4jChatEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        var operation = this.endpoint.getConfiguration().getChatOperation();

        if (LangChain4jChatOperations.CHAT_SINGLE_MESSAGE.equals(operation)) {
            processSingleMessage(exchange);
        } else if (LangChain4jChatOperations.CHAT_SINGLE_MESSAGE_WITH_PROMPT.equals(operation)) {
            processSingleMessageWithPrompt(exchange);
        } else if (LangChain4jChatOperations.CHAT_MULTIPLE_MESSAGES.equals(operation)) {
            processMultipleMessages(exchange);
        }
    }

    @SuppressWarnings("unchecked")
    private void processSingleMessageWithPrompt(Exchange exchange) throws NoSuchHeaderException, InvalidPayloadException {
        final String promptTemplate = exchange.getIn().getHeader(LangChain4jChat.Headers.PROMPT_TEMPLATE, String.class);
        if (promptTemplate == null) {
            throw new NoSuchHeaderException(
                    "The promptTemplate is a required header", exchange, LangChain4jChat.Headers.PROMPT_TEMPLATE);
        }

        Map<String, Object> variables = (Map<String, Object>) exchange.getIn().getMandatoryBody(Map.class);

        var response = sendWithPromptTemplate(promptTemplate, variables, exchange);

        populateResponse(response, exchange);
    }

    private void processSingleMessage(Exchange exchange) throws InvalidPayloadException {
        // Retrieve the mandatory body from the exchange
        final var message = exchange.getIn().getMandatoryBody();

        // Use pattern matching with instanceof to streamline type checks and assignments
        ChatMessage userMessage = (message instanceof String) ? new UserMessage((String) message) : (ChatMessage) message;

        populateResponse(sendChatMessage(userMessage, exchange), exchange);

    }

    @SuppressWarnings("unchecked")
    private void processMultipleMessages(Exchange exchange) throws InvalidPayloadException {
        List<ChatMessage> messages = exchange.getIn().getMandatoryBody(List.class);
        populateResponse(sendListChatMessage(messages, exchange), exchange);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatLanguageModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatLanguageModel, "chatLanguageModel");
    }

    private void populateResponse(String response, Exchange exchange) {
        exchange.getMessage().setBody(response);
    }

    /**
     * Send a ChatMessage
     *
     * @param  chatMessage
     * @return
     */
    private String sendChatMessage(ChatMessage chatMessage, Exchange exchange) {
        var augmentedChatMessage = addAugmentedData(chatMessage, exchange);

        Response<AiMessage> response = this.chatLanguageModel.generate(augmentedChatMessage);
        return extractAiResponse(response);
    }

    /**
     * Augment the message for RAG if the header is specified
     *
     * @param chatMessage
     * @param exchange
     */
    private ChatMessage addAugmentedData(ChatMessage chatMessage, Exchange exchange) {
        // check if there's any augmented data
        List<Content> augmentedData = exchange.getIn().getHeader(AUGMENTED_DATA, List.class);

        // inject data
        if (augmentedData != null && augmentedData.size() != 0) {
            ContentInjector contentInjector = new DefaultContentInjector();
            // inject with new List of Contents
            return contentInjector.inject(augmentedData, chatMessage);
        } else {
            return chatMessage;
        }
    }

    /**
     * Send a ChatMessage
     *
     * @param  chatMessages
     * @return
     */
    private String sendListChatMessage(List<ChatMessage> chatMessages, Exchange exchange) {
        LangChain4jChatEndpoint langChain4jChatEndpoint = (LangChain4jChatEndpoint) getEndpoint();

        Response<AiMessage> response;

        // Check if the last message is a UserMessage and if there's a need to augment the message for RAG
        int size = chatMessages.size();
        if (size != 0) {
            int lastIndex = size - 1;
            ChatMessage lastUserMessage = chatMessages.get(lastIndex);
            if (lastUserMessage instanceof UserMessage) {
                chatMessages.set(lastIndex, addAugmentedData(lastUserMessage, exchange));
            }

        }

        final Map<String, Set<CamelToolSpecification>> tools = CamelToolExecutorCache.getInstance().getTools();
        if (tools.containsKey(langChain4jChatEndpoint.getChatId())) {
            final Set<CamelToolSpecification> camelToolSpecificationSet = tools
                    .get(langChain4jChatEndpoint.getChatId());

            final List<ToolSpecification> toolSpecifications = camelToolSpecificationSet.stream()
                    .map(camelToolSpecification -> camelToolSpecification.getToolSpecification())
                    .collect(Collectors.toList());

            response = this.chatLanguageModel.generate(chatMessages, toolSpecifications);
        } else {
            response = this.chatLanguageModel.generate(chatMessages);
        }

        if (response.content().hasToolExecutionRequests()) {
            chatMessages.add(response.content());

            for (ToolExecutionRequest toolExecutionRequest : response.content().toolExecutionRequests()) {
                String toolName = toolExecutionRequest.name();
                CamelToolSpecification camelToolSpecification = CamelToolExecutorCache.getInstance().getTools()
                        .get(langChain4jChatEndpoint.getChatId()).stream()
                        .filter(cts -> cts.getToolSpecification().name().equals(toolName))
                        .findFirst().orElseThrow(() -> new RuntimeException("Tool " + toolName + " not found"));
                try {
                    // Map Json to Header
                    JsonNode jsonNode = objectMapper.readValue(toolExecutionRequest.arguments(), JsonNode.class);

                    jsonNode.fieldNames()
                            .forEachRemaining(name -> exchange.getMessage().setHeader(name, jsonNode.get(name)));

                    // Execute the consumer route
                    camelToolSpecification.getConsumer().getProcessor().process(exchange);
                } catch (Exception e) {
                    // How to handle this exception?
                    exchange.setException(e);
                }

                chatMessages.add(new ToolExecutionResultMessage(
                        toolExecutionRequest.id(),
                        toolExecutionRequest.name(),
                        exchange.getIn().getBody(String.class)));
            }

            response = this.chatLanguageModel.generate(chatMessages);
        }

        return extractAiResponse(response);
    }

    private String extractAiResponse(Response<AiMessage> response) {
        AiMessage message = response.content();
        return message == null ? null : message.text();
    }

    public String sendWithPromptTemplate(String promptTemplate, Map<String, Object> variables, Exchange exchange) {
        PromptTemplate template = PromptTemplate.from(promptTemplate);
        Prompt prompt = template.apply(variables);
        return this.sendChatMessage(new UserMessage(prompt.text()), exchange);
    }

}
