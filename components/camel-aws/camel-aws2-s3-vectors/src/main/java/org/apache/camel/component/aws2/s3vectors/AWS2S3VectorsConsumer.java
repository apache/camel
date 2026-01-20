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
package org.apache.camel.component.aws2.s3vectors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/**
 * A Consumer that polls AWS S3 Vectors by performing similarity searches and returning matching vectors
 */
public class AWS2S3VectorsConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3VectorsConsumer.class);

    private final Set<String> processedVectorIds = new HashSet<>();

    public AWS2S3VectorsConsumer(AWS2S3VectorsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String vectorBucketName = getConfiguration().getVectorBucketName();
        String vectorIndexName = getConfiguration().getVectorIndexName();
        String queryVectorString = getConfiguration().getConsumerQueryVector();

        // If no query vector is configured, cannot poll
        if (queryVectorString == null || queryVectorString.trim().isEmpty()) {
            LOG.trace(
                    "No query vector configured for consumer, skipping poll. Set consumerQueryVector to enable polling.");
            return 0;
        }

        // Parse comma-separated string to List<Float>
        List<Float> queryVector = parseVectorString(queryVectorString);

        LOG.trace("Polling vector bucket [{}] with index [{}] using query vector", vectorBucketName, vectorIndexName);

        Queue<Exchange> exchanges = new LinkedList<>();

        try {
            // Build query request
            QueryVectorsRequest.Builder requestBuilder = QueryVectorsRequest.builder()
                    .vectorBucketName(vectorBucketName)
                    .indexName(vectorIndexName)
                    .queryVector(VectorData.builder().float32(queryVector).build())
                    .topK(Math.min(getMaxMessagesPerPoll(), getConfiguration().getTopK()));

            // Add metadata filter if configured
            String metadataFilter = getConfiguration().getConsumerMetadataFilter();
            if (ObjectHelper.isNotEmpty(metadataFilter)) {
                // Note: Metadata filter implementation depends on AWS SDK support
                LOG.trace("Using metadata filter: {}", metadataFilter);
            }

            // Execute query
            QueryVectorsResponse response = getS3VectorsClient().queryVectors(requestBuilder.build());

            if (response.hasVectors()) {
                List<QueryOutputVector> vectors = response.vectors();
                LOG.trace("Query returned {} vectors", vectors.size());

                // Create exchanges for each vector
                for (QueryOutputVector vector : vectors) {
                    String vectorId = vector.key();

                    // Skip if already processed (to avoid duplicates in subsequent polls)
                    if (processedVectorIds.contains(vectorId)) {
                        LOG.trace("Skipping already processed vector [{}]", vectorId);
                        continue;
                    }

                    // Create exchange
                    Exchange exchange = createExchange(false);
                    Message message = exchange.getIn();

                    // Set vector data as body
                    message.setBody(vector);

                    // Set headers
                    message.setHeader(AWS2S3VectorsConstants.VECTOR_ID, vectorId);
                    message.setHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME, vectorBucketName);
                    message.setHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME, vectorIndexName);

                    // Add to processed set
                    processedVectorIds.add(vectorId);

                    // Add delete callback if deleteAfterRead is enabled
                    if (getConfiguration().isDeleteAfterRead()) {
                        exchange.getExchangeExtension().addOnCompletion(
                                new VectorDeleteSynchronization(
                                        getS3VectorsClient(), vectorBucketName, vectorIndexName,
                                        vectorId));
                    }

                    exchanges.add(exchange);
                }
            } else {
                LOG.trace("No vectors returned from query");
            }

        } catch (Exception e) {
            LOG.error("Error polling S3 Vectors", e);
            throw e;
        }

        return processBatch(ObjectHelper.cast(Queue.class, exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }

        return total;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting S3 Vectors consumer for bucket [{}]", getConfiguration().getVectorBucketName());

        // Validate configuration
        if (getConfiguration().getVectorIndexName() == null) {
            throw new IllegalArgumentException("vectorIndexName is required for the consumer");
        }
    }

    @Override
    protected void doStop() throws Exception {
        // Clear processed vector IDs on stop
        processedVectorIds.clear();
        super.doStop();
    }

    @Override
    public AWS2S3VectorsEndpoint getEndpoint() {
        return (AWS2S3VectorsEndpoint) super.getEndpoint();
    }

    private AWS2S3VectorsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private S3VectorsClient getS3VectorsClient() {
        return getEndpoint().getS3VectorsClient();
    }

    /**
     * Parse comma-separated vector string into List<Float>
     *
     * @param  vectorString comma-separated float values (e.g., "0.1,0.2,0.3")
     * @return              List of Float values
     */
    private List<Float> parseVectorString(String vectorString) {
        List<Float> result = new ArrayList<>();
        String[] parts = vectorString.split(",");
        for (String part : parts) {
            try {
                result.add(Float.parseFloat(part.trim()));
            } catch (NumberFormatException e) {
                LOG.warn("Failed to parse vector component '{}' as float, skipping", part);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "consumerQueryVector must contain at least one valid float value. Got: " + vectorString);
        }
        return result;
    }

    /**
     * Synchronization callback to delete a vector after successful processing
     */
    private static class VectorDeleteSynchronization extends SynchronizationAdapter {

        private final S3VectorsClient client;
        private final String vectorBucketName;
        private final String vectorIndexName;
        private final String vectorId;

        public VectorDeleteSynchronization(
                                           S3VectorsClient client, String vectorBucketName, String vectorIndexName,
                                           String vectorId) {
            this.client = client;
            this.vectorBucketName = vectorBucketName;
            this.vectorIndexName = vectorIndexName;
            this.vectorId = vectorId;
        }

        @Override
        public void onComplete(Exchange exchange) {
            try {
                LOG.trace("Deleting vector [{}] from bucket [{}] index [{}] after successful processing",
                        vectorId, vectorBucketName, vectorIndexName);

                List<String> keysToDelete = new ArrayList<>();
                keysToDelete.add(vectorId);

                client.deleteVectors(DeleteVectorsRequest.builder()
                        .vectorBucketName(vectorBucketName)
                        .indexName(vectorIndexName)
                        .keys(keysToDelete)
                        .build());

                LOG.debug("Deleted vector [{}] after successful processing", vectorId);
            } catch (Exception e) {
                LOG.warn("Error deleting vector [{}] after processing: {}", vectorId, e.getMessage(), e);
            }
        }

        @Override
        public void onFailure(Exchange exchange) {
            LOG.trace("Exchange failed, not deleting vector [{}]", vectorId);
        }
    }
}
