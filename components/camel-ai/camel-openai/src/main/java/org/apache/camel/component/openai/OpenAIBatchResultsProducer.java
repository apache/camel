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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for the {@code batch-results} operation, which downloads the output or error file of a finished
 * batch.
 * <p>
 * The body is an {@link java.util.Iterator} of the lines of the file, each parsed into a {@link Map}, read from the
 * response as the splitter consumes them, so a result of any size is handled with only the current line in memory. The
 * response is closed once the last line is read, or when the exchange completes.
 */
public class OpenAIBatchResultsProducer extends DefaultProducer {

    private static final String ERROR_FILE = "error";

    public OpenAIBatchResultsProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        String batchId = exchange.getIn().getHeader(OpenAIConstants.BATCH_ID, String.class);
        if (ObjectHelper.isEmpty(batchId)) {
            throw new IllegalArgumentException(
                    "The " + OpenAIConstants.BATCH_ID + " header must hold the id of the batch to read the results of");
        }

        String resultsFile = exchange.getIn().getHeader(OpenAIConstants.BATCH_RESULTS_FILE,
                config.getBatchResultsFile(), String.class);
        if (!"output".equals(resultsFile) && !ERROR_FILE.equals(resultsFile)) {
            throw new IllegalArgumentException(
                    "Unsupported batch results file: " + resultsFile + ". Supported: output, error");
        }

        Batch batch;
        try {
            batch = getEndpoint().getClient().batches().retrieve(batchId);
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }
        OpenAIBatchSupport.setBatchHeaders(exchange.getMessage(), batch);

        // the API sets the file ids once it has stopped processing, so their presence, not the status, says whether
        // the results can be read; the status only explains why a file is missing
        Optional<String> fileId = ERROR_FILE.equals(resultsFile) ? batch.errorFileId() : batch.outputFileId();
        if (fileId.isEmpty()) {
            switch (batch.status().value()) {
                case FAILED -> {
                    // the input was rejected, so no result file exists: the reason is in the errors of the batch
                    List<Map<String, Object>> errors = OpenAIBatchSupport.errors(batch);
                    throw new CamelExchangeException(
                            "Batch " + batchId + " failed: "
                                                     + (errors.isEmpty() ? "the API reported no validation errors" : errors),
                            exchange);
                }
                case COMPLETED, EXPIRED, CANCELLED ->
                    // a batch without failures has no error file, and one without successes no output file
                    exchange.getMessage().setBody(Collections.emptyIterator());
                default -> throw new CamelExchangeException(
                        "Batch " + batchId + " is " + batch.status().asString() + " and has no " + resultsFile
                                                            + " file yet",
                        exchange);
            }
            return;
        }

        HttpResponse response;
        try {
            response = getEndpoint().getClient().files().content(fileId.get());
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }
        OpenAIBatchResultLines lines = new OpenAIBatchResultLines(response);
        // the lines close the response once read to the end; this covers a route that stops before that
        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange completed) {
                lines.close();
            }
        });
        exchange.getMessage().setBody(lines);
    }
}
