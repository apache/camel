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
import java.util.ArrayList;
import java.util.List;

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

/**
 * Abstract base class for watsonx.ai operation handlers providing common helper methods.
 */
public abstract class AbstractWatsonxAiHandler implements WatsonxAiOperationHandler {

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
            input = in.getBody(String.class);
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
            } else if (body instanceof String) {
                // Single string input
                inputs = List.of((String) body);
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
    @SuppressWarnings("unchecked")
    protected List<ChatMessage> getMessages(Exchange exchange) {
        Message in = exchange.getIn();

        // Try MESSAGES header first
        List<ChatMessage> messages = in.getHeader(WatsonxAiConstants.MESSAGES, List.class);

        // If MESSAGES not set, try to build from SYSTEM_MESSAGE/USER_MESSAGE headers
        if (messages == null || messages.isEmpty()) {
            String systemMessage = in.getHeader(WatsonxAiConstants.SYSTEM_MESSAGE, String.class);
            String userMessage = in.getHeader(WatsonxAiConstants.USER_MESSAGE, String.class);

            // If no USER_MESSAGE header, try body
            if (userMessage == null) {
                Object body = in.getBody();
                if (body instanceof String) {
                    userMessage = (String) body;
                } else if (body instanceof List) {
                    // Body is a list of messages
                    return (List<ChatMessage>) body;
                }
            }

            // Build messages from headers
            if (systemMessage != null || userMessage != null) {
                messages = new ArrayList<>();
                if (systemMessage != null) {
                    messages.add(SystemMessage.of(systemMessage));
                }
                if (userMessage != null) {
                    messages.add(UserMessage.text(userMessage));
                }
            }
        }

        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chat messages must be provided via message body, header '" + WatsonxAiConstants.MESSAGES
                                               + "', or headers '" + WatsonxAiConstants.SYSTEM_MESSAGE + "'/'"
                                               + WatsonxAiConstants.USER_MESSAGE + "'");
        }

        return messages;
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

            if (fileObject instanceof File) {
                // Local file (file:// component)
                return FileInput.of((File) fileObject);
            } else {
                // Remote file (ftp://, sftp://) - use type converter to get InputStream
                String fileName = resolveFileName(in);
                InputStream is = in.getBody(InputStream.class);
                return FileInput.of(is, fileName);
            }
        }

        // 2. Check for direct File
        if (body instanceof File) {
            return FileInput.of((File) body);
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

        fileName = in.getHeader("CamelAwsS3Key", String.class);
        if (fileName != null) {
            return extractFileName(fileName);
        }

        fileName = in.getHeader("CamelAzureStorageBlobName", String.class);
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
