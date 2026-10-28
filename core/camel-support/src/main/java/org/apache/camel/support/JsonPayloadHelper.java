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
package org.apache.camel.support;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;

/**
 * Helper for the JSON data formats: a body that already is the JSON text is written as it is when marshalling
 * (CAMEL-25329). A file, a stream or bytes are the serialized form of a payload, never an object to serialize, and a
 * String holding a JSON object or array is that same text; serializing them would fail, give Base64 or a number array,
 * or encode the text as one JSON string. Any other String, and every object, is left to the data format.
 */
public final class JsonPayloadHelper {

    private JsonPayloadHelper() {
    }

    /**
     * Writes the body as it is when it already is the JSON text.
     *
     * @param  exchange  the exchange
     * @param  body      the body to marshal
     * @param  stream    where to write it
     * @return           the number of bytes written, or -1 when the body is not already JSON (the data format marshals
     *                   it as usual)
     * @throws Exception when the body cannot be read
     */
    public static long writeIfAlreadyJson(Exchange exchange, Object body, OutputStream stream) throws Exception {
        if (body instanceof byte[] bytes) {
            stream.write(bytes);
            return bytes.length;
        } else if (body instanceof StreamCache cache) {
            cache.writeTo(stream);
            return cache.length();
        } else if (body instanceof InputStream is) {
            return is.transferTo(stream);
        } else if (body instanceof WrappedFile<?> wf) {
            // the file itself when there is one: no type conversion, which other converters could take part in
            if (wf.getFile() instanceof File file) {
                return Files.copy(file.toPath(), stream);
            }
            try (InputStream is
                    = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body)) {
                return is.transferTo(stream);
            }
        } else if (body instanceof String text && isJsonText(text)) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            stream.write(bytes);
            return bytes.length;
        }
        return -1;
    }

    /** Whether the text is a JSON object or array, by its first and last non-blank character; it is not parsed. */
    public static boolean isJsonText(String text) {
        if (text == null) {
            return false;
        }
        String t = text.strip();
        return t.length() >= 2 && (t.startsWith("{") && t.endsWith("}") || t.startsWith("[") && t.endsWith("]"));
    }
}
