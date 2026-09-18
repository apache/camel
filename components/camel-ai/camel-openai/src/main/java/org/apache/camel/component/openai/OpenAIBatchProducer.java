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

import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI producer for the {@code batch} operation, which uploads the requests of a batch as a JSONL file and creates
 * the batch that runs them.
 * <p>
 * The body is either the JSONL itself, as a {@link File}, {@link Path}, {@link WrappedFile}, {@link InputStream},
 * {@code byte[]} or String, or a {@link Map} keyed by {@code custom_id}. A map value that is itself a map is used as
 * the request body as it is, while a String value is turned into a request built from the endpoint options, so a route
 * that only has prompts does not have to assemble the API payload itself.
 */
public class OpenAIBatchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIBatchProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public OpenAIBatchProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();
        // a batch request runs once and returns a file, so options needing several round-trips cannot be honoured
        // and are refused rather than silently dropped
        rejectUnsupportedOptions(config);

        String endpoint = OpenAIBatchSupport.resolveEndpoint(in, config);

        Path input = null;
        boolean temporary = false;
        try {
            Object body = in.getBody();
            if (body instanceof WrappedFile<?> wrappedFile && wrappedFile.getFile() instanceof File file) {
                body = file;
            }
            if (body instanceof File file) {
                input = file.toPath();
            } else if (body instanceof Path path) {
                input = path;
            } else {
                input = writeTemporaryInput(exchange, body, endpoint, in, config);
                temporary = true;
            }

            String inputFileId = upload(exchange, input);
            in.setHeader(OpenAIConstants.BATCH_INPUT_FILE_ID, inputFileId);

            Batch batch = create(exchange, inputFileId, endpoint, in, config);
            if (config.isStoreFullResponse()) {
                exchange.setProperty(OpenAIConstants.BATCH_RESPONSE, batch);
            }
            OpenAIBatchSupport.setBatchHeaders(exchange.getMessage(), batch);
        } finally {
            if (temporary && input != null) {
                Files.deleteIfExists(input);
            }
        }
    }

    private String upload(Exchange exchange, Path input) {
        try {
            return getEndpoint().getClient().files().create(FileCreateParams.builder()
                    .file(input)
                    .purpose(FilePurpose.BATCH)
                    .build()).id();
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }
    }

    private Batch create(
            Exchange exchange, String inputFileId, String endpoint, Message in, OpenAIConfiguration config) {
        BatchCreateParams.Builder params = BatchCreateParams.builder()
                .inputFileId(inputFileId)
                .endpoint(BatchCreateParams.Endpoint.of(endpoint))
                // the API supports a single completion window
                .completionWindow(BatchCreateParams.CompletionWindow._24H);

        Map<String, Object> metadata = metadata(in, config);
        if (ObjectHelper.isNotEmpty(metadata)) {
            BatchCreateParams.Metadata.Builder builder = BatchCreateParams.Metadata.builder();
            metadata.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(String.valueOf(value))));
            params.metadata(builder.build());
        }

        try {
            return getEndpoint().getClient().batches().create(params.build());
        } catch (RuntimeException e) {
            // the input file outlives a failed create, so it is removed instead of being left behind with its data
            try {
                getEndpoint().getClient().files().delete(inputFileId);
            } catch (RuntimeException deleteFailure) {
                e.addSuppressed(deleteFailure);
            }
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Message in, OpenAIConfiguration config) {
        Map<String, Object> header = in.getHeader(OpenAIConstants.BATCH_METADATA, Map.class);
        return header != null ? header : config.getBatchMetadata();
    }

    /**
     * Writes the request lines to a temporary file, so the upload can be retried by the SDK, which a one-shot stream
     * would not allow.
     */
    private Path writeTemporaryInput(
            Exchange exchange, Object body, String endpoint, Message in, OpenAIConfiguration config)
            throws Exception {
        Path file = Files.createTempFile("camel-openai-batch-", ".jsonl");
        if (body instanceof Map) {
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (String line : requestLines((Map<?, ?>) body, endpoint, in, config)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            LOG.debug("Wrote batch input file {} from a map body", file);
            return file;
        }

        InputStream stream = body instanceof InputStream is ? is : in.getBody(InputStream.class);
        if (stream == null) {
            throw new IllegalArgumentException(
                    "Unsupported body type for the batch operation: "
                                               + (body != null ? body.getClass().getName() : "null")
                                               + ". Supported: File, Path, InputStream, byte[], String, or a Map "
                                               + "keyed by custom_id");
        }
        try (InputStream source = stream) {
            Files.copy(source, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    /**
     * Turns a map keyed by {@code custom_id} into the request lines of the input file, writing the envelope every line
     * needs around the request body.
     */
    private List<String> requestLines(Map<?, ?> body, String endpoint, Message in, OpenAIConfiguration config)
            throws Exception {
        List<String> lines = new ArrayList<>(body.size());
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("The batch input map must not contain null keys or values");
            }
            String customId = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            Object requestBody;
            if (value instanceof Map || value instanceof JsonNode) {
                requestBody = value;
            } else if (value instanceof String text) {
                requestBody = requestBody(endpoint, text, in, config);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported batch input value for custom_id " + customId + ": "
                                                   + value.getClass().getName()
                                                   + ". Supported: String, Map or JsonNode");
            }

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("custom_id", customId);
            line.put("method", "POST");
            line.put("url", endpoint);
            line.put("body", requestBody);
            lines.add(OBJECT_MAPPER.writeValueAsString(line));
        }
        return lines;
    }

    /**
     * Builds the request body of one line from the endpoint options, for the endpoints whose request is a single prompt
     * or input text.
     */
    private Map<String, Object> requestBody(String endpoint, String text, Message in, OpenAIConfiguration config)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        switch (endpoint) {
            case "/v1/chat/completions" -> {
                request.put("model", requiredModel(in, config));
                List<Map<String, Object>> messages = new ArrayList<>();
                addMessage(messages, "system", in.getHeader(OpenAIConstants.SYSTEM_MESSAGE,
                        config.getSystemMessage(), String.class));
                addMessage(messages, "developer", config.getDeveloperMessage());
                addMessage(messages, "user", text);
                request.put("messages", messages);
                addChatParameters(request, in, config);
            }
            case "/v1/responses" -> {
                request.put("model", requiredModel(in, config));
                String instructions = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE,
                        config.getSystemMessage(), String.class);
                if (ObjectHelper.isNotEmpty(instructions)) {
                    request.put("instructions", instructions);
                }
                request.put("input", text);
            }
            case "/v1/embeddings" -> {
                if (ObjectHelper.isEmpty(config.getEmbeddingModel())) {
                    throw new IllegalArgumentException(
                            "The embeddingModel option must be set to build the requests of an embeddings batch");
                }
                request.put("model", config.getEmbeddingModel());
                request.put("input", text);
                if (config.getDimensions() != null) {
                    request.put("dimensions", config.getDimensions());
                }
            }
            case "/v1/moderations" -> {
                request.put("model", config.getModerationModel());
                request.put("input", text);
            }
            default -> throw new IllegalArgumentException(
                    "A String value builds the request of a /v1/chat/completions, /v1/responses, /v1/embeddings or "
                                                          + "/v1/moderations batch. For " + endpoint
                                                          + " pass the request body as a Map instead");
        }
        return request;
    }

    private void addChatParameters(Map<String, Object> request, Message in, OpenAIConfiguration config)
            throws Exception {
        Double temperature = in.getHeader(OpenAIConstants.TEMPERATURE, config.getTemperature(), Double.class);
        if (temperature != null) {
            request.put("temperature", temperature);
        }
        Double topP = in.getHeader(OpenAIConstants.TOP_P, config.getTopP(), Double.class);
        if (topP != null) {
            request.put("top_p", topP);
        }
        Integer maxTokens = in.getHeader(OpenAIConstants.MAX_TOKENS, config.getMaxTokens(), Integer.class);
        if (maxTokens != null) {
            request.put("max_tokens", maxTokens);
        }
        String jsonSchema = in.getHeader(OpenAIConstants.JSON_SCHEMA, config.getJsonSchema(), String.class);
        if (ObjectHelper.isNotEmpty(jsonSchema)) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", "response");
            schema.put("strict", true);
            schema.put("schema", OBJECT_MAPPER.readValue(jsonSchema, Map.class));
            request.put("response_format", Map.of("type", "json_schema", "json_schema", schema));
        }
    }

    private String requiredModel(Message in, OpenAIConfiguration config) {
        String model = in.getHeader(OpenAIConstants.MODEL, config.getModel(), String.class);
        if (ObjectHelper.isEmpty(model)) {
            throw new IllegalArgumentException("The model option must be set to build the requests of the batch");
        }
        return model;
    }

    private void addMessage(List<Map<String, Object>> messages, String role, String content) {
        if (ObjectHelper.isNotEmpty(content)) {
            messages.add(Map.of("role", role, "content", content));
        }
    }

    private void rejectUnsupportedOptions(OpenAIConfiguration config) {
        if (config.isStreaming()) {
            throw new IllegalArgumentException("The batch operation cannot stream responses; set streaming=false");
        }
        if (config.isConversationMemory()) {
            throw new IllegalArgumentException(
                    "The batch operation runs each request once, so conversationMemory is not supported");
        }
        if (ObjectHelper.isNotEmpty(config.getMcpServer()) || ObjectHelper.isNotEmpty(config.getTags())) {
            throw new IllegalArgumentException(
                    "The batch operation cannot run a tool loop, so mcpServer and tags are not supported");
        }
    }
}
