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
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.util.IOHelper;

/**
 * The requests of a batch collected on disk by {@link OpenAIBatchAggregationStrategy}, one {@code custom_id} and value
 * per line, so a batch of any size is aggregated with constant memory. The {@code batch} operation reads it back one
 * entry at a time while uploading, exactly as it reads a {@link Map} body, and deletes the file once the batch is
 * created.
 * <p>
 * Only the path is held in memory, and it is serializable, so the aggregation works with a persistent
 * {@code AggregationRepository}.
 */
public final class OpenAIBatchSpool implements Iterable<Map.Entry<String, Object>>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> LINE = new TypeReference<>() {
    };

    private final File file;

    OpenAIBatchSpool(File file) {
        this.file = file;
    }

    static OpenAIBatchSpool create(Path directory) throws IOException {
        return new OpenAIBatchSpool(Files.createTempFile(directory, "camel-openai-batch-", ".jsonl").toFile());
    }

    public File getFile() {
        return file;
    }

    /**
     * Opens the spool for appending. The writer is kept by the strategy for the life of the aggregation, so a message
     * costs one write rather than an open and close of the file, and is flushed after every line so the file is
     * complete at any point for a persistent repository that recovers the aggregation.
     */
    BufferedWriter open() throws IOException {
        return Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    static void append(BufferedWriter writer, String customId, Object value) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("custom_id", customId);
        line.put("value", value);
        writer.write(OBJECT_MAPPER.writeValueAsString(line));
        writer.write('\n');
        writer.flush();
    }

    void delete() throws IOException {
        Files.deleteIfExists(file.toPath());
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        try {
            return new Lines(Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "OpenAIBatchSpool[" + file + "]";
    }

    private static final class Lines implements Iterator<Map.Entry<String, Object>>, Closeable {
        private final BufferedReader reader;
        private String next;
        private boolean closed;

        Lines(BufferedReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean hasNext() {
            if (next == null && !closed) {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            next = line;
                            return true;
                        }
                    }
                } catch (IOException e) {
                    close();
                    throw new UncheckedIOException(e);
                }
                close();
            }
            return next != null;
        }

        @Override
        public Map.Entry<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String line = next;
            next = null;
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(line, LINE);
                return new AbstractMap.SimpleImmutableEntry<>(
                        String.valueOf(parsed.get("custom_id")),
                        parsed.get("value"));
            } catch (IOException e) {
                close();
                throw new UncheckedIOException("Invalid batch spool line: " + line, e);
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                IOHelper.close(reader);
            }
        }
    }
}
