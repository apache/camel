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

package org.apache.camel.component.telegram.service;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The body of a request. It is written to a buffer that starts at the buffer size and grows to the size of the content,
 * so media of any size can be sent and a small request does not allocate more than it needs.
 */
public class TelegramBodyPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramBodyPublisher.class);

    private final Set<TelegramBodyPart> bodyParts = new LinkedHashSet<>();
    private final String boundary = RandomStringUtils.secure().nextAlphanumeric(12);
    private final int bufferSize;

    public TelegramBodyPublisher(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public interface TelegramBodyPart {
        void serialize(BodyBuffer buffer, String separator);
    }

    /**
     * A byte buffer that grows as needed, and whose content is published without a copy.
     */
    public static final class BodyBuffer extends ByteArrayOutputStream {

        public BodyBuffer(int initialSize) {
            super(initialSize);
        }

        /**
         * Writes the text in UTF-8.
         */
        public void put(String text) {
            put(text, StandardCharsets.UTF_8);
        }

        public void put(String text, Charset charset) {
            writeBytes(text.getBytes(charset));
        }

        public void put(byte[] bytes) {
            writeBytes(bytes);
        }

        HttpRequest.BodyPublisher toPublisher() {
            return HttpRequest.BodyPublishers.ofByteArray(buf, 0, count);
        }
    }

    public static class SingleBodyPart implements TelegramBodyPart {
        private final String body;

        public SingleBodyPart(String body) {
            this.body = body;
        }

        @Override
        public void serialize(BodyBuffer buffer, String boundary) {
            buffer.put(body);
        }

    }

    public static class MultilineBodyPart<T> implements TelegramBodyPart {
        private final String contentType;
        private final Map<String, Object> headers = new LinkedHashMap<>();
        private final T body;
        private final String charset;

        public MultilineBodyPart(String name, T body, String contentType) {
            this(name, body, contentType, StandardCharsets.UTF_8.name());
        }

        public MultilineBodyPart(String name, T body, String contentType, String charset) {
            this.body = body;
            this.contentType = contentType;
            this.charset = charset;

            addHeader("name", name);
        }

        public void addHeader(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public void serialize(BodyBuffer buffer, String boundary) {
            String partHeader = "--" + boundary + "\r\n";
            buffer.put(partHeader);

            String contentDisposition = "Content-Disposition: form-data; ";

            // this creates the key-pair part of the content disposition (i.e.: name="myName"; file="myFile.doc")
            contentDisposition += headers.entrySet()
                    .stream()
                    .map(e -> e.getKey().toLowerCase() + "=\"" + e.getValue().toString() + "\"")
                    .collect(Collectors.joining("; ")) + "\r\n";
            buffer.put(contentDisposition);

            String contentTypePart = "Content-Type: " + contentType;
            if (charset != null) {
                contentTypePart += "; charset=" + charset;
            }
            contentTypePart += "\r\n\r\n";

            buffer.put(contentTypePart);

            if (body instanceof byte[] bytes) {
                buffer.put(bytes);
            } else if (body != null) {
                // a text, or a value such as a number or a boolean, in the charset the part declares
                buffer.put(body.toString(), charset != null ? Charset.forName(charset) : StandardCharsets.UTF_8);
            }

            buffer.put("\r\n");
        }

        public static void serializeEnd(BodyBuffer buffer, String separator) {
            String partHeader = "--" + separator + "--\r\n";
            buffer.put(partHeader);
        }
    }

    public void addBodyPart(TelegramBodyPart bodyPart) {
        bodyParts.add(bodyPart);
    }

    public HttpRequest.BodyPublisher newPublisher() {
        BodyBuffer buffer = new BodyBuffer(bufferSize);
        serialize(bodyParts, buffer, boundary);
        LOG.debug("The request body has {} bytes", buffer.size());

        return buffer.toPublisher();
    }

    static void serialize(Set<TelegramBodyPart> bodyParts, BodyBuffer buffer, String separator) {
        try {
            boolean isMultiBody = false;

            for (TelegramBodyPart bodyPart : bodyParts) {
                bodyPart.serialize(buffer, separator);
                if (bodyPart instanceof MultilineBodyPart) {
                    isMultiBody = true;
                }
            }

            if (isMultiBody) {
                MultilineBodyPart.serializeEnd(buffer, separator);
            }

        } finally {
            bodyParts.clear();
        }
    }

    Set<TelegramBodyPart> getBodyParts() {
        return bodyParts;
    }

    String getBoundary() {
        return boundary;
    }
}
