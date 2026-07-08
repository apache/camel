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
package org.apache.camel.component.a2a.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

/**
 * Utility for encoding and decoding Server-Sent Events (SSE) frames in A2A protocol.
 * <p>
 * SSE format: one or more {@code data:} lines followed by a blank line.
 */
public final class SseCodec {

    private static final ObjectMapper MAPPER = A2AJsonMapper.instance();
    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String SSE_DATA_FIELD = "data:";
    private static final String SSE_FRAME_TERMINATOR = "\n\n";

    private SseCodec() {
        // Utility class
    }

    /**
     * Encode a {@link StreamResponse} to an SSE frame.
     *
     * @param  response the response to encode
     * @return          SSE frame: {@code data: {json}\n\n}
     */
    public static String encode(StreamResponse response) {
        try {
            response.validate();
            String json = MAPPER.writeValueAsString(response);
            return SSE_DATA_PREFIX + json + SSE_FRAME_TERMINATOR;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to encode StreamResponse to SSE frame", e);
        }
    }

    /**
     * Decode a single SSE frame to a {@link StreamResponse}.
     *
     * @param  frame SSE frame (with {@code data: } prefix and {@code \n\n} terminator)
     * @return       the decoded response
     */
    public static StreamResponse decode(String frame) {
        List<StreamResponse> responses = decodeAll(frame);
        if (responses.isEmpty()) {
            throw new RuntimeCamelException("Failed to decode SSE frame to StreamResponse");
        }
        return responses.get(0);
    }

    /**
     * Decode already-concatenated SSE data field content.
     *
     * @param  data event data with multiline {@code data:} fields already joined by newline
     * @return      the decoded response
     */
    public static StreamResponse decodeData(String data) {
        try {
            StreamResponse response = MAPPER.readValue(data, StreamResponse.class);
            response.validate();
            return response;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to decode SSE frame to StreamResponse", e);
        }
    }

    /**
     * Decode multiple concatenated SSE frames.
     *
     * @param  raw raw SSE stream with multiple frames separated by {@code \n\n}
     * @return     list of decoded responses
     */
    public static List<StreamResponse> decodeAll(String raw) {
        List<StreamResponse> responses = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(raw))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    addEvent(responses, data);
                } else if (line.startsWith(SSE_DATA_FIELD)) {
                    appendData(data, line);
                }
            }
            addEvent(responses, data);
            return responses;
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to decode SSE stream", e);
        }
    }

    private static void addEvent(List<StreamResponse> responses, StringBuilder data) {
        if (data.length() == 0) {
            return;
        }
        try {
            responses.add(decodeData(data.toString()));
            data.setLength(0);
        } catch (RuntimeCamelException e) {
            throw e;
        }
    }

    private static void appendData(StringBuilder data, String line) {
        String value = line.substring(SSE_DATA_FIELD.length());
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }
        if (data.length() > 0) {
            data.append('\n');
        }
        data.append(value);
    }
}
