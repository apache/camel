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
package org.apache.camel.component.a2a;

import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Converter;
import org.apache.camel.component.a2a.model.DataPart;
import org.apache.camel.component.a2a.model.FilePart;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.Part;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

/**
 * Type converters for A2A model objects. Enables {@code convertBodyTo(String.class)} and {@code ${bodyAs(String)}} to
 * extract text content from Task and Message objects.
 * <p>
 * Also provides type-specific extraction methods used by the Simple functions ({@code a2aText()}, {@code a2aData()},
 * {@code a2aFile()}).
 */
@Converter(generateLoader = true)
public final class A2ATypeConverters {

    private static final ObjectMapper MAPPER = A2AJsonMapper.instance();

    private A2ATypeConverters() {
    }

    /**
     * Extracts content from all parts in a Message, handling TextPart, DataPart, and FilePart.
     */
    @Converter
    public static String messageToString(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            if (part instanceof TextPart tp) {
                sb.append(tp.text());
            } else if (part instanceof DataPart dp) {
                try {
                    sb.append(MAPPER.writeValueAsString(dp.data()));
                } catch (Exception e) {
                    sb.append(String.valueOf(dp.data()));
                }
            } else if (part instanceof FilePart fp) {
                if (fp.url() != null) {
                    sb.append(fp.url());
                } else if (fp.raw() != null) {
                    sb.append("[binary ").append(fp.raw().length()).append(" chars base64]");
                }
            }
        }
        return sb.toString();
    }

    @Converter
    public static String taskToString(Task task) {
        Message latest = task.latest();
        return latest != null ? messageToString(latest) : "";
    }

    // --- Type-specific extraction (used by Simple functions) ---

    public static String extractText(Message message) {
        return extractParts(message, TextPart.class, tp -> tp.text());
    }

    public static String extractText(Task task) {
        Message latest = task != null ? task.latest() : null;
        return latest != null ? extractText(latest) : "";
    }

    public static String extractData(Message message) {
        return extractParts(message, DataPart.class, dp -> {
            try {
                return MAPPER.writeValueAsString(dp.data());
            } catch (Exception e) {
                return String.valueOf(dp.data());
            }
        });
    }

    public static String extractData(Task task) {
        Message latest = task != null ? task.latest() : null;
        return latest != null ? extractData(latest) : "";
    }

    public static String extractFile(Message message) {
        return extractParts(message, FilePart.class, fp -> {
            if (fp.url() != null) {
                return fp.url();
            } else if (fp.raw() != null) {
                return "[binary " + fp.raw().length() + " chars base64]";
            }
            return "";
        });
    }

    public static String extractFile(Task task) {
        Message latest = task != null ? task.latest() : null;
        return latest != null ? extractFile(latest) : "";
    }

    @SuppressWarnings("unchecked")
    private static <
            P> String extractParts(Message message, Class<P> partType, Function<P, String> renderer) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (partType.isInstance(part)) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(renderer.apply((P) part));
            }
        }
        return sb.toString();
    }
}
