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

package org.apache.camel.component.ibm.cos;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.CopyObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.GetObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of messages from IBM Cloud Object Storage
 */
public class IBMCOSConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IBMCOSConsumer.class);

    private String continuationToken;

    public IBMCOSConsumer(IBMCOSEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String fileName = getConfiguration().getFileName();
        String bucketName = getConfiguration().getBucketName();
        Queue<Exchange> exchanges;

        if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            S3Object s3Object = getCosClient().getObject(new GetObjectRequest(bucketName, fileName));
            exchanges = createExchanges(s3Object, fileName);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request();
            listObjectsRequest.withBucketName(bucketName);

            if (ObjectHelper.isNotEmpty(getConfiguration().getPrefix())) {
                listObjectsRequest.withPrefix(getConfiguration().getPrefix());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getDelimiter())) {
                listObjectsRequest.withDelimiter(getConfiguration().getDelimiter());
            }

            if (maxMessagesPerPoll > 0) {
                listObjectsRequest.withMaxKeys(maxMessagesPerPoll);
            }

            // if there was a continuation token from previous poll then use that to
            // continue from where we left last time
            if (continuationToken != null) {
                LOG.trace("Resuming from continuation token: {}", continuationToken);
                listObjectsRequest.withContinuationToken(continuationToken);
            }

            ListObjectsV2Result listObjects = getCosClient().listObjectsV2(listObjectsRequest);

            if (Boolean.TRUE.equals(listObjects.isTruncated())) {
                continuationToken = listObjects.getNextContinuationToken();
                LOG.trace("Returned list is truncated, so setting next continuation token: {}", continuationToken);
            } else {
                // no more data so clear token
                continuationToken = null;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace(
                        "Found {} objects in bucket [{}]...",
                        listObjects.getObjectSummaries().size(),
                        bucketName);
            }

            exchanges = createExchanges(listObjects.getObjectSummaries());
        }

        // okay we have some response from IBM COS so lets mark the consumer as ready
        forceConsumerAsReady();

        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(org.apache.camel.ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(org.apache.camel.ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(org.apache.camel.ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // use default consumer callback
            org.apache.camel.AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }

        return total;
    }

    protected Queue<Exchange> createExchanges(S3Object s3Object, String key) {
        Queue<Exchange> exchanges = new LinkedList<>();
        Exchange exchange = createExchange(s3Object, key);
        exchanges.add(exchange);
        return exchanges;
    }

    protected Queue<Exchange> createExchanges(List<S3ObjectSummary> s3ObjectSummaries) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", s3ObjectSummaries.size());
        }

        Queue<Exchange> exchanges = new LinkedList<>();
        for (S3ObjectSummary s3ObjectSummary : s3ObjectSummaries) {
            if (!getConfiguration().isIncludeFolders() && isFolder(s3ObjectSummary)) {
                LOG.trace("Skipping folder: {}", s3ObjectSummary.getKey());
                continue;
            }

            if (getEndpoint().getInProgressRepository() != null
                    && getEndpoint().getInProgressRepository().contains(s3ObjectSummary.getKey())) {
                LOG.trace("Object {} is already in progress", s3ObjectSummary.getKey());
                continue;
            }

            S3Object s3Object = getCosClient()
                    .getObject(new GetObjectRequest(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey()));
            Exchange exchange = createExchange(s3Object, s3ObjectSummary.getKey());
            exchanges.add(exchange);
        }

        return exchanges;
    }

    protected Exchange createExchange(S3Object s3Object, String key) {
        Exchange exchange = createExchange(true);
        Message message = exchange.getIn();

        if (getConfiguration().isIncludeBody()) {
            message.setBody(s3Object.getObjectContent());
        }

        message.setHeader(IBMCOSConstants.KEY, key);
        message.setHeader(IBMCOSConstants.BUCKET_NAME, s3Object.getBucketName());
        message.setHeader(IBMCOSConstants.E_TAG, s3Object.getObjectMetadata().getETag());
        message.setHeader(
                IBMCOSConstants.LAST_MODIFIED, s3Object.getObjectMetadata().getLastModified());
        message.setHeader(
                IBMCOSConstants.VERSION_ID, s3Object.getObjectMetadata().getVersionId());
        message.setHeader(
                IBMCOSConstants.CONTENT_TYPE, s3Object.getObjectMetadata().getContentType());
        message.setHeader(
                IBMCOSConstants.CONTENT_LENGTH, s3Object.getObjectMetadata().getContentLength());
        message.setHeader(
                IBMCOSConstants.CONTENT_ENCODING, s3Object.getObjectMetadata().getContentEncoding());
        message.setHeader(
                IBMCOSConstants.CONTENT_DISPOSITION,
                s3Object.getObjectMetadata().getContentDisposition());
        message.setHeader(
                IBMCOSConstants.CACHE_CONTROL, s3Object.getObjectMetadata().getCacheControl());

        if (s3Object.getObjectMetadata().getUserMetadata() != null) {
            message.setHeader(
                    IBMCOSConstants.METADATA, s3Object.getObjectMetadata().getUserMetadata());
        }

        /**
         * If includeBody != true, it is safe to close the object here. If includeBody == true, the caller is
         * responsible for closing the stream and object once the body has been fully consumed. As of 2.17, the consumer
         * does not close the stream or object on commit.
         */
        if (!getConfiguration().isIncludeBody()) {
            IOHelper.close(s3Object);
        } else {
            if (getConfiguration().isAutocloseBody()) {
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        IOHelper.close(s3Object);
                    }
                });
            }
        }

        // Store key as exchange property for later use
        exchange.setProperty(IBMCOSConstants.KEY, key);

        // Add synchronization to handle post-processing
        exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
            @Override
            public void onComplete(Exchange exchange) {
                processCommit(s3Object.getBucketName(), key);
            }

            @Override
            public void onFailure(Exchange exchange) {
                processRollback(key);
            }
        });

        return exchange;
    }

    protected void processCommit(String bucketName, String key) {
        try {
            if (getConfiguration().isMoveAfterRead()) {
                copyObject(bucketName, key);
                deleteObject(bucketName, key);
                LOG.trace("Moved object from bucket {} with key {} to destination bucket", bucketName, key);
            } else if (getConfiguration().isDeleteAfterRead()) {
                deleteObject(bucketName, key);
                LOG.trace("Deleted object from bucket {} with key {}", bucketName, key);
            }
        } catch (Exception e) {
            LOG.warn("Error during post processing of object {} from bucket {}: {}", key, bucketName, e.getMessage());
        }
    }

    protected void processRollback(String key) {
        LOG.trace("Processing failed for object with key {}", key);
    }

    private void copyObject(String bucketName, String key) {
        String destinationKey = getDestinationKey(key);

        CopyObjectRequest copyObjectRequest =
                new CopyObjectRequest(bucketName, key, getConfiguration().getDestinationBucket(), destinationKey);

        LOG.trace(
                "Copying object from bucket {} with key {} to destination bucket {} with destination key {}",
                bucketName,
                key,
                getConfiguration().getDestinationBucket(),
                destinationKey);

        getCosClient().copyObject(copyObjectRequest);
    }

    private void deleteObject(String bucketName, String key) {
        DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, key);
        getCosClient().deleteObject(deleteObjectRequest);
    }

    private String getDestinationKey(String key) {
        String destinationKey = key;
        if (ObjectHelper.isNotEmpty(getConfiguration().getDestinationBucketPrefix())) {
            destinationKey = getConfiguration().getDestinationBucketPrefix() + destinationKey;
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getDestinationBucketSuffix())) {
            destinationKey = destinationKey + getConfiguration().getDestinationBucketSuffix();
        }
        return destinationKey;
    }

    private boolean isFolder(S3ObjectSummary s3ObjectSummary) {
        return s3ObjectSummary.getKey().endsWith("/") && s3ObjectSummary.getSize() == 0;
    }

    protected IBMCOSConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected AmazonS3 getCosClient() {
        return getEndpoint().getCosClient();
    }

    @Override
    public IBMCOSEndpoint getEndpoint() {
        return (IBMCOSEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (s3ConsumerToString == null) {
            s3ConsumerToString =
                    "IBMCOSConsumer[" + getEndpoint().getConfiguration().getBucketName() + "]";
        }
        return s3ConsumerToString;
    }

    private String s3ConsumerToString;
}
