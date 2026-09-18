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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.core.MultipartField;
import com.openai.core.ObjectMappers;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.responses.ResponseCreateParams;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.component.openai.OpenAIResponsesInputBuilder.InputSpec;
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
 * that only has prompts does not have to assemble the API payload itself. Such a request is built with the same SDK
 * parameters as the synchronous operation of the endpoint, so the options and headers of that operation apply.
 */
public class OpenAIBatchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIBatchProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INPUT_FILE_SUFFIX = ".jsonl";

    private Class<?> outputClassResolved;

    public OpenAIBatchProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String outputClass = getEndpoint().getConfiguration().getOutputClass();
        if (ObjectHelper.isNotEmpty(outputClass)) {
            outputClassResolved = getEndpoint().getCamelContext().getClassResolver().resolveMandatoryClass(outputClass);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();
        // the options a batch cannot honour are refused when the endpoint starts; the header can still ask for them
        if (Boolean.TRUE.equals(in.getHeader(OpenAIConstants.STREAMING, Boolean.class))) {
            throw new IllegalArgumentException(
                    "The batch operation cannot stream responses; remove the " + OpenAIConstants.STREAMING + " header");
        }

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
                input = writeTemporaryInput(body, endpoint, in, config);
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

    /**
     * Uploads the input file under a {@code .jsonl} name, the only extension the Files API accepts for a batch input,
     * whatever the file is called on disk.
     */
    private String upload(Exchange exchange, Path input) throws IOException {
        String filename = input.getFileName().toString();
        if (!filename.endsWith(INPUT_FILE_SUFFIX)) {
            filename = filename + INPUT_FILE_SUFFIX;
        }
        try (InputStream stream = Files.newInputStream(input)) {
            return getEndpoint().getClient().files().create(FileCreateParams.builder()
                    .file(MultipartField.<InputStream> builder().value(stream).filename(filename).build())
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
     * Writes the body to a temporary file, so every body type is uploaded the same way. The body is validated and the
     * request lines are built before the file is created, and the file is removed if writing it fails, so a rejected
     * exchange leaves nothing behind.
     */
    private Path writeTemporaryInput(Object body, String endpoint, Message in, OpenAIConfiguration config)
            throws Exception {
        List<String> lines = null;
        InputStream stream = null;
        if (body instanceof Map) {
            lines = requestLines((Map<?, ?>) body, endpoint, in, config);
        } else {
            stream = body instanceof InputStream is ? is : in.getBody(InputStream.class);
            if (stream == null) {
                throw new IllegalArgumentException(
                        "Unsupported body type for the batch operation: "
                                                   + (body != null ? body.getClass().getName() : "null")
                                                   + ". Supported: File, Path, InputStream, byte[], String, or a Map "
                                                   + "keyed by custom_id");
            }
        }

        Path file = Files.createTempFile("camel-openai-batch-", INPUT_FILE_SUFFIX);
        try {
            if (lines != null) {
                try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                LOG.debug("Wrote batch input file {} from a map body", file);
            } else {
                try (InputStream source = stream) {
                    Files.copy(source, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(file);
            throw e;
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
     * or input text. The body is built with the SDK parameters of the synchronous operation and serialized with the SDK
     * mapper, so a line carries exactly what that operation would send.
     */
    private JsonNode requestBody(String endpoint, String text, Message in, OpenAIConfiguration config)
            throws Exception {
        Object body = switch (endpoint) {
            case "/v1/chat/completions" -> chatCompletion(text, in, config);
            case "/v1/responses" -> responses(text, in, config);
            case "/v1/embeddings" -> embeddings(text, in, config);
            case "/v1/moderations" -> moderation(text, in, config);
            default -> throw new IllegalArgumentException(
                    "A String value builds the request of a /v1/chat/completions, /v1/responses, /v1/embeddings or "
                                                          + "/v1/moderations batch. For " + endpoint
                                                          + " pass the request body as a Map instead");
        };
        return ObjectMappers.jsonMapper().valueToTree(body);
    }

    private ChatCompletionCreateParams.Body chatCompletion(String text, Message in, OpenAIConfiguration config)
            throws Exception {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(requiredModel(in, config));

        String systemMessage = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE, config.getSystemMessage(), String.class);
        if (ObjectHelper.isNotEmpty(systemMessage)) {
            builder.addSystemMessage(systemMessage);
        }
        String developerMessage
                = in.getHeader(OpenAIConstants.DEVELOPER_MESSAGE, config.getDeveloperMessage(), String.class);
        if (ObjectHelper.isNotEmpty(developerMessage)) {
            builder.addDeveloperMessage(developerMessage);
        }
        builder.addUserMessage(text);

        Double temperature = in.getHeader(OpenAIConstants.TEMPERATURE, config.getTemperature(), Double.class);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        Double topP = in.getHeader(OpenAIConstants.TOP_P, config.getTopP(), Double.class);
        if (topP != null) {
            builder.topP(topP);
        }
        Integer maxTokens = in.getHeader(OpenAIConstants.MAX_TOKENS, config.getMaxTokens(), Integer.class);
        if (maxTokens != null) {
            builder.maxCompletionTokens(maxTokens.longValue());
        }
        additionalBodyProperties(config).forEach(builder::putAdditionalBodyProperty);

        Class<?> outputClass = resolveOutputClass(in);
        if (outputClass != null) {
            return builder.responseFormat(outputClass).build().rawParams()._body();
        }
        String jsonSchema = in.getHeader(OpenAIConstants.JSON_SCHEMA, config.getJsonSchema(), String.class);
        if (ObjectHelper.isNotEmpty(jsonSchema)) {
            builder.responseFormat(ResponseFormatJsonSchema.builder()
                    .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                            .name("camel_schema")
                            .schema(schema(jsonSchema))
                            .build())
                    .build());
        }
        return builder.build()._body();
    }

    private ResponseCreateParams.Body responses(String text, Message in, OpenAIConfiguration config)
            throws Exception {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(requiredModel(in, config));

        String instructions = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE, config.getSystemMessage(), String.class);
        if (ObjectHelper.isNotEmpty(instructions)) {
            builder.instructions(instructions);
        }
        InputSpec input = InputSpec.plainText(text);
        String developerMessage
                = in.getHeader(OpenAIConstants.DEVELOPER_MESSAGE, config.getDeveloperMessage(), String.class);
        if (ObjectHelper.isNotEmpty(developerMessage)) {
            input = input.withDeveloperMessage(developerMessage);
        }
        if (input.isPlainText()) {
            builder.input(text);
        } else {
            builder.inputOfResponse(input.items());
        }

        Double temperature = in.getHeader(OpenAIConstants.TEMPERATURE, config.getTemperature(), Double.class);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        Double topP = in.getHeader(OpenAIConstants.TOP_P, config.getTopP(), Double.class);
        if (topP != null) {
            builder.topP(topP);
        }
        Integer maxTokens = in.getHeader(OpenAIConstants.MAX_TOKENS, config.getMaxTokens(), Integer.class);
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens.longValue());
        }
        OpenAIResponsesSupport.applyAdditionalBodyProperties(builder, config.getAdditionalBodyProperty());

        Class<?> outputClass = resolveOutputClass(in);
        if (outputClass != null) {
            return builder.text(outputClass).build().rawParams()._body();
        }
        String jsonSchema = in.getHeader(OpenAIConstants.JSON_SCHEMA, config.getJsonSchema(), String.class);
        if (ObjectHelper.isNotEmpty(jsonSchema)) {
            OpenAIResponsesSupport.applyJsonSchemaTextFormat(builder, jsonSchema);
        }
        return builder.build()._body();
    }

    private EmbeddingCreateParams.Body embeddings(String text, Message in, OpenAIConfiguration config) {
        String model = in.getHeader(OpenAIConstants.EMBEDDING_MODEL, config.getEmbeddingModel(), String.class);
        if (ObjectHelper.isEmpty(model)) {
            throw new IllegalArgumentException(
                    "The embeddingModel option must be set to build the requests of an embeddings batch");
        }
        EmbeddingCreateParams.Builder builder = EmbeddingCreateParams.builder()
                .model(model)
                .input(text);
        Integer dimensions = in.getHeader(OpenAIConstants.EMBEDDING_DIMENSIONS, config.getDimensions(), Integer.class);
        if (dimensions != null) {
            builder.dimensions(dimensions.longValue());
        }
        return builder.build()._body();
    }

    private ModerationCreateParams.Body moderation(String text, Message in, OpenAIConfiguration config) {
        ModerationCreateParams.Builder builder = ModerationCreateParams.builder().input(text);
        String model = in.getHeader(OpenAIConstants.MODERATION_MODEL, config.getModerationModel(), String.class);
        if (ObjectHelper.isNotEmpty(model)) {
            builder.model(model);
        }
        return builder.build()._body();
    }

    private String requiredModel(Message in, OpenAIConfiguration config) {
        String model = in.getHeader(OpenAIConstants.MODEL, config.getModel(), String.class);
        if (ObjectHelper.isEmpty(model)) {
            throw new IllegalArgumentException("The model option must be set to build the requests of the batch");
        }
        return model;
    }

    private Class<?> resolveOutputClass(Message in) throws ClassNotFoundException {
        String header = in.getHeader(OpenAIConstants.OUTPUT_CLASS, String.class);
        if (ObjectHelper.isNotEmpty(header)) {
            return getEndpoint().getCamelContext().getClassResolver().resolveMandatoryClass(header);
        }
        return outputClassResolved;
    }

    /**
     * The additional body properties as the chat completion operation sends them: a String value holding JSON is sent
     * as that JSON, any other String as a literal.
     */
    private static Map<String, JsonValue> additionalBodyProperties(OpenAIConfiguration config) {
        Map<String, JsonValue> properties = new LinkedHashMap<>();
        Map<String, Object> additional = config.getAdditionalBodyProperty();
        if (additional == null) {
            return properties;
        }
        for (Map.Entry<String, Object> entry : additional.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                try {
                    value = OBJECT_MAPPER.readValue(s, Object.class);
                } catch (Exception e) {
                    value = s;
                }
            }
            properties.put(entry.getKey(), JsonValue.from(value));
        }
        return properties;
    }

    private static ResponseFormatJsonSchema.JsonSchema.Schema schema(String jsonSchema) {
        Map<String, Object> root;
        try {
            root = OBJECT_MAPPER.readValue(jsonSchema, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON schema content provided in header/option", e);
        }
        if (root == null) {
            throw new IllegalArgumentException("JSON schema string parsed to null");
        }
        ResponseFormatJsonSchema.JsonSchema.Schema.Builder builder = ResponseFormatJsonSchema.JsonSchema.Schema.builder();
        root.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }
}
