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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.openai.core.MultipartField;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;

/**
 * Shared helpers for the audio operations (transcription and translation) that upload an audio file as multipart input.
 */
final class OpenAIAudioSupport {

    private OpenAIAudioSupport() {
    }

    /**
     * Resolves the audio input from the message body and applies it to the request builder. The supported body types
     * are {@link File}, {@link Path}, {@code byte[]} and {@link InputStream} (with a fallback to type conversion).
     *
     * @param in                the incoming message carrying the audio body
     * @param pathConsumer      applies a file {@link Path} to the request builder
     * @param multipartConsumer applies a streamed {@link MultipartField} to the request builder
     */
    static void applyFileInput(
            Message in, Consumer<Path> pathConsumer, Consumer<MultipartField<InputStream>> multipartConsumer) {
        Object body = in.getBody();

        if (body instanceof WrappedFile<?> wrappedFile) {
            body = wrappedFile.getFile();
        }

        if (body instanceof File file) {
            pathConsumer.accept(file.toPath());
        } else if (body instanceof Path path) {
            pathConsumer.accept(path);
        } else if (body instanceof byte[] bytes) {
            multipartConsumer.accept(multipartWithFilename(new ByteArrayInputStream(bytes), resolveFilename(in)));
        } else if (body instanceof InputStream inputStream) {
            multipartConsumer.accept(multipartWithFilename(inputStream, resolveFilename(in)));
        } else {
            InputStream converted = in.getBody(InputStream.class);
            if (converted == null) {
                throw new IllegalArgumentException(
                        "Unsupported body type for audio operation: "
                                                   + (body != null ? body.getClass().getName() : "null")
                                                   + ". Supported: File, Path, InputStream, byte[]");
            }
            multipartConsumer.accept(multipartWithFilename(converted, resolveFilename(in)));
        }
    }

    private static String resolveFilename(Message in) {
        String filename = in.getHeader(Exchange.FILE_NAME_ONLY, String.class);
        if (ObjectHelper.isNotEmpty(filename)) {
            return filename;
        }
        return "audio";
    }

    private static MultipartField<InputStream> multipartWithFilename(InputStream stream, String filename) {
        return MultipartField.<InputStream> builder()
                .value(stream)
                .filename(filename)
                .build();
    }

    /**
     * Resolves a parameter value from a message header, falling back to a configured default.
     *
     * @param  message      the incoming message
     * @param  headerName   the header to read the override from
     * @param  defaultValue the configured default value
     * @param  type         the expected value type
     * @return              the header value when present and not empty, otherwise the default
     */
    static <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        if (headerName != null) {
            T headerValue = message.getHeader(headerName, type);
            return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
        }
        return defaultValue;
    }
}
