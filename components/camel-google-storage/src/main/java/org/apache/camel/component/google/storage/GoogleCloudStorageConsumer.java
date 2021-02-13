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
package org.apache.camel.component.google.storage;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.CopyRequest;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudStorageConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageConsumer.class);

    //private String marker;
    //private transient String consumerToString;

    public GoogleCloudStorageConsumer(GoogleCloudStorageEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().isMoveAfterRead()) {

            Bucket bucket = getStorageClient().get(getConfiguration().getDestinationBucket());
            if (bucket != null) {
                LOG.trace("Bucket [{}] already exists", bucket.getName());
                return;
            } else {
                LOG.trace("Destination Bucket [{}] doesn't exist yet", getConfiguration().getDestinationBucket());
                if (getConfiguration().isAutoCreateBucket()) {
                    // creates the new bucket because it doesn't exist yet
                    GoogleCloudStorageEndpoint.createNewBucket(getConfiguration().getDestinationBucket(), getConfiguration(),
                            getStorageClient());
                }
            }

        }
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String fileName = getConfiguration().getObjectName();
        String bucketName = getConfiguration().getBucketName();
        Queue<Exchange> exchanges = new LinkedList<>();

        if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            Blob blob = getStorageClient().get(bucketName, fileName);

            exchanges = createExchanges(blob, fileName);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            List<Blob> bloblist = new LinkedList<>();
            for (Blob blob : getStorageClient().list(bucketName).iterateAll()) {
                bloblist.add(blob);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", bloblist.size(), bucketName);
            }

            exchanges = createExchanges(bloblist);
        }

        return processBatch(CastUtils.cast(exchanges));
    }

    protected Queue<Exchange> createExchanges(Blob blob, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(blob, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<Blob> blobList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", blobList.size());
        }

        Queue<Exchange> answer = new LinkedList<>();
        try {
            for (Blob blob : blobList) {

                if (includeObject(blob)) {
                    Exchange exchange = getEndpoint().createExchange(blob, blob.getBlobId().getName());
                    answer.add(exchange);
                }

            }
        } catch (Exception e) {
            LOG.warn("Error getting object due: {}", e.getMessage(), e);
            throw e;
        }

        return answer;
    }

    /**
     * Decide whether to include the Objects in the results
     *
     * @param  Object
     * @return        true to include, false to exclude
     */
    protected boolean includeObject(Blob blob) {

        if (getConfiguration().isIncludeFolders()) {
            return true;
        }
        // Config says to ignore folders/directories
        return blob.getName().endsWith("/");
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.adapt(ExtendedExchange.class).addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "ConsumerOnCompletion";
                }
            });

            LOG.trace("Processing exchange [{}]...", exchange);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange [{}] done.", exchange);
                }
            });
        }

        return total;
    }

    /**
     * Strategy to delete the message after being processed.
     *
     * @param exchange the exchange
     */
    protected void processCommit(Exchange exchange) {
        //LOG.info("processCommit");
        try {
            if (getConfiguration().isMoveAfterRead()) {
                String bucketName = exchange.getIn().getHeader(GoogleCloudStorageConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class);

                LOG.trace("Moving object from bucket {} with key {} to bucket {}...", bucketName, key,
                        getConfiguration().getDestinationBucket());

                BlobId sourceBlobId = BlobId.of(bucketName, key);
                BlobId targetBlobId = BlobId.of(getConfiguration().getDestinationBucket(), key);
                CopyRequest request = CopyRequest.of(sourceBlobId, targetBlobId);
                CopyWriter copyWriter = getStorageClient().copy(request);

                LOG.trace("Moved object from bucket {} with key {} to bucketName {} -> {}", bucketName, key,
                        getConfiguration().getDestinationBucket(), copyWriter.getResult());
            }
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(GoogleCloudStorageConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class);

                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);

                boolean b = getStorageClient().delete(bucketName, key);

                LOG.trace("Deleted object from bucket {} with key {}, result={}", bucketName, key, b);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.",
                    exchange, e);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }

    protected GoogleCloudStorageComponentConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Storage getStorageClient() {
        return getEndpoint().getStorageClient();
    }

    @Override
    public GoogleCloudStorageEndpoint getEndpoint() {
        return (GoogleCloudStorageEndpoint) super.getEndpoint();
    }
}
