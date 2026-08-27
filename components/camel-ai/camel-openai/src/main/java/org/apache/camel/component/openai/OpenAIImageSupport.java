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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.openai.models.images.Image;
import com.openai.models.images.ImagesResponse;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the {@code image-generation} and {@code image-edit} operations.
 */
final class OpenAIImageSupport {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIImageSupport.class);

    private static final String DEFAULT_OUTPUT_FORMAT = "png";
    private static final String DEFAULT_IMAGE_MIME_TYPE = "image/png";

    private OpenAIImageSupport() {
    }

    /**
     * Resolves a value from the message header, falling back to the endpoint configuration.
     */
    static <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    static String resolveModel(Message in, OpenAIConfiguration config, String operation) {
        String model = resolveParameter(in, OpenAIConstants.IMAGE_MODEL, config.getImageModel(), String.class);
        if (ObjectHelper.isEmpty(model)) {
            throw new IllegalArgumentException(
                    "Image model must be specified via the imageModel parameter or the "
                                               + OpenAIConstants.IMAGE_MODEL + " header for the " + operation
                                               + " operation. The model determines which of the other image options "
                                               + "are accepted by the API.");
        }
        return model;
    }

    /**
     * Reads the image data carried by the message body of an {@code image-edit} exchange. A {@link List} body is sent
     * as multiple reference images, which the GPT image models accept.
     */
    static List<InputStream> resolveImages(Message in) throws Exception {
        Object body = in.getBody();
        if (body instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("The message body for image-edit must not be an empty list");
            }
            List<InputStream> streams = new ArrayList<>(list.size());
            for (Object item : list) {
                try {
                    streams.add(toInputStream(in, item, "image"));
                } catch (Exception e) {
                    // the caller only closes the streams it was handed, so a partial list must not leak
                    streams.forEach(OpenAIImageSupport::closeQuietly);
                    throw e;
                }
            }
            return streams;
        }
        return List.of(toInputStream(in, body, "image"));
    }

    static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            LOG.debug("Could not close the image input stream", e);
        }
    }

    static InputStream resolveMask(Message in) throws Exception {
        Object mask = in.getHeader(OpenAIConstants.IMAGE_MASK);
        if (mask == null) {
            return null;
        }
        return toInputStream(in, mask, "mask");
    }

    private static InputStream toInputStream(Message in, Object value, String what) throws Exception {
        Object source = value instanceof WrappedFile<?> wrappedFile ? wrappedFile.getFile() : value;

        if (source instanceof File file) {
            return Files.newInputStream(file.toPath());
        } else if (source instanceof Path path) {
            return Files.newInputStream(path);
        } else if (source instanceof byte[] bytes) {
            return new ByteArrayInputStream(bytes);
        } else if (source instanceof InputStream inputStream) {
            return inputStream;
        }

        InputStream converted = in.getExchange().getContext().getTypeConverter()
                .tryConvertTo(InputStream.class, in.getExchange(), source);
        if (converted == null) {
            throw new IllegalArgumentException(
                    "Unsupported " + what + " type for the image-edit operation: "
                                               + (source != null ? source.getClass().getName() : "null")
                                               + ". Supported: File, Path, InputStream, byte[]");
        }
        return converted;
    }

    /**
     * Resolves the MIME type of an uploaded image. The API rejects the upload on the content type of the multipart
     * part, not on the file name, and only accepts {@code image/png}, {@code image/jpeg} and {@code image/webp}, so
     * anything else falls back to PNG rather than being passed through.
     */
    static String resolveImageMimeType(Message in) {
        String mime = MimeTypeHelper.resolveForBinary(in);
        if (mime == null) {
            mime = mimeTypeOfFileName(fileNameOfBody(in.getBody()));
        }
        return isSupportedImageMimeType(mime) ? mime.toLowerCase(Locale.ROOT) : DEFAULT_IMAGE_MIME_TYPE;
    }

    /**
     * Builds the multipart file name for an uploaded image, keeping its extension consistent with the content type.
     */
    static String filenameFor(Message in, String fallbackBaseName, String mimeType) {
        String fileName = in.getHeader(Exchange.FILE_NAME_ONLY, String.class);
        if (ObjectHelper.isEmpty(fileName)) {
            fileName = fileNameOfBody(in.getBody());
        }
        if (ObjectHelper.isNotEmpty(fileName) && fileName.indexOf('.') > 0) {
            return fileName;
        }
        return fallbackBaseName + "." + extensionFor(mimeType);
    }

    private static boolean isSupportedImageMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png", "image/jpeg", "image/webp" -> true;
            default -> false;
        };
    }

    private static String mimeTypeOfFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (name.endsWith(".webp")) {
            return "image/webp";
        } else if (name.endsWith(".png")) {
            return "image/png";
        }
        return null;
    }

    private static String fileNameOfBody(Object body) {
        Object source = body instanceof WrappedFile<?> wrappedFile ? wrappedFile.getFile() : body;
        if (source instanceof File file) {
            return file.getName();
        } else if (source instanceof Path path) {
            return path.getFileName().toString();
        }
        return null;
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return DEFAULT_OUTPUT_FORMAT;
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            default -> DEFAULT_OUTPUT_FORMAT;
        };
    }

    /**
     * Maps the returned images onto the exchange. A single image becomes the body directly and multiple images become a
     * {@link List}, so the common case does not force routes to unwrap a one-element list.
     */
    static void applyResponse(
            Exchange exchange, ImagesResponse response, String outputFormat, boolean storeFullResponse)
            throws CamelExchangeException {

        List<Image> images = response.data().orElse(List.of());
        if (images.isEmpty()) {
            throw new CamelExchangeException("The image response contained no images", exchange);
        }

        List<Object> results = new ArrayList<>(images.size());
        List<String> revisedPrompts = new ArrayList<>(images.size());
        boolean binary = false;

        for (Image image : images) {
            String b64 = image.b64Json().orElse(null);
            if (ObjectHelper.isNotEmpty(b64)) {
                // a MIME decoder is used because some OpenAI-compatible servers wrap the payload in line breaks
                results.add(Base64.getMimeDecoder().decode(b64));
                binary = true;
            } else {
                String url = image.url().orElse(null);
                if (ObjectHelper.isEmpty(url)) {
                    throw new CamelExchangeException(
                            "The image response contained an entry with neither b64_json nor url", exchange);
                }
                results.add(url);
            }
            revisedPrompts.add(image.revisedPrompt().orElse(null));
        }

        if (storeFullResponse) {
            exchange.setProperty(OpenAIConstants.IMAGE_RESPONSE, response);
        }

        Message out = exchange.getMessage();
        out.setBody(results.size() == 1 ? results.get(0) : results);
        out.setHeader(OpenAIConstants.IMAGE_RESULT_COUNT, results.size());

        if (binary) {
            out.setHeader(Exchange.CONTENT_TYPE,
                    contentTypeFor(response.outputFormat().map(ImagesResponse.OutputFormat::asString)
                            .orElse(outputFormat)));
        }

        if (revisedPrompts.stream().anyMatch(ObjectHelper::isNotEmpty)) {
            out.setHeader(OpenAIConstants.IMAGE_REVISED_PROMPTS, revisedPrompts);
            if (revisedPrompts.size() == 1) {
                out.setHeader(OpenAIConstants.IMAGE_REVISED_PROMPT, revisedPrompts.get(0));
            }
        }

        response.usage().ifPresent(usage -> {
            out.setHeader(OpenAIConstants.IMAGE_INPUT_TOKENS, usage.inputTokens());
            out.setHeader(OpenAIConstants.IMAGE_OUTPUT_TOKENS, usage.outputTokens());
            out.setHeader(OpenAIConstants.IMAGE_TOTAL_TOKENS, usage.totalTokens());
        });
    }

    private static String contentTypeFor(String outputFormat) {
        if (ObjectHelper.isEmpty(outputFormat)) {
            return "image/png";
        }
        return switch (outputFormat.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }
}
