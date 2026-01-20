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
package org.apache.camel.component.langchain4j.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.langchain4j.agent.api.Headers.MEDIA_TYPE;
import static org.apache.camel.component.langchain4j.agent.api.Headers.MEMORY_ID;
import static org.apache.camel.component.langchain4j.agent.api.Headers.SYSTEM_MESSAGE;
import static org.apache.camel.component.langchain4j.agent.api.Headers.USER_MESSAGE;

/**
 * Type converters for the LangChain4j Agent component.
 * <p>
 * Provides automatic conversion from various input types to {@link AiAgentBody} with appropriate LangChain4j
 * {@link Content} types based on the MIME type.
 * </p>
 * <p>
 * Supported input types:
 * </p>
 * <ul>
 * <li>{@link WrappedFile} - from file, ftp, sftp components</li>
 * <li>{@code byte[]} - from aws2-s3, azure-storage-blob, and other cloud components</li>
 * <li>{@link InputStream} - from various streaming components</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> For {@code byte[]} and {@link InputStream}, the MIME type must be provided via the
 * {@code CamelLangChain4jAgentMediaType} header or a component-specific content type header.
 * </p>
 */
@Converter(generateLoader = true)
public final class LangChain4jAgentConverter {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAgentConverter.class);

    private LangChain4jAgentConverter() {
    }

    /**
     * Converts a {@link WrappedFile} to an {@link AiAgentBody} with the appropriate {@link Content} type.
     * <p>
     * The conversion uses the following headers from the exchange:
     * </p>
     * <ul>
     * <li>{@code CamelLangChain4jAgentUserMessage} - The text message to accompany the file content</li>
     * <li>{@code CamelLangChain4jAgentSystemMessage} - Optional system message for the AI agent</li>
     * <li>{@code CamelLangChain4jAgentMemoryId} - Optional memory ID for stateful conversations</li>
     * <li>{@code CamelLangChain4jAgentMediaType} - Optional MIME type override (highest priority)</li>
     * <li>{@code Exchange.FILE_CONTENT_TYPE} - MIME type from file components (second priority)</li>
     * </ul>
     * <p>
     * If no MIME type header is found, the type is auto-detected from the file extension.
     * </p>
     *
     * @param  wrappedFile              the wrapped file from file-based components (file, ftp, sftp, etc.)
     * @param  exchange                 the Camel exchange containing headers
     * @return                          an AiAgentBody with the appropriate Content type
     * @throws IllegalArgumentException if the file cannot be read or the MIME type is unsupported
     */
    @Converter
    public static AiAgentBody<?> toAiAgentBody(WrappedFile<?> wrappedFile, Exchange exchange) {
        Object fileObj = wrappedFile.getFile();
        if (fileObj == null) {
            throw new IllegalArgumentException("WrappedFile contains null file");
        }
        if (!(fileObj instanceof File)) {
            throw new IllegalArgumentException(
                    "WrappedFile must contain a java.io.File instance, got: " + fileObj.getClass().getName());
        }

        File file = (File) fileObj;
        String mimeType = detectMimeType(file, exchange);
        byte[] fileData = readFileBytes(file);
        Content content = createContent(fileData, mimeType);

        String userMessage = exchange.getIn().getHeader(USER_MESSAGE, String.class);
        String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
        Object memoryId = exchange.getIn().getHeader(MEMORY_ID);

        AiAgentBody<Content> body = new AiAgentBody<>();
        body.setUserMessage(userMessage != null ? userMessage : "");
        body.setSystemMessage(systemMessage);
        body.setMemoryId(memoryId);
        body.setContent(content);

        return body;
    }

    /**
     * Converts a {@code byte[]} to an {@link AiAgentBody} with the appropriate {@link Content} type.
     * <p>
     * This converter is useful for cloud storage components like aws2-s3, azure-storage-blob, etc. that return file
     * content as byte arrays.
     * </p>
     * <p>
     * <strong>Important:</strong> The MIME type must be provided via headers since it cannot be auto-detected from byte
     * arrays. Supported headers (in priority order):
     * </p>
     * <ul>
     * <li>{@code CamelLangChain4jAgentMediaType} header (highest priority)</li>
     * <li>{@code CamelAwsS3ContentType} header (from AWS S3)</li>
     * <li>{@code CamelAzureStorageBlobContentType} header (from Azure Blob Storage)</li>
     * <li>{@code CamelAzureStorageDataLakeContentType} header (from Azure Data Lake Storage)</li>
     * <li>{@code CamelGoogleCloudStorageContentType} header (from Google Cloud Storage)</li>
     * <li>{@code CamelMinioContentType} header (from Minio)</li>
     * <li>{@code CamelIBMCOSContentType} header (from IBM Cloud Object Storage)</li>
     * <li>{@code Content-Type} header</li>
     * <li>{@code CamelFileContentType} header (from file components)</li>
     * </ul>
     *
     * @param  data                     the file content as a byte array
     * @param  exchange                 the Camel exchange containing headers
     * @return                          an AiAgentBody with the appropriate Content type
     * @throws IllegalArgumentException if the MIME type is not provided or is unsupported
     */
    @Converter
    public static AiAgentBody<?> byteArrayToAiAgentBody(byte[] data, Exchange exchange) {
        String mimeType = detectMimeTypeFromHeaders(exchange);
        Content content = createContent(data, mimeType);

        String userMessage = exchange.getIn().getHeader(USER_MESSAGE, String.class);
        String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
        Object memoryId = exchange.getIn().getHeader(MEMORY_ID);

        AiAgentBody<Content> body = new AiAgentBody<>();
        body.setUserMessage(userMessage != null ? userMessage : "");
        body.setSystemMessage(systemMessage);
        body.setMemoryId(memoryId);
        body.setContent(content);

        return body;
    }

    /**
     * Converts an {@link InputStream} to an {@link AiAgentBody} with the appropriate {@link Content} type.
     * <p>
     * This converter is useful for streaming components that return file content as input streams.
     * </p>
     * <p>
     * <strong>Important:</strong> The MIME type must be provided via headers since it cannot be auto-detected from
     * streams.
     * </p>
     *
     * @param  inputStream              the file content as an input stream
     * @param  exchange                 the Camel exchange containing headers
     * @return                          an AiAgentBody with the appropriate Content type
     * @throws IllegalArgumentException if the stream cannot be read or the MIME type is not provided/unsupported
     */
    @Converter
    public static AiAgentBody<?> inputStreamToAiAgentBody(InputStream inputStream, Exchange exchange) {
        try {
            byte[] data = inputStream.readAllBytes();
            return byteArrayToAiAgentBody(data, exchange);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read input stream", e);
        }
    }

    /**
     * Creates the appropriate LangChain4j Content object based on the MIME type.
     */
    static Content createContent(byte[] data, String mimeType) {
        String base64Data = Base64.getEncoder().encodeToString(data);

        if (mimeType.startsWith("image/")) {
            Image image = Image.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            return ImageContent.from(image);
        } else if (mimeType.startsWith("audio/")) {
            Audio audio = Audio.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            return AudioContent.from(audio);
        } else if (mimeType.startsWith("video/")) {
            Video video = Video.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            return VideoContent.from(video);
        } else if ("application/pdf".equals(mimeType)) {
            PdfFile pdfFile = PdfFile.builder()
                    .base64Data(base64Data)
                    .build();
            return PdfFileContent.from(pdfFile);
        } else if (mimeType.startsWith("text/")) {
            return TextContent.from(new String(data));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported MIME type: " + mimeType
                                               + ". Supported types: image/*, audio/*, video/*, application/pdf, text/*");
        }
    }

    /**
     * Detects the MIME type from headers or file extension.
     * <p>
     * Priority:
     * </p>
     * <ol>
     * <li>CamelLangChain4jAgentMediaType header (highest priority)</li>
     * <li>Exchange.FILE_CONTENT_TYPE header (from file components)</li>
     * <li>Auto-detection from file extension</li>
     * </ol>
     */
    private static String detectMimeType(File file, Exchange exchange) {
        // Check agent-specific header first (highest priority)
        String mediaType = exchange.getIn().getHeader(MEDIA_TYPE, String.class);
        if (mediaType != null) {
            return mediaType;
        }

        // Check file component's content type header
        String fileContentType = exchange.getIn().getHeader(Exchange.FILE_CONTENT_TYPE, String.class);
        if (fileContentType != null) {
            return fileContentType;
        }

        // Auto-detect from file extension
        return detectMimeTypeFromExtension(file.getName());
    }

    /**
     * Detects the MIME type from headers only (for byte[] and InputStream where file extension is not available).
     * <p>
     * Priority:
     * </p>
     * <ol>
     * <li>CamelLangChain4jAgentMediaType header (highest priority)</li>
     * <li>Cloud storage component headers (AWS S3, Azure Blob, Google Cloud, Minio, IBM COS)</li>
     * <li>Exchange.CONTENT_TYPE header</li>
     * <li>Exchange.FILE_CONTENT_TYPE header</li>
     * </ol>
     *
     * @throws IllegalArgumentException if no MIME type header is found
     */
    private static String detectMimeTypeFromHeaders(Exchange exchange) {
        // Check agent-specific header first (highest priority)
        String mediaType = exchange.getIn().getHeader(MEDIA_TYPE, String.class);
        if (mediaType != null) {
            return normalizeContentType(mediaType);
        }

        // Cloud storage component content type headers
        String[] cloudContentTypeHeaders = {
                "CamelAwsS3ContentType",                  // AWS S3
                "CamelAzureStorageBlobContentType",       // Azure Blob Storage
                "CamelAzureStorageDataLakeContentType",   // Azure Data Lake Storage
                "CamelGoogleCloudStorageContentType",     // Google Cloud Storage
                "CamelMinioContentType",                  // Minio (S3-compatible)
                "CamelIBMCOSContentType"                  // IBM Cloud Object Storage
        };

        for (String header : cloudContentTypeHeaders) {
            String cloudContentType = exchange.getIn().getHeader(header, String.class);
            if (cloudContentType != null) {
                return normalizeContentType(cloudContentType);
            }
        }

        // Check standard content type header
        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType != null) {
            return normalizeContentType(contentType);
        }

        // Check file component's content type header
        String fileContentType = exchange.getIn().getHeader(Exchange.FILE_CONTENT_TYPE, String.class);
        if (fileContentType != null) {
            return normalizeContentType(fileContentType);
        }

        throw new IllegalArgumentException(
                "MIME type is required for byte[] or InputStream input. "
                                           + "Please set the CamelLangChain4jAgentMediaType header.");
    }

    /**
     * Normalizes a content type by removing charset and other parameters.
     * <p>
     * For example: "text/html; charset=utf-8" becomes "text/html"
     * </p>
     */
    private static String normalizeContentType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return semicolon > 0 ? contentType.substring(0, semicolon).trim() : contentType;
    }

    /**
     * Detects the MIME type from the file extension.
     */
    private static String detectMimeTypeFromExtension(String fileName) {
        String lowerName = fileName.toLowerCase();

        // Image formats
        if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lowerName.endsWith(".tiff") || lowerName.endsWith(".tif")) {
            return "image/tiff";
        } else if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        // Video formats
        else if (lowerName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerName.endsWith(".webm")) {
            return "video/webm";
        } else if (lowerName.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerName.endsWith(".mkv")) {
            return "video/x-matroska";
        } else if (lowerName.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        // Audio formats
        else if (lowerName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lowerName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (lowerName.endsWith(".flac")) {
            return "audio/flac";
        }
        // Document formats
        else if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        }
        // Text formats
        else if (lowerName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerName.endsWith(".csv")) {
            return "text/csv";
        } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "text/html";
        } else if (lowerName.endsWith(".md")) {
            return "text/markdown";
        } else if (lowerName.endsWith(".xml")) {
            return "text/xml";
        } else if (lowerName.endsWith(".json")) {
            return "application/json";
        }

        LOG.warn("Could not detect MIME type from file extension: {}. Please set the CamelLangChain4jAgentMediaType header.",
                fileName);
        throw new IllegalArgumentException(
                "Cannot determine MIME type for file: " + fileName
                                           + ". Please set the CamelLangChain4jAgentMediaType header.");
    }

    /**
     * Reads the file content as a byte array.
     */
    private static byte[] readFileBytes(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }
}
