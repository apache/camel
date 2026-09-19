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
import java.util.Base64;
import java.util.List;

import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;

/**
 * Builds chat-completion user messages from text, image, PDF and audio bodies.
 */
final class OpenAIChatCompletionMultimodalSupport {

    private OpenAIChatCompletionMultimodalSupport() {
    }

    static ChatCompletionMessageParam buildUserMessage(Message in, String userPrompt) throws Exception {
        Object body = in.getBody();

        if (body instanceof WrappedFile || body instanceof File || body instanceof Path) {
            return buildFileMessage(in, userPrompt);
        }
        if (body instanceof byte[] || body instanceof InputStream) {
            return buildBinaryMessage(in, userPrompt);
        }
        return buildTextMessage(in, userPrompt);
    }

    private static ChatCompletionMessageParam buildTextMessage(Message in, String userPrompt) {
        String prompt = userPrompt != null ? userPrompt : in.getBody(String.class);
        if (prompt == null || prompt.trim().isEmpty()) {
            return null;
        }
        return createTextMessage(prompt);
    }

    private static ChatCompletionMessageParam buildFileMessage(Message in, String userPrompt) throws Exception {
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
            return createTextMessage(prompt);
        }
        if (MimeTypeHelper.isImage(mime)) {
            byte[] image = inputFile != null ? Files.readAllBytes(inputFile.toPath()) : readBodyBytes(in);
            return createImageMessage(image, mime, userPrompt);
        }
        if (MimeTypeHelper.isPdf(mime)) {
            byte[] document = inputFile != null ? Files.readAllBytes(inputFile.toPath()) : readBodyBytes(in);
            return createFileMessage(document, mime, fileName(in, inputFile), userPrompt);
        }
        if (MimeTypeHelper.isAudio(mime)) {
            byte[] audio = inputFile != null ? Files.readAllBytes(inputFile.toPath()) : readBodyBytes(in);
            return createAudioMessage(audio, mime, userPrompt);
        }
        throw unsupportedMimeType(mime,
                inputFile != null ? inputFile.getName() : in.getHeader(Exchange.FILE_NAME, String.class));
    }

    private static ChatCompletionMessageParam buildBinaryMessage(Message in, String userPrompt) throws Exception {
        String mime = MimeTypeHelper.resolveForBinary(in);
        if (MimeTypeHelper.isImage(mime)) {
            return createImageMessage(readBodyBytes(in), mime, userPrompt);
        }
        if (MimeTypeHelper.isPdf(mime)) {
            return createFileMessage(readBodyBytes(in), mime, fileName(in, null), userPrompt);
        }
        if (MimeTypeHelper.isAudio(mime)) {
            return createAudioMessage(readBodyBytes(in), mime, userPrompt);
        }
        return buildTextMessage(in, userPrompt);
    }

    private static ChatCompletionMessageParam createTextMessage(String prompt) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                        .build());
    }

    private static ChatCompletionMessageParam createImageMessage(byte[] image, String mime, String userPrompt) {
        if (userPrompt == null || userPrompt.isEmpty()) {
            throw new IllegalArgumentException("User message configuration must be set when using an image body");
        }
        return createMultimodalMessage(userPrompt, createImageContentPart(image, mime));
    }

    private static ChatCompletionMessageParam createFileMessage(
            byte[] document, String mime, String fileName, String userPrompt) {
        if (userPrompt == null || userPrompt.isEmpty()) {
            throw new IllegalArgumentException("User message configuration must be set when using a PDF body");
        }
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(document);
        ChatCompletionContentPart filePart = ChatCompletionContentPart.ofFile(
                ChatCompletionContentPart.File.builder()
                        .file(ChatCompletionContentPart.File.FileObject.builder()
                                .fileData(dataUrl)
                                .filename(fileName)
                                .build())
                        .build());
        return createMultimodalMessage(userPrompt, filePart);
    }

    private static ChatCompletionMessageParam createAudioMessage(byte[] audio, String mime, String userPrompt) {
        if (userPrompt == null || userPrompt.isEmpty()) {
            throw new IllegalArgumentException("User message configuration must be set when using an audio body");
        }
        ChatCompletionContentPartInputAudio.InputAudio.Format format = MimeTypeHelper.audioInputFormat(mime);
        if (format == null) {
            throw new IllegalArgumentException(
                    "Unsupported audio MIME type for chat-completion input_audio: " + mime
                                               + ". Supported formats are wav and mp3.");
        }
        String base64Audio = Base64.getEncoder().encodeToString(audio);
        ChatCompletionContentPart audioPart = ChatCompletionContentPart.ofInputAudio(
                ChatCompletionContentPartInputAudio.builder()
                        .inputAudio(ChatCompletionContentPartInputAudio.InputAudio.builder()
                                .data(base64Audio)
                                .format(format)
                                .build())
                        .build());
        return createMultimodalMessage(userPrompt, audioPart);
    }

    private static ChatCompletionMessageParam createMultimodalMessage(
            String userPrompt, ChatCompletionContentPart mediaPart) {
        ChatCompletionContentPart textPart = ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder().text(userPrompt).build());
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                                List.of(textPart, mediaPart)))
                        .build());
    }

    private static ChatCompletionContentPart createImageContentPart(byte[] image, String mime) {
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
        return ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                        .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                .url(dataUrl)
                                .build())
                        .build());
    }

    private static String fileName(Message in, File inputFile) {
        if (inputFile != null) {
            return inputFile.getName();
        }
        String fileName = in.getHeader(Exchange.FILE_NAME_ONLY, String.class);
        if (ObjectHelper.isEmpty(fileName)) {
            fileName = in.getHeader(Exchange.FILE_NAME, String.class);
        }
        return ObjectHelper.isNotEmpty(fileName) ? fileName : "document.pdf";
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
                "Only text, image, PDF and audio files are supported. Detected MIME type: " + mime
                                            + (fileName != null ? " for file: " + fileName : "")
                                            + ". Set the " + OpenAIConstants.MEDIA_TYPE
                                            + " header to override the MIME type detection");
    }
}
