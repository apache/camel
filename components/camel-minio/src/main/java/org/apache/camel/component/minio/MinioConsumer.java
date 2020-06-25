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
package org.apache.camel.component.minio;

import java.io.InputStream;
import java.util.*;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.apache.camel.*;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of messages from the Minio Storage Service.
 */
public class MinioConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MinioConsumer.class);

    private Iterator<Result<Item>> marker;
    private transient String minioConsumerToString;

    public MinioConsumer(MinioEndpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        MinioClient minioClient = getConfiguration().getMinioClient();
        String objectName = getConfiguration().getObjectName();
        String bucketName = getConfiguration().getBucketName();
        Queue<Exchange> exchanges;

        if (objectName != null) {
            LOG.trace("Getting object in bucket [{}] with object name [{}]...", bucketName, objectName);

            try {
                InputStream stream = minioClient.getObject(bucketName,
                        objectName,
                        getConfiguration().getOffset(),
                        getConfiguration().getLength(),
                        getConfiguration().getServerSideEncryption());

                exchanges = createExchanges(stream, objectName);
            } catch (Exception e) {
                LOG.trace("Failed to get object in bucket [{}] with object name [{}], Error message [{}]", bucketName, objectName, e);
            }

        } else {

            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            if (marker == null) {

                try {
                    Iterable<Result<Item>> results = minioClient.listObjects(bucketName,
                            getConfiguration().getPrefix(),
                            getConfiguration().isRecursive(),
                            getConfiguration().isUseVersion1()
                    );

                    marker = results.iterator();
                } catch (Exception e) {
                    LOG.trace("Failed to get object list in bucket [{}], Error message [{}]", bucketName, e);
                }
            }

            LOG.trace("Resuming from marker: {}", marker);
            Queue<Exchange> bucketQueue = new LinkedList<>();
            for (int i = 0; i < maxMessagesPerPoll; i++) {
                assert marker != null;
                if (marker.hasNext()) {
                    Item item = marker.next().get();
                    LOG.trace("Getting object name: [{}] in [{}]", item.objectName(), bucketName);
                    try {
                        InputStream resumeStream = minioClient.getObject(
                                bucketName,
                                item.objectName(),
                                getConfiguration().getOffset(),
                                getConfiguration().getLength(),
                                getConfiguration().getServerSideEncryption());

                        Exchange exchange = getEndpoint().createExchange(minioObject, item.objectName());
                        bucketQueue.add(exchange);

                    } catch (Exception e) {
                        LOG.trace("Failed to get object in bucket [{}] with object name [{}], Error message [{}]", bucketName, item.objectName(), e);
                    }
                } else {
                    // no more data so clear marker
                    marker = null;
                }
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", ((Collection<?>) results).size(), bucketName);
            }

        }
        return processBatch(CastUtils.cast(exchanges));
    }

    protected Queue<Exchange> createExchanges(InputStream stream, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(minioObject, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<MinioObject> minioObjectSummaries) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", minioObjectSummaries.size());
        }

        Collection<ResponseInputStream<GetObjectResponse>> minioObjects = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<>();
        try {
            for (MinioObject minioObjectSummary : minioObjectSummaries) {
                ResponseInputStream<GetObjectResponse> minioObject = getAmazonMinioClient()
                        .getObject(GetObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(minioObjectSummary.key()).build(), ResponseTransformer.toInputStream());

                if (includeMinioObject(minioObject)) {
                    minioObjects.add(minioObject);
                    Exchange exchange = getEndpoint().createExchange(minioObject, minioObjectSummary.key());
                    answer.add(exchange);
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error getting MinioObject due: {}", e.getMessage(), e);
            // ensure all previous gathered minio objects are closed
            // if there was an exception creating the exchanges in this batch
            minioObjects.forEach(IOHelper::close);
            throw e;
        }

        return answer;
    }

    /**
     * Decide whether to include the MinioObjects in the results
     *
     * @param minioObject
     * @return true to include, false to exclude
     */
    protected boolean includeMinioObject(ResponseInputStream<GetObjectResponse> minioObject) {

        if (getConfiguration().isIncludeFolders()) {
            return true;
        } else {
            //Config says to ignore folders/directories
            return !"application/x-directory".equalsIgnoreCase(minioObject.response().contentType());
        }
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
                    return "MinioConsumerOnCompletion";
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
        try {
            if (getConfiguration().isMoveAfterRead()) {
                String bucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(MinioConstants.KEY, String.class);

                LOG.trace("Moving object from bucket {} with key {} to bucket {}...", bucketName, key, getConfiguration().getDestinationBucket());

                getMinioClient().copyObject(CopyObjectRequest.builder().destinationKey(key).destinationBucket(getConfiguration().getDestinationBucket()).copySource(bucketName + "/" + key).build());

                LOG.trace("Moved object from bucket {} with key {} to bucket {}...", bucketName, key, getConfiguration().getDestinationBucket());
            }
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(MinioConstants.KEY, String.class);

                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);

                getMinioClient().deleteObject(DeleteObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(key).build());

                LOG.trace("Deleted object from bucket {} with key {}...", bucketName, key);
            }
        } catch (MinioException e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.", exchange, e);
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

    protected MinioConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected MinioClient getMinioClient() {
        return getEndpoint().getMinioClient();
    }

    @Override
    public MinioEndpoint getEndpoint() {
        return (MinioEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (minioConsumerToString == null) {
            minioConsumerToString = "MinioConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return minioConsumerToString;
    }
}
