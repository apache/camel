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

import java.util.Optional;

import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI producer for the {@code batch-results} operation, which downloads the output or error file of a finished
 * batch.
 * <p>
 * The body is the file as an {@link java.io.InputStream}, so a large result can be split line by line with the splitter
 * in streaming mode. The HTTP response is closed when the exchange completes. Stream caching reads such a body into
 * memory before the next step, so a route handling large results should turn it off.
 */
public class OpenAIBatchResultsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIBatchResultsProducer.class);
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

        String status = batch.status().asString();
        if ("failed".equals(status)) {
            // the input was rejected, so no result file exists: the reason is in the errors of the batch
            throw new CamelExchangeException(
                    "Batch " + batchId + " failed: " + OpenAIBatchSupport.errors(batch), exchange);
        }
        if (!OpenAIBatchSupport.isFinal(batch)) {
            throw new CamelExchangeException(
                    "Batch " + batchId + " is " + status + "; its results are available once it is completed, "
                                             + "expired or cancelled",
                    exchange);
        }

        Optional<String> fileId = ERROR_FILE.equals(resultsFile) ? batch.errorFileId() : batch.outputFileId();
        if (fileId.isEmpty()) {
            // a batch without failures has no error file, and one without successes no output file
            exchange.getMessage().setBody(null);
            return;
        }

        HttpResponse response;
        try {
            response = getEndpoint().getClient().files().content(fileId.get());
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }
        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange completed) {
                try {
                    response.close();
                } catch (Exception e) {
                    // the exchange is done, so a failure to close the response must not replace its outcome
                    LOG.debug("Could not close the batch results response of batch {}", batchId, e);
                }
            }
        });
        exchange.getMessage().setBody(response.body());
    }
}
