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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;

/**
 * Builds Responses API {@code input} from Camel exchange message bodies (text and image), aligned with chat-completion
 * ergonomics.
 */
final class OpenAIResponsesInputBuilder {

    private OpenAIResponsesInputBuilder() {
    }

    static InputSpec buildInput(Message in, OpenAIConfiguration config) throws Exception {
        Object body = in.getBody();
        String userPrompt = in.getHeader(OpenAIConstants.USER_MESSAGE, String.class);
        if ((userPrompt == null || userPrompt.isEmpty()) && ObjectHelper.isNotEmpty(config.getUserMessage())) {
            userPrompt = config.getUserMessage();
        }

        if (body instanceof WrappedFile || body instanceof File || body instanceof Path) {
            return buildFromFile(in, userPrompt, config);
        }
        if (body instanceof byte[] || body instanceof InputStream) {
            return buildFromBinary(in, userPrompt, config);
        }
        return buildFromText(in, userPrompt, config);
    }

    private static InputSpec buildFromText(Message in, String userPrompt, OpenAIConfiguration config) {
        String prompt = userPrompt != null ? userPrompt : in.getBody(String.class);
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "No input provided. Set the message body or configure userMessage / CamelOpenAIUserMessage");
        }
        return InputSpec.plainText(prompt);
    }

    private static InputSpec buildFromFile(Message in, String userPrompt, OpenAIConfiguration config) throws Exception {
        Object body = in.getBody();
        File inputFile = null;
        if (body instanceof WrappedFile<?> wrappedFile && wrappedFile.getFile() instanceof File file) {
            inputFile = file;
        } else if (body instanceof File file) {
            inputFile = file;
        } else if (body instanceof Path path) {
            inputFile = path.toFile();
        }

        String mime = inputFile != null
                ? MimeTypeHelper.resolveForFile(in, inputFile) : MimeTypeHelper.resolveForBinary(in);

        if (MimeTypeHelper.isText(mime)) {
            String prompt = userPrompt;
            if (prompt == null || prompt.isEmpty()) {
                prompt = in.getBody(String.class);
            }
            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException(
                        "File content or user message configuration must contain the prompt text");
            }
            return InputSpec.plainText(prompt);
        }
        if (MimeTypeHelper.isImage(mime)) {
            byte[] image = inputFile != null ? Files.readAllBytes(inputFile.toPath()) : readBodyBytes(in);
            return buildImageInput(image, mime, userPrompt);
        }
        throw unsupportedMimeType(mime,
                inputFile != null ? inputFile.getName() : in.getHeader(Exchange.FILE_NAME, String.class));
    }

    private static InputSpec buildFromBinary(Message in, String userPrompt, OpenAIConfiguration config)
            throws Exception {
        String mime = MimeTypeHelper.resolveForBinary(in);
        if (MimeTypeHelper.isImage(mime)) {
            return buildImageInput(readBodyBytes(in), mime, userPrompt);
        }
        return buildFromText(in, userPrompt, config);
    }

    private static InputSpec buildImageInput(byte[] image, String mime, String userPrompt) {
        if (userPrompt == null || userPrompt.isEmpty()) {
            throw new IllegalArgumentException("User message must be set when using an image body");
        }
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
        List<ResponseInputItem> items = new ArrayList<>();
        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addContent(ResponseInputContent.ofInputText(
                        ResponseInputText.builder().text(userPrompt).build()))
                .addContent(ResponseInputContent.ofInputImage(
                        ResponseInputImage.builder().imageUrl(dataUrl).build()))
                .build();
        items.add(ResponseInputItem.ofMessage(message));
        return InputSpec.structured(items);
    }

    private static byte[] readBodyBytes(Message in) throws IOException {
        Object body = in.getBody();
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        InputStream is = in.getBody(InputStream.class);
        if (is == null) {
            throw new IllegalArgumentException(
                    "Cannot read message body as InputStream: " + (body != null ? body.getClass().getName() : "null"));
        }
        try (is) {
            return is.readAllBytes();
        }
    }

    private static IllegalArgumentException unsupportedMimeType(String mime, String fileName) {
        return new IllegalArgumentException(
                "Only text and image files are supported. Detected MIME type: " + mime
                                            + (fileName != null ? " for file: " + fileName : "")
                                            + ". Set the " + OpenAIConstants.MEDIA_TYPE
                                            + " header to override MIME type detection");
    }

    record InputSpec(String plainText, List<ResponseInputItem> structuredItems) {

        static InputSpec plainText(String text) {
            return new InputSpec(text, null);
        }

        static InputSpec structured(List<ResponseInputItem> items) {
            return new InputSpec(null, items);
        }

        boolean isPlainText() {
            return plainText != null;
        }
    }
}
