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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.http.HttpResponse;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The lines of a batch result file, read from the response one at a time and parsed into maps, so a route splits them
 * with {@code split(body()).streaming()} while only the current line is in memory. The response is closed once the last
 * line has been read, or when the iterator is closed.
 */
public final class OpenAIBatchResultLines implements Iterator<Map<String, Object>>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIBatchResultLines.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> LINE = new TypeReference<>() {
    };

    private final HttpResponse response;
    private final BufferedReader reader;
    private String next;
    private boolean closed;

    OpenAIBatchResultLines(HttpResponse response) {
        this.response = response;
        this.reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
    }

    @Override
    public boolean hasNext() {
        if (next == null && !closed) {
            advance();
        }
        return next != null;
    }

    @Override
    public Map<String, Object> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String line = next;
        next = null;
        try {
            return OBJECT_MAPPER.readValue(line, LINE);
        } catch (IOException e) {
            close();
            throw new UncheckedIOException("Invalid batch result line: " + line, e);
        }
    }

    private void advance() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    next = line;
                    return;
                }
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException(e);
        }
        close();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        IOHelper.close(reader);
        try {
            response.close();
        } catch (Exception e) {
            LOG.debug("Could not close the batch results response", e);
        }
    }
}
