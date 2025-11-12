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
package org.apache.camel.component.springai.chat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.springai.tools.TagsHelper;
import org.apache.camel.component.springai.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.springai.tools.spec.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.MimeType;

/**
 * Producer for Spring AI Chat operations using ChatClient.
 */
public class SpringAiChatProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiChatProducer.class);

    private ChatClient chatClient;

    public SpringAiChatProducer(SpringAiChatEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringAiChatEndpoint getEndpoint() {
        return (SpringAiChatEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Initialize ChatClient
        ChatClient configuredClient = getEndpoint().getConfiguration().getChatClient();
        if (configuredClient != null) {
            // Use the provided ChatClient
            this.chatClient = configuredClient;
        } else {
            // Create ChatClient from ChatModel
            ChatModel chatModel = getEndpoint().getConfiguration().getChatModel();
            if (chatModel == null) {
                throw new IllegalArgumentException(
                        "Either ChatClient or ChatModel must be configured");
            }

            ChatClient.Builder builder = ChatClient.builder(chatModel);

            // Add default advisors based on configuration
            List<Advisor> advisors = buildDefaultAdvisors();
            if (!advisors.isEmpty()) {
                builder.defaultAdvisors(advisors);
            }

            // Set default system message if configured
            String systemMessage = getEndpoint().getConfiguration().getSystemMessage();
            if (systemMessage != null) {
                builder.defaultSystem(systemMessage);
            }

            this.chatClient = builder.build();
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SpringAiChatOperations operation = getEndpoint().getConfiguration().getChatOperation();

        switch (operation) {
            case CHAT_SINGLE_MESSAGE:
                processSingleMessage(exchange);
                break;
            case CHAT_SINGLE_MESSAGE_WITH_PROMPT:
                processSingleMessageWithPrompt(exchange);
                break;
            case CHAT_MULTIPLE_MESSAGES:
                processMultipleMessages(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void processSingleMessage(Exchange exchange) throws Exception {
        // Get the message body
        final Object messageBody = exchange.getIn().getMandatoryBody();

        ChatClient.ChatClientRequestSpec request = chatClient.prompt();

        // Check for manual augmented data for RAG
        String userMessageText = null;
        List<Document> augmentedData = exchange.getIn().getHeader(SpringAiChatConstants.AUGMENTED_DATA, List.class);

        // Warn if both automatic RAG (QuestionAnswerAdvisor) and manual RAG (AUGMENTED_DATA header) are configured
        if (augmentedData != null && !augmentedData.isEmpty() && isQuestionAnswerAdvisorConfigured()) {
            LOG.warn("Both AUGMENTED_DATA header and QuestionAnswerAdvisor (VectorStore) are configured. " +
                     "This may result in redundant or conflicting context being added to the prompt. " +
                     "Consider using only one RAG method: either automatic (QuestionAnswerAdvisor) or manual (AUGMENTED_DATA header).");
        }

        // Handle different body types
        if (messageBody instanceof String) {
            userMessageText = (String) messageBody;
        } else if (messageBody instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) messageBody;
            userMessageText = userMsg.getText();
            // If there's media, we need to handle it differently using additive builder
            if (!userMsg.getMedia().isEmpty()) {
                applyUserMessageWithMedia(request, exchange, userMsg.getText(), userMsg.getMedia());
                userMessageText = null; // Already handled
            }
        } else if (messageBody instanceof Message) {
            // Note: Using request.messages() here replaces the entire message list,
            // which will ignore any default system message configured in doStart() or via headers.
            // This is intentional for the CHAT_SINGLE_MESSAGE operation when a complete Message is provided.
            request.messages((Message) messageBody);
        } else if (messageBody instanceof WrappedFile) {
            // Handle single WrappedFile for multimodal content
            WrappedFile<?> wrappedFile = (WrappedFile<?>) messageBody;
            List<Media> mediaList = convertWrappedFilesToMedia(exchange, List.of(wrappedFile));
            UserMessage multimodalMessage = createMultimodalMessageWithMedia(exchange, mediaList);
            applyUserMessageWithMedia(request, exchange, multimodalMessage.getText(), multimodalMessage.getMedia());
        } else if (messageBody instanceof List) {
            // Handle List<WrappedFile> for multimodal content with multiple files
            List<?> list = (List<?>) messageBody;
            if (!list.isEmpty() && list.get(0) instanceof WrappedFile) {
                @SuppressWarnings("unchecked")
                List<WrappedFile<?>> wrappedFiles = (List<WrappedFile<?>>) list;
                List<Media> mediaList = convertWrappedFilesToMedia(exchange, wrappedFiles);
                UserMessage multimodalMessage = createMultimodalMessageWithMedia(exchange, mediaList);
                applyUserMessageWithMedia(request, exchange, multimodalMessage.getText(), multimodalMessage.getMedia());
            } else {
                throw new IllegalArgumentException(
                        "List body must contain WrappedFile objects. Got: "
                                                   + (list.isEmpty() ? "empty list" : list.get(0).getClass().getName()));
            }
        } else if (messageBody instanceof byte[]) {
            // Handle multimodal content (image or audio)
            UserMessage multimodalMessage = createMultimodalMessage(exchange, (byte[]) messageBody);
            applyUserMessageWithMedia(request, exchange, multimodalMessage.getText(), multimodalMessage.getMedia());
        } else {
            throw new IllegalArgumentException(
                    "Unsupported message type: " + messageBody.getClass().getName()
                                               + ". Expected String, byte[], WrappedFile, List<WrappedFile>, or org.springframework.ai.chat.messages.Message");
        }

        // Apply augmented data to user message if provided
        if (userMessageText != null && augmentedData != null && !augmentedData.isEmpty()) {
            String context = augmentedData.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
            userMessageText = formatRagPrompt(context, userMessageText);
        }

        // Set the user message if we have one
        if (userMessageText != null) {
            applyUserMessageWithText(request, exchange, userMessageText);
        }

        // Apply configuration and headers (augmented data is already handled above)
        applyRequestOptions(request, exchange);

        // Execute the request with entity or structured output handling
        executeRequest(request, exchange);
    }

    /**
     * Process a single message using a PromptTemplate.
     * <p>
     * This method passes ALL messages from the rendered PromptTemplate to the ChatClient, including SystemMessage and
     * UserMessage. This allows templates to define both system roles and user messages.
     * </p>
     */
    private void processSingleMessageWithPrompt(Exchange exchange) throws NoSuchHeaderException, InvalidPayloadException {
        // Get the prompt template from header
        final String promptTemplateText
                = exchange.getIn().getHeader(SpringAiChatConstants.PROMPT_TEMPLATE, String.class);
        if (promptTemplateText == null) {
            throw new NoSuchHeaderException(
                    "The promptTemplate is a required header for CHAT_SINGLE_MESSAGE_WITH_PROMPT operation",
                    exchange,
                    SpringAiChatConstants.PROMPT_TEMPLATE);
        }

        // Get variables from message body
        Map<String, Object> variables = exchange.getIn().getMandatoryBody(Map.class);

        // Create prompt using Spring AI's PromptTemplate
        PromptTemplate template = new PromptTemplate(promptTemplateText);
        Prompt prompt = template.create(variables);

        // Pass ALL messages from the prompt template (including SystemMessage, UserMessage, etc.)
        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .messages(prompt.getInstructions());

        // Apply configuration and headers
        applyRequestOptions(request, exchange);

        // Execute the request with entity or structured output handling
        executeRequest(request, exchange);
    }

    private void processMultipleMessages(Exchange exchange) throws InvalidPayloadException {
        // Get messages from body
        List<Message> messages = exchange.getIn().getMandatoryBody(List.class);

        ChatClient.ChatClientRequestSpec request = chatClient.prompt().messages(messages);

        // Apply configuration and headers
        applyRequestOptions(request, exchange);

        // Execute the request with entity or structured output handling
        executeRequest(request, exchange);
    }

    private UserMessage createMultimodalMessage(Exchange exchange, byte[] mediaData) {
        String userMessageText = getUserMessageText(exchange);

        // Get media type from header or try to detect
        String mediaTypeStr = exchange.getIn().getHeader(SpringAiChatConstants.MEDIA_TYPE, String.class);
        if (mediaTypeStr == null) {
            // Default to image/png if not specified
            mediaTypeStr = "image/png";
        }

        MimeType mimeType = MimeType.valueOf(mediaTypeStr);
        Media media = Media.builder()
                .mimeType(mimeType)
                .data(mediaData)
                .build();

        return UserMessage.builder()
                .text(userMessageText)
                .media(media)
                .build();
    }

    /**
     * Create a multimodal message with multiple media items
     */
    private UserMessage createMultimodalMessageWithMedia(Exchange exchange, List<Media> mediaList) {
        String userMessageText = getUserMessageText(exchange);

        return UserMessage.builder()
                .text(userMessageText)
                .media(mediaList)
                .build();
    }

    /**
     * Get user message text from header or configuration, with empty string as fallback
     */
    private String getUserMessageText(Exchange exchange) {
        String userMessageText = exchange.getIn().getHeader(SpringAiChatConstants.USER_MESSAGE, String.class);
        if (userMessageText == null) {
            userMessageText = getEndpoint().getConfiguration().getUserMessage();
        }
        if (userMessageText == null) {
            userMessageText = ""; // Empty text for media-only messages
        }
        return userMessageText;
    }

    /**
     * Convert a list of WrappedFile objects to Media objects
     */
    private List<Media> convertWrappedFilesToMedia(Exchange exchange, List<WrappedFile<?>> wrappedFiles) {
        List<Media> mediaList = new ArrayList<>();

        for (WrappedFile<?> wrappedFile : wrappedFiles) {
            // Unwrap the file
            Object fileObj = wrappedFile.getFile();
            if (fileObj == null) {
                throw new IllegalArgumentException("WrappedFile contains null file");
            }
            if (!(fileObj instanceof File)) {
                throw new IllegalArgumentException(
                        "WrappedFile must contain a java.io.File instance, got: " + fileObj.getClass().getName());
            }

            File file = (File) fileObj;

            // Detect MIME type
            String mimeTypeStr = detectMimeType(file, exchange);
            MimeType mimeType = MimeType.valueOf(mimeTypeStr);

            // Convert file to byte array with size validation
            byte[] fileData = fileToByteArray(file, exchange);

            // Create Media object
            Media media = Media.builder()
                    .mimeType(mimeType)
                    .data(fileData)
                    .build();

            mediaList.add(media);
        }

        return mediaList;
    }

    /**
     * Detect MIME type from file extension and component headers Priority: 1) CamelSpringAiChatMediaType header, 2)
     * Component-specific headers (FILE_CONTENT_TYPE, etc.), 3) Auto-detection from extension, 4) Default to image/png
     */
    private String detectMimeType(File file, Exchange exchange) {
        // First check if MIME type is provided in spring-ai-chat specific header (highest priority)
        String mediaTypeStr = exchange.getIn().getHeader(SpringAiChatConstants.MEDIA_TYPE, String.class);
        if (mediaTypeStr != null) {
            return mediaTypeStr;
        }

        // Check for file component's content type header (camel-file, camel-ftp, camel-sftp, camel-smb)
        String fileContentType = exchange.getIn().getHeader(Exchange.FILE_CONTENT_TYPE, String.class);
        if (fileContentType != null) {
            return fileContentType;
        }

        // Auto-detect from file extension
        String fileName = file.getName().toLowerCase();

        // Image formats (matching Spring AI Media.Format constants)
        if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (fileName.endsWith(".tiff") || fileName.endsWith(".tif")) {
            return "image/tiff";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        // Video formats (matching Spring AI Media.Format constants)
        else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".webm")) {
            return "video/webm";
        } else if (fileName.endsWith(".mov")) {
            return "video/quicktime";
        } else if (fileName.endsWith(".mkv")) {
            return "video/x-matroska";
        } else if (fileName.endsWith(".flv")) {
            return "video/x-flv";
        } else if (fileName.endsWith(".mpeg") || fileName.endsWith(".mpg")) {
            return "video/mpeg";
        } else if (fileName.endsWith(".wmv")) {
            return "video/x-ms-wmv";
        } else if (fileName.endsWith(".3gp")) {
            return "video/3gpp";
        }
        // Audio formats (using standard MIME types)
        else if (fileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (fileName.endsWith(".mp3")) {
            return "audio/mpeg"; // Correct MIME type for MP3
        } else if (fileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (fileName.endsWith(".m4a")) {
            return "audio/mp4"; // Standard MIME type for M4A
        } else if (fileName.endsWith(".flac")) {
            return "audio/flac";
        }
        // Document formats (matching Spring AI Media.Format constants)
        else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (fileName.endsWith(".csv")) {
            return "text/csv";
        } else if (fileName.endsWith(".doc")) {
            return "application/msword";
        } else if (fileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (fileName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (fileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.endsWith(".txt")) {
            return "text/plain";
        } else if (fileName.endsWith(".md")) {
            return "text/markdown";
        }

        // Default to image/png if extension not recognized
        LOG.debug("Could not detect MIME type from file extension: {}, defaulting to image/png", fileName);
        return "image/png";
    }

    /**
     * Convert a File to byte array with size validation
     */
    private byte[] fileToByteArray(File file) {
        return fileToByteArray(file, null);
    }

    /**
     * Convert a File to byte array with size validation
     */
    private byte[] fileToByteArray(File file, Exchange exchange) {
        long fileSize = file.length();
        long maxSize = getMaxFileSize(exchange);

        // Validate file size if max size is set (0 means no limit)
        if (maxSize > 0 && fileSize > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes): %s",
                            fileSize, maxSize, file.getAbsolutePath()));
        }

        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Get max file size from header (priority) or configuration
     */
    private long getMaxFileSize(Exchange exchange) {
        if (exchange != null) {
            Long headerMaxSize = exchange.getIn().getHeader(SpringAiChatConstants.MAX_FILE_SIZE, Long.class);
            if (headerMaxSize != null) {
                return headerMaxSize;
            }
        }
        return getEndpoint().getConfiguration().getMaxFileSize();
    }

    private void applyRequestOptions(ChatClient.ChatClientRequestSpec request, Exchange exchange) {
        // Apply system message from header (overrides configuration)
        String systemMessage = exchange.getIn().getHeader(SpringAiChatConstants.SYSTEM_MESSAGE, String.class);
        if (systemMessage != null) {
            Map<String, Object> systemMetadata = getSystemMetadata(exchange);
            final String finalSystemMessage = systemMessage;
            request.system(s -> {
                s.text(finalSystemMessage);
                if (systemMetadata != null && !systemMetadata.isEmpty()) {
                    s.metadata(systemMetadata);
                }
            });
        }

        // Note: Augmented data is handled in the calling method for better control

        // Get tool callbacks first if tags are configured
        List<ToolCallback> toolCallbacks = getToolCallbacksForTags(getEndpoint().getConfiguration().getTags());

        // Apply chat options from configuration and headers using ToolCallingChatOptions
        // This ensures tool callbacks are properly included in the options
        ToolCallingChatOptions.Builder optionsBuilder = ToolCallingChatOptions.builder();

        // Add tool callbacks to the options builder if present
        if (!toolCallbacks.isEmpty()) {
            LOG.debug("Adding {} tool callbacks to ToolCallingChatOptions", toolCallbacks.size());
            optionsBuilder.toolCallbacks(toolCallbacks);
        }

        // Check configuration first
        if (getEndpoint().getConfiguration().getTemperature() != null) {
            optionsBuilder.temperature(getEndpoint().getConfiguration().getTemperature());
        }
        if (getEndpoint().getConfiguration().getMaxTokens() != null) {
            optionsBuilder.maxTokens(getEndpoint().getConfiguration().getMaxTokens());
        }
        if (getEndpoint().getConfiguration().getTopP() != null) {
            optionsBuilder.topP(getEndpoint().getConfiguration().getTopP());
        }
        if (getEndpoint().getConfiguration().getTopKSampling() != null) {
            optionsBuilder.topK(getEndpoint().getConfiguration().getTopKSampling());
        }

        // Headers override configuration
        Double temperature = exchange.getIn().getHeader(SpringAiChatConstants.TEMPERATURE, Double.class);
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }

        Integer maxTokens = exchange.getIn().getHeader(SpringAiChatConstants.MAX_TOKENS, Integer.class);
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        Double topP = exchange.getIn().getHeader(SpringAiChatConstants.TOP_P, Double.class);
        if (topP != null) {
            optionsBuilder.topP(topP);
        }

        Integer topK = exchange.getIn().getHeader(SpringAiChatConstants.TOP_K, Integer.class);
        if (topK != null) {
            optionsBuilder.topK(topK);
        }

        ToolCallingChatOptions options = optionsBuilder.build();
        request.options(options);

        // Apply conversation ID for chat memory if provided
        String conversationId = exchange.getIn().getHeader(SpringAiChatConstants.CONVERSATION_ID, String.class);
        if (conversationId != null) {
            request.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }

        // Apply SafeGuard advisor overrides if provided via headers
        applySafeguardHeaderOverrides(request, exchange);

        // Apply custom advisors from headers if provided
        applyCustomAdvisorsFromHeaders(request, exchange);
    }

    /**
     * Apply SafeGuard advisor configuration overrides from headers
     */
    private void applySafeguardHeaderOverrides(ChatClient.ChatClientRequestSpec request, Exchange exchange) {
        String sensitiveWords = exchange.getIn().getHeader(SpringAiChatConstants.SAFEGUARD_SENSITIVE_WORDS, String.class);
        String failureResponse = exchange.getIn().getHeader(SpringAiChatConstants.SAFEGUARD_FAILURE_RESPONSE, String.class);
        Integer order = exchange.getIn().getHeader(SpringAiChatConstants.SAFEGUARD_ORDER, Integer.class);

        // If any SafeGuard header is provided, we need to configure/override the SafeGuard advisor for this request
        if (sensitiveWords != null || failureResponse != null || order != null) {
            // Start with configuration values, then override with headers
            String finalSensitiveWords = sensitiveWords != null
                    ? sensitiveWords
                    : getEndpoint().getConfiguration().getSafeguardSensitiveWords();
            String finalFailureResponse = failureResponse != null
                    ? failureResponse
                    : getEndpoint().getConfiguration().getSafeguardFailureResponse();
            Integer finalOrder = order != null
                    ? order
                    : getEndpoint().getConfiguration().getSafeguardOrder();

            // Build and add SafeGuard advisor if we have sensitive words
            SafeGuardAdvisor safeguardAdvisor
                    = buildSafeguardAdvisor(finalSensitiveWords, finalFailureResponse, finalOrder, true);
            if (safeguardAdvisor != null) {
                request.advisors(safeguardAdvisor);
            }
        }
    }

    /**
     * Apply custom advisors from headers to the request
     */
    @SuppressWarnings("unchecked")
    private void applyCustomAdvisorsFromHeaders(ChatClient.ChatClientRequestSpec request, Exchange exchange) {
        List<Advisor> customAdvisors = exchange.getIn().getHeader(SpringAiChatConstants.ADVISORS, List.class);

        if (customAdvisors != null && !customAdvisors.isEmpty()) {
            // Add each custom advisor individually to the request
            for (Advisor advisor : customAdvisors) {
                request.advisors(advisor);
            }
            LOG.debug("Added {} custom advisors from headers", customAdvisors.size());
        }
    }

    private void populateResponse(ChatResponse response, Exchange exchange) {
        String responseText = response.getResult().getOutput().getText();
        exchange.getMessage().setBody(responseText);
        exchange.getMessage().setHeader(SpringAiChatConstants.CHAT_RESPONSE, responseText);

        populateTokenUsage(response, exchange);
        populateResponseMetadata(response, exchange);
    }

    private void populateTokenUsage(ChatResponse response, Exchange exchange) {
        // Add token usage information if available
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            exchange.getMessage().setHeader(SpringAiChatConstants.INPUT_TOKEN_COUNT,
                    usage.getPromptTokens());
            exchange.getMessage().setHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT,
                    usage.getCompletionTokens());
            exchange.getMessage().setHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT,
                    usage.getTotalTokens());
        }
    }

    private void populateResponseMetadata(ChatResponse response, Exchange exchange) {
        // Add response metadata if available
        if (response.getMetadata() != null) {
            var responseMetadata = response.getMetadata();

            // Model name
            if (responseMetadata.getModel() != null) {
                exchange.getMessage().setHeader(SpringAiChatConstants.MODEL_NAME,
                        responseMetadata.getModel());
            }

            // Response ID
            if (responseMetadata.getId() != null) {
                exchange.getMessage().setHeader(SpringAiChatConstants.RESPONSE_ID,
                        responseMetadata.getId());
            }

            // Finish reason is on the Generation metadata, not the Response metadata
            if (response.getResult() != null && response.getResult().getMetadata() != null) {
                String finishReason = response.getResult().getMetadata().getFinishReason();
                if (finishReason != null) {
                    exchange.getMessage().setHeader(SpringAiChatConstants.FINISH_REASON, finishReason);
                }
            }

            // Full metadata map for advanced users
            // This includes all metadata fields in a single map
            Map<String, Object> metadataMap = new java.util.HashMap<>();
            if (responseMetadata.getId() != null) {
                metadataMap.put("id", responseMetadata.getId());
            }
            if (responseMetadata.getModel() != null) {
                metadataMap.put("model", responseMetadata.getModel());
            }
            // Add finish reason from generation metadata
            if (response.getResult() != null && response.getResult().getMetadata() != null) {
                String finishReason = response.getResult().getMetadata().getFinishReason();
                if (finishReason != null) {
                    metadataMap.put("finishReason", finishReason);
                }
            }
            if (responseMetadata.getUsage() != null) {
                metadataMap.put("usage", responseMetadata.getUsage());
            }
            // Add rate limit info if available
            if (responseMetadata.getRateLimit() != null) {
                metadataMap.put("rateLimit", responseMetadata.getRateLimit());
            }
            // Add prompt metadata if available
            if (responseMetadata.getPromptMetadata() != null) {
                metadataMap.put("promptMetadata", responseMetadata.getPromptMetadata());
            }

            if (!metadataMap.isEmpty()) {
                exchange.getMessage().setHeader(SpringAiChatConstants.RESPONSE_METADATA, metadataMap);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getUserMetadata(Exchange exchange) {
        // Check if metadata is provided via header (takes highest priority)
        Map<String, Object> metadata = exchange.getIn().getHeader(SpringAiChatConstants.USER_METADATA, Map.class);

        if (metadata != null) {
            return metadata;
        }

        // Check configuration for metadata
        return getEndpoint().getConfiguration().getUserMetadata();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSystemMetadata(Exchange exchange) {
        // Check if metadata is provided via header (takes highest priority)
        Map<String, Object> metadata = exchange.getIn().getHeader(SpringAiChatConstants.SYSTEM_METADATA, Map.class);

        if (metadata != null) {
            return metadata;
        }

        // Check configuration for metadata
        return getEndpoint().getConfiguration().getSystemMetadata();
    }

    private Class<?> getEntityClass(Exchange exchange) {
        // Check if entity class is provided via header (takes highest priority)
        Class<?> entityClass = exchange.getIn().getHeader(SpringAiChatConstants.ENTITY_CLASS, Class.class);

        if (entityClass != null) {
            return entityClass;
        }

        // Check configuration for entity class
        return getEndpoint().getConfiguration().getEntityClass();
    }

    /**
     * Process a chat request with entity conversion.
     * <p>
     * This method executes the chat request and automatically converts the AI model's response into a Java entity
     * object using Spring AI's entity conversion mechanism. The conversion leverages the model's ability to generate
     * structured output that matches the entity class structure.
     * </p>
     *
     * @param request     the configured chat client request
     * @param exchange    the Camel exchange
     * @param entityClass the target class to convert the response into
     * @param <T>         the type of the entity
     */
    private <T> void processEntityRequest(
            ChatClient.ChatClientRequestSpec request, Exchange exchange, Class<T> entityClass) {
        // Execute the request and convert to entity
        T entity = request.call().entity(entityClass);

        // Set the entity as the body
        exchange.getMessage().setBody(entity);

        LOG.debug("Converted response to entity of type: {}", entityClass.getName());
    }

    private StructuredOutputConverter<?> getStructuredOutputConverter(Exchange exchange) {
        // Check if converter is provided via header (takes highest priority)
        StructuredOutputConverter<?> converter
                = exchange.getIn().getHeader(SpringAiChatConstants.OUTPUT_FORMAT, StructuredOutputConverter.class);

        if (converter != null) {
            return converter;
        }

        // Check configuration for pre-configured converter
        if (getEndpoint().getConfiguration().getStructuredOutputConverter() != null) {
            return getEndpoint().getConfiguration().getStructuredOutputConverter();
        }

        // Check if we should create a converter based on headers or configuration
        String outputFormat = exchange.getIn().getHeader(SpringAiChatConstants.OUTPUT_FORMAT, String.class);
        Class<?> outputClass = exchange.getIn().getHeader(SpringAiChatConstants.OUTPUT_CLASS, Class.class);

        // If not in headers, check configuration
        if (outputFormat == null) {
            outputFormat = getEndpoint().getConfiguration().getOutputFormat();
        }
        if (outputClass == null) {
            outputClass = getEndpoint().getConfiguration().getOutputClass();
        }

        if (outputFormat != null) {
            switch (outputFormat.toUpperCase()) {
                case "BEAN":
                    if (outputClass == null) {
                        throw new IllegalArgumentException(
                                "OUTPUT_CLASS header or configuration is required when OUTPUT_FORMAT is BEAN");
                    }
                    return new BeanOutputConverter<>(outputClass);
                case "MAP":
                    return new MapOutputConverter();
                case "LIST":
                    return new ListOutputConverter(new DefaultConversionService());
                default:
                    throw new IllegalArgumentException(
                            "Unsupported OUTPUT_FORMAT: " + outputFormat + ". Supported values: BEAN, MAP, LIST");
            }
        }

        return null;
    }

    /**
     * Process a chat request with structured output conversion.
     * <p>
     * This method executes the chat request and converts the AI model's response into a structured format using a
     * {@link StructuredOutputConverter}. The converter appends format instructions to the user message, guiding the
     * model to generate output in the desired format (e.g., JSON, XML, CSV).
     * </p>
     * <p>
     * The method performs the following steps:
     * </p>
     * <ol>
     * <li>Retrieves format instructions from the converter</li>
     * <li>Appends format instructions to the user message</li>
     * <li>Executes the chat request</li>
     * <li>Converts the raw response text using the converter</li>
     * <li>Sets the structured output as the message body</li>
     * <li>Populates response headers with both raw and structured output</li>
     * </ol>
     * <p>
     * Supported converters include:
     * </p>
     * <ul>
     * <li>{@link BeanOutputConverter} - Converts response to a Java bean</li>
     * <li>{@link MapOutputConverter} - Converts response to a Map</li>
     * <li>{@link ListOutputConverter} - Converts response to a List</li>
     * </ul>
     *
     * @param request   the configured chat client request
     * @param exchange  the Camel exchange
     * @param converter the structured output converter to use
     * @param <T>       the type of the structured output
     */
    @SuppressWarnings("unchecked")
    private <T> void processStructuredOutputRequest(
            ChatClient.ChatClientRequestSpec request, Exchange exchange,
            StructuredOutputConverter<T> converter) {
        // Get format instructions from the converter
        String format = converter.getFormat();

        // Append format instructions to the request
        request.user(u -> u.text(format));

        // Execute the request
        ChatResponse response = request.call().chatResponse();

        // Get the raw response text
        String responseText = response.getResult().getOutput().getText();

        // Convert the response using the converter
        T structuredOutput = converter.convert(responseText);

        // Set the structured output as the body
        exchange.getMessage().setBody(structuredOutput);

        // Also set headers
        exchange.getMessage().setHeader(SpringAiChatConstants.CHAT_RESPONSE, responseText);
        exchange.getMessage().setHeader(SpringAiChatConstants.STRUCTURED_OUTPUT, structuredOutput);

        populateTokenUsage(response, exchange);
    }

    /**
     * Register tools from camel-spring-ai-tools routes based on configured tags
     *
     * Tools are registered via ChatOptions.toolCallbacks() which is the correct way to pass ToolCallback instances in
     * Spring AI. The tools() method expects objects with @Tool annotated methods, not ToolCallback instances.
     */
    private List<ToolCallback> getToolCallbacksForTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            LOG.debug("No tags configured, skipping tool discovery");
            return List.of();
        }

        // Discover tools from Camel Spring AI Tools routes
        List<ToolCallback> toolCallbacks = discoverTools(tags);

        if (!toolCallbacks.isEmpty()) {
            // Collect tool names for enhanced logging
            String toolNames = toolCallbacks.stream()
                    .map(callback -> callback.getToolDefinition().name())
                    .collect(Collectors.joining(", "));

            LOG.debug("Discovered {} unique tools for tags: {} - Tools: [{}]", toolCallbacks.size(), tags, toolNames);

            // Detailed logging for each tool
            for (ToolCallback callback : toolCallbacks) {
                LOG.debug("Tool found: {} - {} - Schema: {}", callback.getToolDefinition().name(),
                        callback.getToolDefinition().description(),
                        callback.getToolDefinition().inputSchema());
            }
        } else {
            LOG.warn("No tools found for tags: {}", tags);
        }

        return toolCallbacks;
    }

    /**
     * Discover tools by tags and return a list of ToolCallback instances
     */
    private List<ToolCallback> discoverTools(String tags) {
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        final String[] tagArray = TagsHelper.splitTags(tags);

        final List<ToolCallback> toolCallbacks = Arrays.stream(tagArray)
                .flatMap(tag -> tools.entrySet().stream()
                        .filter(entry -> entry.getKey().equals(tag))
                        .flatMap(entry -> entry.getValue().stream()))
                .map(CamelToolSpecification::getToolCallback)
                .collect(Collectors.toList());

        LOG.debug("Discovered {} unique tools for tags: {}", toolCallbacks.size(), tags);
        return toolCallbacks;
    }

    /**
     * Format the RAG prompt using the configured template
     */
    private String formatRagPrompt(String context, String question) {
        String template = getEndpoint().getConfiguration().getRagTemplate();
        if (template == null || template.trim().isEmpty()) {
            // Fallback to default template if not configured
            template = "Context:\n{context}\n\nQuestion: {question}";
        }
        return template
                .replace("{context}", context)
                .replace("{question}", question);
    }

    /**
     * Check if QuestionAnswerAdvisor is configured (VectorStore is present)
     */
    private boolean isQuestionAnswerAdvisorConfigured() {
        return getEndpoint().getConfiguration().getVectorStore() != null;
    }

    /**
     * Build a SafeGuard advisor with the given configuration
     *
     * @param  sensitiveWords  comma-separated list of sensitive words
     * @param  failureResponse response to return when sensitive words are detected
     * @param  order           order of the advisor
     * @param  isPerRequest    whether this is a per-request advisor (affects logging)
     * @return                 SafeGuardAdvisor instance or null if sensitiveWords is null or empty
     */
    private SafeGuardAdvisor buildSafeguardAdvisor(
            String sensitiveWords, String failureResponse, Integer order, boolean isPerRequest) {
        if (sensitiveWords == null || sensitiveWords.trim().isEmpty()) {
            return null;
        }

        SafeGuardAdvisor.Builder safeguardBuilder = SafeGuardAdvisor.builder();

        // Parse sensitive words from comma-separated list
        List<String> wordList = Arrays.stream(sensitiveWords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        safeguardBuilder.sensitiveWords(wordList);

        // Set failure response if configured
        if (failureResponse != null && !failureResponse.trim().isEmpty()) {
            safeguardBuilder.failureResponse(failureResponse);
        }

        // Set order if configured
        if (order != null) {
            safeguardBuilder.order(order);
        }

        String logMessage = isPerRequest
                ? "Per-request SafeGuardAdvisor configured with {} sensitive words"
                : "SafeGuardAdvisor enabled with {} sensitive words";
        LOG.debug(logMessage, wordList.size());

        return safeguardBuilder.build();
    }

    /**
     * Apply a text-only user message to the chat request.
     * <p>
     * This method configures the user message part of the chat request with the provided text content. It also applies
     * any user metadata from the exchange headers or endpoint configuration. User metadata can be used to pass
     * additional context or information that should be associated with the user message.
     * </p>
     *
     * @param request  the chat client request to configure
     * @param exchange the Camel exchange containing headers and configuration
     * @param text     the user message text content
     */
    private void applyUserMessageWithText(ChatClient.ChatClientRequestSpec request, Exchange exchange, String text) {
        Map<String, Object> userMetadata = getUserMetadata(exchange);
        request.user(u -> {
            u.text(text);
            if (userMetadata != null && !userMetadata.isEmpty()) {
                u.metadata(userMetadata);
            }
        });
    }

    /**
     * Apply a multimodal user message with media attachments to the chat request.
     * <p>
     * This method configures the user message part of the chat request with both text content and media attachments
     * (such as images, audio, or documents). This enables multimodal AI interactions where the model can process and
     * analyze visual or audio content alongside text. It also applies any user metadata from the exchange headers or
     * endpoint configuration.
     * </p>
     * <p>
     * Supported media types include:
     * </p>
     * <ul>
     * <li>Images: PNG, JPEG, GIF, WebP, BMP, TIFF, SVG</li>
     * <li>Audio: WAV, MP3, OGG, M4A, FLAC</li>
     * <li>Documents: PDF</li>
     * </ul>
     *
     * @param request  the chat client request to configure
     * @param exchange the Camel exchange containing headers and configuration
     * @param text     the user message text content (can be empty for media-only messages)
     * @param media    the list of media attachments to include with the message
     */
    private void applyUserMessageWithMedia(
            ChatClient.ChatClientRequestSpec request, Exchange exchange, String text, List<Media> media) {
        Map<String, Object> userMetadata = getUserMetadata(exchange);
        request.user(u -> {
            u.text(text).media(media.toArray(new Media[0]));
            if (userMetadata != null && !userMetadata.isEmpty()) {
                u.metadata(userMetadata);
            }
        });
    }

    /**
     * Execute the request with entity or structured output handling
     */
    private void executeRequest(ChatClient.ChatClientRequestSpec request, Exchange exchange) {
        // Check for entity class (takes precedence over structured output converter)
        Class<?> entityClass = getEntityClass(exchange);

        // Check for structured output converter
        StructuredOutputConverter<?> converter = getStructuredOutputConverter(exchange);

        // Execute based on entity class and structured output
        if (entityClass != null) {
            processEntityRequest(request, exchange, entityClass);
        } else if (converter != null) {
            processStructuredOutputRequest(request, exchange, converter);
        } else {
            ChatResponse response = request.call().chatResponse();
            populateResponse(response, exchange);
        }
    }

    /**
     * Build the list of default advisors based on endpoint configuration.
     * <p>
     * Advisors are Spring AI components that intercept and modify chat requests and responses. They enable features
     * like logging, memory, RAG (Retrieval-Augmented Generation), content filtering, and custom processing. Advisors
     * are executed in the order they are added to the list.
     * </p>
     * <p>
     * The following advisors may be added based on configuration:
     * </p>
     * <ol>
     * <li><strong>SimpleLoggerAdvisor</strong> - Always added first. Logs chat requests and responses for debugging and
     * monitoring purposes.</li>
     * <li><strong>SafeGuardAdvisor</strong> - Added if sensitive words are configured. Blocks requests or responses
     * containing configured sensitive words and returns a custom failure response.</li>
     * <li><strong>MessageChatMemoryAdvisor</strong> - Added if ChatMemory is configured. Maintains conversation history
     * using an in-memory or persistent chat memory store, enabling contextual multi-turn conversations.</li>
     * <li><strong>VectorStoreChatMemoryAdvisor</strong> - Added if chatMemoryVectorStore is configured (alternative to
     * MessageChatMemoryAdvisor). Stores conversation history in a vector store with automatic conversation isolation by
     * ID.</li>
     * <li><strong>QuestionAnswerAdvisor</strong> - Added if VectorStore is configured. Implements RAG by automatically
     * retrieving relevant context from the vector store and augmenting the user's question with it.</li>
     * <li><strong>Custom Advisors</strong> - Any user-provided advisors from the endpoint configuration are added
     * last.</li>
     * </ol>
     * <p>
     * <strong>Note:</strong> If both ChatMemory and chatMemoryVectorStore are configured, only ChatMemory will be used
     * and a warning will be logged.
     * </p>
     *
     * @return the list of configured advisors to be applied to the ChatClient
     */
    private List<Advisor> buildDefaultAdvisors() {
        List<Advisor> advisors = new ArrayList<>();

        // Always add SimpleLoggerAdvisor first
        advisors.add(new SimpleLoggerAdvisor());

        // Add SafeGuardAdvisor if configured
        SafeGuardAdvisor safeguardAdvisor = buildSafeguardAdvisor(
                getEndpoint().getConfiguration().getSafeguardSensitiveWords(),
                getEndpoint().getConfiguration().getSafeguardFailureResponse(),
                getEndpoint().getConfiguration().getSafeguardOrder(),
                false);
        if (safeguardAdvisor != null) {
            advisors.add(safeguardAdvisor);
        }

        // Add ChatMemory advisor if configured
        ChatMemory chatMemory = getEndpoint().getConfiguration().getChatMemory();
        VectorStore chatMemoryVectorStore = getEndpoint().getConfiguration().getChatMemoryVectorStore();

        if (chatMemory != null && chatMemoryVectorStore != null) {
            LOG.warn("Both chatMemory and chatMemoryVectorStore are configured. Using MessageChatMemoryAdvisor (chatMemory). " +
                     "Configure only one memory type.");
        }

        if (chatMemory != null) {
            advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
            LOG.debug("MessageChatMemoryAdvisor enabled");
        } else if (chatMemoryVectorStore != null) {
            // Configure VectorStoreChatMemoryAdvisor with conversation isolation
            // The conversationId parameter enables automatic filtering by conversation ID
            advisors.add(VectorStoreChatMemoryAdvisor.builder(chatMemoryVectorStore)
                    .conversationId(ChatMemory.CONVERSATION_ID)
                    .defaultTopK(getEndpoint().getConfiguration().getTopK())
                    .build());
            LOG.debug("VectorStoreChatMemoryAdvisor enabled with conversation isolation and topK={}",
                    getEndpoint().getConfiguration().getTopK());
        }

        // Add QuestionAnswerAdvisor if VectorStore is configured
        VectorStore vectorStore = getEndpoint().getConfiguration().getVectorStore();
        if (vectorStore != null) {
            advisors.add(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder()
                            .topK(getEndpoint().getConfiguration().getTopK())
                            .similarityThreshold(getEndpoint().getConfiguration().getSimilarityThreshold())
                            .build())
                    .build());
            LOG.debug("QuestionAnswerAdvisor enabled with topK={}, similarityThreshold={}",
                    getEndpoint().getConfiguration().getTopK(),
                    getEndpoint().getConfiguration().getSimilarityThreshold());
        }

        // Add custom advisors if configured
        List<Advisor> customAdvisors = getEndpoint().getConfiguration().getAdvisors();
        if (customAdvisors != null && !customAdvisors.isEmpty()) {
            advisors.addAll(customAdvisors);
            LOG.debug("Added {} custom advisors", customAdvisors.size());
        }

        LOG.debug("Built {} total advisors", advisors.size());
        return advisors;
    }
}
