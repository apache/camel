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

import com.openai.models.batches.Batch;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for the {@code batch-retrieve} and {@code batch-cancel} operations, which report the progress of a
 * batch or stop it.
 * <p>
 * The body is left untouched, so an exchange polling a batch keeps whatever it carries, and the batch is reported on
 * the headers.
 */
public class OpenAIBatchStoredProducer extends DefaultProducer {

    private final boolean cancel;

    public OpenAIBatchStoredProducer(OpenAIEndpoint endpoint, boolean cancel) {
        super(endpoint);
        this.cancel = cancel;
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String batchId = exchange.getIn().getHeader(OpenAIConstants.BATCH_ID, String.class);
        if (ObjectHelper.isEmpty(batchId)) {
            throw new IllegalArgumentException(
                    "The " + OpenAIConstants.BATCH_ID + " header must hold the id of the batch to "
                                               + (cancel ? "cancel" : "retrieve"));
        }

        Batch batch;
        try {
            batch = cancel
                    ? getEndpoint().getClient().batches().cancel(batchId)
                    : getEndpoint().getClient().batches().retrieve(batchId);
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }

        if (getEndpoint().getConfiguration().isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.BATCH_RESPONSE, batch);
        }
        OpenAIBatchSupport.setBatchHeaders(exchange.getMessage(), batch);
    }
}
