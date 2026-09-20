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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Collects the messages of an aggregation into the input of a batch, so a stream of prompts becomes one
 * {@code openai:batch} call:
 *
 * <pre>
 * from("kafka:tickets")
 *         .aggregate(constant(true), new OpenAIBatchAggregationStrategy())
 *         .completionSize(1000).completionTimeout(60000)
 *         .to("openai:batch?batchEndpoint=/v1/chat/completions&amp;model=gpt-4o-mini");
 * </pre>
 *
 * The body of each message is the value of its request, as for a {@link Map} body of the {@code batch} operation: a
 * String is turned into a request from the endpoint options, a {@code Map} or {@code JsonNode} is the request body. Its
 * {@code custom_id} is the {@code CamelOpenAIBatchCustomId} header when set, and the message id otherwise, so the
 * result lines can be matched back to what produced them.
 * <p>
 * By default the requests are collected in a {@link Map} in memory, which suits batches of a few thousand short
 * requests. {@link #spooled()} writes them to a file instead, one line per message, so a batch of any size is
 * aggregated with constant memory, and a persistent {@code AggregationRepository} stores a path rather than the
 * requests. The {@code batch} operation streams either form and removes the file once the batch is created.
 */
public class OpenAIBatchAggregationStrategy implements AggregationStrategy {

    private final Path spoolDirectory;
    // the writer of each spool being aggregated, closed when its aggregation completes
    private final Map<File, BufferedWriter> writers = new ConcurrentHashMap<>();

    /**
     * Collects the requests in memory.
     */
    public OpenAIBatchAggregationStrategy() {
        this(null);
    }

    /**
     * Collects the requests in a file created in the given directory.
     */
    public OpenAIBatchAggregationStrategy(Path spoolDirectory) {
        this.spoolDirectory = spoolDirectory;
    }

    /**
     * Collects the requests in a file created in the temporary directory of the JVM.
     */
    public static OpenAIBatchAggregationStrategy spooled() {
        return new OpenAIBatchAggregationStrategy(Path.of(System.getProperty("java.io.tmpdir")));
    }

    public boolean isSpooled() {
        return spoolDirectory != null;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Message message = newExchange.getMessage();
        String customId = message.getHeader(OpenAIConstants.BATCH_CUSTOM_ID, message.getMessageId(), String.class);
        Object value = message.getBody();
        if (ObjectHelper.isEmpty(customId) || value == null) {
            throw new IllegalArgumentException(
                    "A message aggregated into a batch needs a custom_id and a body; got custom_id " + customId
                                               + " and body " + value);
        }

        try {
            if (oldExchange == null) {
                if (isSpooled()) {
                    OpenAIBatchSpool spool = OpenAIBatchSpool.create(spoolDirectory);
                    OpenAIBatchSpool.append(writer(spool), customId, value);
                    message.setBody(spool);
                } else {
                    Map<String, Object> requests = new LinkedHashMap<>();
                    requests.put(customId, value);
                    message.setBody(requests);
                }
                return newExchange;
            }

            Object aggregated = oldExchange.getMessage().getBody();
            if (aggregated instanceof OpenAIBatchSpool spool) {
                OpenAIBatchSpool.append(writer(spool), customId, value);
            } else if (aggregated instanceof Map<?, ?> requests) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) requests;
                map.put(customId, value);
            } else {
                throw new IllegalStateException("Unexpected aggregated batch body: " + aggregated);
            }
            return oldExchange;
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot spool the batch request of custom_id " + customId, e);
        }
    }

    @Override
    public void onCompletion(Exchange exchange) {
        if (exchange != null && exchange.getMessage().getBody() instanceof OpenAIBatchSpool spool) {
            BufferedWriter writer = writers.remove(spool.getFile());
            if (writer != null) {
                IOHelper.close(writer);
            }
        }
    }

    private BufferedWriter writer(OpenAIBatchSpool spool) throws IOException {
        try {
            return writers.computeIfAbsent(spool.getFile(), file -> {
                try {
                    return spool.open();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
