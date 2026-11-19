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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.textgeneration.TextGenerationParameters;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.support.FileInput;
import org.apache.camel.spi.BodyTypeRegistry;
import org.apache.camel.support.BodyFacts;

/**
 * Abstract base class for watsonx.ai operation handlers providing common helper methods.
 */
public abstract class AbstractWatsonxAiHandler implements WatsonxAiOperationHandler {

    /** The object key header set by camel-aws2-s3 ({@code AWS2S3Constants.KEY}). */
    static final String AWS_S3_KEY_HEADER = "CamelAwsS3Key";
    /** The blob name header set by camel-azure-storage-blob ({@code BlobConstants.BLOB_NAME}). */
    static final String AZURE_BLOB_NAME_HEADER = "CamelAzureStorageBlobBlobName";

    protected final WatsonxAiEndpoint endpoint;

    protected AbstractWatsonxAiHandler(WatsonxAiEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    protected WatsonxAiConfiguration getConfiguration() {
        return endpoint.getConfiguration();
    }

    /**
     * Extracts single text input from exchange body or header.
     */
    protected String getInput(Exchange exchange) {
        Message in = exchange.getIn();

        // Try header first
        String input = in.getHeader(WatsonxAiConstants.INPUT, String.class);

        // Fall back to body
        if (input == null || input.isEmpty()) {
            input = readAsText(in, "the prompt");
        }

        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException(
                    "Input text must be provided via message body or header '" + WatsonxAiConstants.INPUT + "'");
        }

        return input;
    }

    /**
     * Extracts list of inputs from exchange body or header.
     */
    @SuppressWarnings("unchecked")
    protected List<String> getInputs(Exchange exchange) {
        Message in = exchange.getIn();

        // Try header first
        List<String> inputs = in.getHeader(WatsonxAiConstants.INPUTS, List.class);

        // Fall back to body
        if (inputs == null || inputs.isEmpty()) {
            Object body = in.getBody();
            if (body instanceof List) {
                inputs = (List<String>) body;
            } else if (body != null) {
                // Single input, read as text (as textGeneration does), so a byte[] or InputStream body works too
                String text = readAsText(in, "the input text");
                if (text != null) {
                    inputs = List.of(text);
                }
            }
        }

        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Input texts must be provided via message body or header '" + WatsonxAiConstants.INPUTS + "'");
        }

        return inputs;
    }

    /**
     * Extracts chat messages from exchange headers or body.
     */
    protected List<ChatMessage> getMessages(Exchange exchange) {
        Message in = exchange.getIn();

        // Try MESSAGES header first
        List<ChatMessage> messages
                = toChatMessages(in.getHeader(WatsonxAiConstants.MESSAGES, List.class),
                        "header '" + WatsonxAiConstants.MESSAGES + "'");

        // If MESSAGES not set, try to build from SYSTEM_MESSAGE/USER_MESSAGE headers
        if (messages.isEmpty()) {
            String systemMessage = in.getHeader(WatsonxAiConstants.SYSTEM_MESSAGE, String.class);
            String userMessage = in.getHeader(WatsonxAiConstants.USER_MESSAGE, String.class);

            // If no USER_MESSAGE header, try body
            if (userMessage == null) {
                Object body = in.getBody();
                if (body instanceof List<?> list) {
                    // Body is a list of messages
                    messages = toChatMessages(list, "message body");
                } else if (body instanceof ChatMessage chatMessage) {
                    messages = List.of(chatMessage);
                } else if (body != null) {
                    // The user message, read as text (as textGeneration does), so a byte[] or InputStream body works too
                    userMessage = readAsText(in, "the user message");
                }
            }

            // Build messages from headers
            if (messages.isEmpty() && (systemMessage != null || userMessage != null)) {
                messages = new ArrayList<>();
                if (systemMessage != null) {
                    messages.add(SystemMessage.of(systemMessage));
                }
                if (userMessage != null) {
                    messages.add(UserMessage.text(userMessage));
                }
            }
        }

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chat messages must be provided via message body, header '" + WatsonxAiConstants.MESSAGES
                                               + "', or headers '" + WatsonxAiConstants.SYSTEM_MESSAGE + "'/'"
                                               + WatsonxAiConstants.USER_MESSAGE + "'");
        }

        return messages;
    }

    /**
     * Checks the elements of a list of chat messages: a ChatMessage is used as it is, and a String is a user message.
     * Any other element fails, instead of a ClassCastException when the request is built.
     */
    private static List<ChatMessage> toChatMessages(List<?> list, String source) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof ChatMessage chatMessage) {
                messages.add(chatMessage);
            } else if (element instanceof String text) {
                messages.add(UserMessage.text(text));
            } else {
                throw new IllegalArgumentException(
                        "The chat messages in the " + source + " must be ChatMessage objects or Strings (user messages),"
                                                   + " but an element is "
                                                   + (element == null ? "null" : element.getClass().getName()));
            }
        }
        return messages;
    }

    /**
     * The body as the text an operation reads, whether it sends it to the model or uses it as a file path or a request
     * id: text, bytes, a stream or a file is read as it is, and so is any other body Camel converts to its text, such
     * as a {@code char[]} or a {@code ByteBuffer}. A Map or a POJO fails instead, since Camel has no conversion for it
     * other than its {@code toString()}, so the model would be asked about a map written in Java notation, or the
     * request would name such a map as its file (CAMEL-26521).
     *
     * @param  in   the inbound message whose body to read
     * @param  what the input in the words of the operation, such as "the prompt", for the error message of a body that
     *              is not text
     * @return      the body as text, or {@code null} when the body is null
     */
    protected static String readAsText(Message in, String what) {
        Object body = in.getBody();
        String text = in.getBody(String.class);
        if (text != null && isOnlyToString(body, text)) {
            throw new IllegalArgumentException(
                    "The message body must be " + what + " as text (a String, bytes, a stream or a file), but it is a "
                                               + body.getClass().getName()
                                               + ", which Camel reads only as its toString(). Marshal it first, for"
                                               + " example with marshal().json(), set the body to the text, or, when"
                                               + " the toString() of the object is the text to send, convert it with"
                                               + " convertBodyTo(String.class)");
        }
        return text;
    }

    /**
     * Whether the text is no more than the {@code toString()} of the body, which {@code ToStringTypeConverter} gives
     * for an object no type converter reads as text. A payload (text, bytes, a stream, a file) or a single value (a
     * number, a boolean) is text by itself, whatever its {@code toString()}.
     */
    private static boolean isOnlyToString(Object body, String text) {
        return BodyFacts.kindOf(body) != BodyTypeRegistry.Kind.PAYLOAD && !isTextValue(body)
                && text.equals(body.toString());
    }

    /**
     * A value whose {@code toString()} is its text, rather than a dump of its fields: an id, an address or a point in
     * time. {@link BodyFacts} counts text, a number, a boolean and an enum as a payload, but not these, and the type
     * converter has nothing for them either, so they would otherwise be read as only a {@code toString()} (raised in
     * the review of CAMEL-26521).
     */
    private static boolean isTextValue(Object body) {
        return body instanceof UUID || body instanceof URI || body instanceof URL || body instanceof Temporal
                || body instanceof TemporalAmount || body instanceof Date;
    }

    /**
     * Extracts file/stream from message body, supporting Camel file components.
     */
    protected FileInput getFileInput(Exchange exchange) {
        Message in = exchange.getIn();
        Object body = in.getBody();

        // 1. Handle WrappedFile (from file/ftp/sftp components) - unwrap to get the actual file
        if (body instanceof WrappedFile) {
            WrappedFile<?> wrappedFile = (WrappedFile<?>) body;
            Object fileObject = wrappedFile.getFile();

            if (fileObject instanceof File file) {
                // Local file (file:// component)
                return FileInput.of(file);
            } else {
                // Remote file (ftp://, sftp://) - use type converter to get InputStream
                String fileName = resolveFileName(in);
                InputStream is = in.getBody(InputStream.class);
                return FileInput.of(is, fileName);
            }
        }

        // 2. Check for direct File
        if (body instanceof File file) {
            return FileInput.of(file);
        }

        // 3. Check FILE header
        File headerFile = in.getHeader(WatsonxAiConstants.FILE, File.class);
        if (headerFile != null) {
            return FileInput.of(headerFile);
        }

        // 4. Try type converter to get File first (more efficient than InputStream)
        File convertedFile = in.getBody(File.class);
        if (convertedFile != null) {
            return FileInput.of(convertedFile);
        }

        // 5. Fall back to InputStream via type converter (works with GenericFile, byte[], String, etc.)
        InputStream convertedStream = in.getBody(InputStream.class);
        if (convertedStream != null) {
            String fileName = resolveFileName(in);
            return FileInput.of(convertedStream, fileName);
        }

        throw new IllegalArgumentException(
                "File input must be provided as File, WrappedFile, InputStream, or byte[] in message body, "
                                           + "or as File in header '" + WatsonxAiConstants.FILE + "'");
    }

    /**
     * Resolves file name from headers in priority order.
     */
    protected String resolveFileName(Message in) {
        // Priority: explicit header > S3 > Azure > Camel file name
        String fileName = in.getHeader(WatsonxAiConstants.FILE_NAME, String.class);
        if (fileName != null) {
            return extractFileName(fileName);
        }

        fileName = in.getHeader(AWS_S3_KEY_HEADER, String.class);
        if (fileName != null) {
            return extractFileName(fileName);
        }

        fileName = in.getHeader(AZURE_BLOB_NAME_HEADER, String.class);
        if (fileName != null) {
            return extractFileName(fileName);
        }

        fileName = in.getHeader(Exchange.FILE_NAME, String.class);
        if (fileName != null) {
            return extractFileName(fileName);
        }

        throw new IllegalArgumentException(
                "File name must be provided via header '" + WatsonxAiConstants.FILE_NAME
                                           + "' when using InputStream input");
    }

    /**
     * Extracts just the file name from a path.
     */
    protected String extractFileName(String path) {
        if (path == null) {
            return null;
        }
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Applies text generation parameters from configuration and exchange headers. Configuration values are applied
     * first, then header values override if present.
     *
     * @param builder  the TextGenerationParameters builder to apply parameters to
     * @param exchange the exchange containing potential header overrides
     */
    protected void applyTextGenerationParameters(TextGenerationParameters.Builder builder, Exchange exchange) {
        WatsonxAiConfiguration config = getConfiguration();
        Message in = exchange.getIn();

        // Apply configuration parameters
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        if (config.getMaxNewTokens() != null) {
            builder.maxNewTokens(config.getMaxNewTokens());
        }
        if (config.getTopP() != null) {
            builder.topP(config.getTopP());
        }
        if (config.getTopK() != null) {
            builder.topK(config.getTopK());
        }
        if (config.getRepetitionPenalty() != null) {
            builder.repetitionPenalty(config.getRepetitionPenalty());
        }

        // Override with header values if present
        Double headerTemp = in.getHeader(WatsonxAiConstants.TEMPERATURE, Double.class);
        if (headerTemp != null) {
            builder.temperature(headerTemp);
        }

        Integer headerMaxTokens = in.getHeader(WatsonxAiConstants.MAX_NEW_TOKENS, Integer.class);
        if (headerMaxTokens != null) {
            builder.maxNewTokens(headerMaxTokens);
        }

        Double headerTopP = in.getHeader(WatsonxAiConstants.TOP_P, Double.class);
        if (headerTopP != null) {
            builder.topP(headerTopP);
        }

        Integer headerTopK = in.getHeader(WatsonxAiConstants.TOP_K, Integer.class);
        if (headerTopK != null) {
            builder.topK(headerTopK);
        }

        Double headerRepPenalty = in.getHeader(WatsonxAiConstants.REPETITION_PENALTY, Double.class);
        if (headerRepPenalty != null) {
            builder.repetitionPenalty(headerRepPenalty);
        }
    }

    /**
     * Applies chat parameters from configuration and exchange headers. Configuration values are applied first, then
     * header values override if present.
     *
     * @param builder  the ChatParameters builder to apply parameters to
     * @param exchange the exchange containing potential header overrides
     */
    protected void applyChatParameters(ChatParameters.Builder builder, Exchange exchange) {
        WatsonxAiConfiguration config = getConfiguration();
        Message in = exchange.getIn();

        // Apply configuration parameters
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        if (config.getMaxCompletionTokens() != null) {
            builder.maxCompletionTokens(config.getMaxCompletionTokens());
        }
        if (config.getTopP() != null) {
            builder.topP(config.getTopP());
        }
        if (config.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(config.getFrequencyPenalty());
        }
        if (config.getPresencePenalty() != null) {
            builder.presencePenalty(config.getPresencePenalty());
        }

        // Override with header values if present
        Double headerTemp = in.getHeader(WatsonxAiConstants.TEMPERATURE, Double.class);
        if (headerTemp != null) {
            builder.temperature(headerTemp);
        }

        Integer headerMaxTokens = in.getHeader(WatsonxAiConstants.MAX_NEW_TOKENS, Integer.class);
        if (headerMaxTokens != null) {
            builder.maxCompletionTokens(headerMaxTokens);
        }

        Double headerTopP = in.getHeader(WatsonxAiConstants.TOP_P, Double.class);
        if (headerTopP != null) {
            builder.topP(headerTopP);
        }
    }
}
