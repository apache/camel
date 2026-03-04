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
package org.apache.camel.component.huggingface.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the CHAT task, handling conversational LLM inference with automatic history management.
 *
 * <p>
 * This predictor manages multi-turn chat conversations using Hugging Face's text-generation pipeline for instruct-tuned
 * models. It supports automatic history retention in-memory (keyed by a header for multi-user support), system prompts,
 * and configurable roles. The predictor is designed to be model-agnostic, allowing seamless swapping of compatible
 * chat-tuned LLMs via the modelId configuration.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The user's message or prompt for the current turn.</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The LLM's generated response (extracted from the last "assistant" message).</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code CamelChatMemoryId} (optional, default "default"): String key for conversation history (e.g., user session
 * ID).</li>
 * <li>{@code CamelChatClearHistory} (optional): Boolean to clear the history for the current memory ID.</li>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The same generated response string (for convenience).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "mistralai/Mistral-7B-Instruct-v0.2" or
 * "microsoft/Phi-3-mini-4k-instruct". Use chat-tuned models for best results.</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto" — "cpu" for no GPU).</li>
 * <li>{@code maxTokens}: Optional int, max new tokens in response (default 512).</li>
 * <li>{@code temperature}: Optional float, sampling temperature (default 1.0f; set to 0 for deterministic).</li>
 * <li>{@code authToken}: Optional String, HF API token for gated models.</li>
 * <li>{@code userRole}: Optional String, role for user messages (default "user").</li>
 * <li>{@code systemPrompt}: Optional String, initial system message (appended if history is empty).</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: JSON string of a list of dicts (messages): [{ "role": "system/user/assistant", "content": "text"
 * }]. Models must support chat templates or multi-turn formats.</li>
 * <li><b>Output</b>: JSON from HF pipeline: [{"generated_text": [full history list of dicts]}]. The predictor extracts
 * the last assistant content.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use instruct-tuned models with compatible chat formats (e.g., Llama-3,
 * Mistral-Instruct, Phi-3).
 * </p>
 */
public class ChatPredictor extends AbstractTaskPredictor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory chat history (key = memoryId, value = JSON string of list of dicts)
    private final Map<String, String> chatHistories = new ConcurrentHashMap<>();

    public ChatPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getPythonScript() {
        String doSample = config.getTemperature() > 0 ? "True" : "False";
        float temperature = config.getTemperature() > 0 ? config.getTemperature() : 1.0f;
        String tokenClause = config.getAuthToken() != null ? ", token='" + config.getAuthToken() + "'" : "";
        return loadPythonScript("chat.py", config.getModelId(), config.getRevision(), config.getDevice(), tokenClause,
                config.getMaxTokens(),
                doSample, temperature);
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        String memoryIdHeader = config.getMemoryIdHeader();
        String memoryId = exchange.getIn().getHeader(memoryIdHeader, "default", String.class);

        // Clear if requested
        Boolean clear = exchange.getIn().getHeader("CamelChatClearHistory", Boolean.class);
        if (Boolean.TRUE.equals(clear)) {
            chatHistories.remove(memoryId);
            LOG.debug("Cleared chat history for ID: {}", memoryId);
        }

        String historyJson = chatHistories.get(memoryId);
        List<Map<String, String>> history;
        if (historyJson == null || historyJson.isEmpty()) {
            history = new ArrayList<>();
        } else {
            history = objectMapper.readValue(historyJson, new TypeReference<List<Map<String, String>>>() {
            });
        }

        // Prepend system prompt if history is empty and systemPrompt is set
        String systemPrompt = config.getSystemPrompt();
        if (history.isEmpty() && systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            history.add(systemMsg);
        }

        String userMessage = exchange.getIn().getBody(String.class);
        if (userMessage != null && !userMessage.isEmpty()) {
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", config.getUserRole());
            userMsg.put("content", userMessage);
            history.add(userMsg);
        }

        // Set full history as JSON for Python
        String jsonInput = objectMapper.writeValueAsString(history);

        Input input = new Input();
        input.add("data", jsonInput.getBytes("UTF-8"));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String resultJson = output.getAsString("data");

        if (resultJson.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference failed: " + resultJson);
        }

        // Parse JSON and extract generated text
        JsonNode node = objectMapper.readTree(resultJson);
        String generatedText = null;

        if (node.isArray() && !node.isEmpty()) {
            JsonNode first = node.get(0);
            if (first.has("generated_text")) {
                JsonNode genText = first.get("generated_text");
                if (genText.isTextual()) {
                    generatedText = genText.asText();
                } else if (genText.isArray()) {
                    JsonNode lastMessage = genText.get(genText.size() - 1);
                    String role = lastMessage.get("role").asText();
                    if (role.equals("assistant") || role.equals("AI")) {
                        generatedText = lastMessage.get("content").asText();
                    }
                }
            }
        }

        if (generatedText == null) {
            generatedText = resultJson;  // Fallback
        }

        exchange.getMessage().setBody(generatedText);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, generatedText);

        // Update history with full returned history
        String memoryIdHeader = config.getMemoryIdHeader();
        String memoryId = exchange.getIn().getHeader(memoryIdHeader, "default", String.class);
        if (node.isArray() && !node.isEmpty()) {
            JsonNode first = node.get(0);
            if (first.has("generated_text") && first.get("generated_text").isArray()) {
                String fullHistoryJson = first.get("generated_text").toString();
                chatHistories.put(memoryId, fullHistoryJson);
                LOG.debug("Updated chat history for ID: {}", memoryId);
            }
        }
    }
}
