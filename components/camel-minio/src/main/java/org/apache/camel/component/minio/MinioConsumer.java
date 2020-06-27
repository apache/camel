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

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
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

        String bucketName = getConfiguration().getBucketName();
        MinioClient minioClient = getMinioClient();
        String objectName = getConfiguration().getObjectName();
        InputStream minioObject = null;
        Queue<Exchange> exchanges = null;

        assert bucketExists(minioClient, bucketName);
        if (objectName != null) {
            LOG.trace("Getting object in bucket [{}] with object name [{}]...", bucketName, objectName);

            try {
                minioObject = getObject(bucketName, minioClient, objectName);
                if (minioObject != null) {
                    exchanges = createExchanges(minioObject, objectName);
                }

            } catch (Throwable e) {
                LOG.warn("Failed to get object in bucket [{}] with object name [{}], Error message [{}]", bucketName, objectName, e);
                throw e;

            } finally {
                //must be closed after use to release network resources.
                try {
                    assert minioObject != null;
                    minioObject.close();

                } catch (IOException e) {
                    LOG.warn("Error closing MinioObject due: [{}], Could not release network resources properly", e.getMessage());
                }
            }

        } else {

            LOG.trace("Queueing objects in bucket [{}]...", bucketName);
            if (marker == null) {

                marker = listObjects(minioClient, bucketName).iterator();
                LOG.trace("Marker created...");
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", ((Collection<?>) marker).size(), bucketName);
            }

            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            LOG.trace("Getting from marker...");
            exchanges = createExchanges(marker);
        }

        assert CastUtils.cast(exchanges) != null;
        return processBatch(CastUtils.cast(exchanges));
    }

    protected Queue<Exchange> createExchanges(InputStream objectStream, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(objectStream, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(Iterator<Result<Item>> objectsList) throws Exception {

        Collection<InputStream> minioObjects = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<>();

        try {
            String bucketName = getConfiguration().getBucketName();
            MinioClient minioClient = getMinioClient();
            for (int i = 0; i < maxMessagesPerPoll; i++) {
                assert marker != null;
                if (marker.hasNext()) {
                    Item item = marker.next().get();
                    String objectName = item.objectName();
                    InputStream minioObject;
                    LOG.trace("Getting object name: [{}] in [{}]", objectName, bucketName);

                    minioObject = getObject(bucketName, minioClient, objectName);

                    if (minioObject != null) {
                        minioObjects.add(minioObject);
                        Exchange exchange = getEndpoint().createExchange(minioObject, item.objectName());
                        answer.add(exchange);
                    } else {
                        LOG.trace("no returned objects found, Possible reason: Downloads may have set to fileName location");
                    }

                } else {
                    // no more data so clear marker
                    marker = null;
                }
            }

        } catch (Throwable e) {
            LOG.warn("Error getting MinioObject due: {}", e.getMessage(), e);
            throw e;

        } finally {
            // must be closed after use to release network resources.
            minioObjects.forEach(minioObject -> {
                try {
                    minioObject.close();
                } catch (IOException e) {
                    LOG.warn("Error closing MinioObject due: [{}], Could not release network resources properly", e.getMessage());
                }
            });
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Received [{}] messages out of [{}] objects in this poll, Maximum objects per poll is: [{}]",
                    minioObjects.size(), ((Collection<?>) objectsList).size(), maxMessagesPerPoll);
        }

        return answer;
    }

    private boolean bucketExists(MinioClient minioClient, String bucketName) throws Exception {
        try {
            LOG.trace("bucket name [{}] does not exist", bucketName);
            return minioClient.bucketExists(bucketName);

        } catch (Throwable e) {
            LOG.warn("Error checking bucket due: [{}]", e.getMessage());
            throw e;
        }
    }

    private List<Bucket> bucketList(MinioClient minioClient) throws Exception {
        try {
            return minioClient.listBuckets();
        } catch (Throwable e) {
            LOG.warn("Failed to get bucket list, Error message [{}]", e.getMessage());
            throw e;
        }
    }

    private Iterable<Result<Item>> listObjects(MinioClient minioClient, String bucketName) throws Exception {
        try {
            return minioClient.listObjects(bucketName,
                    getConfiguration().getPrefix(),
                    getConfiguration().isRecursive(),
                    getConfiguration().isUseVersion1()
            );

        } catch (Throwable e) {
            LOG.warn("Failed to get object list in bucket [{}], Error message [{}]", bucketName, e);
            throw e;
        }
    }

    private InputStream getObject(String bucketName, MinioClient minioClient, String objectName) throws Exception {
        InputStream minioObject = null;
        if (getConfiguration().getOffset() != 0) {
            if (getConfiguration().getServerSideEncryption() != null) {
                minioObject = minioClient.getObject(bucketName,
                        objectName,
                        getConfiguration().getOffset(),
                        getConfiguration().getLength(),
                        getConfiguration().getServerSideEncryption());
            } else {
                if (getConfiguration().getLength() != 0) {
                    minioObject = minioClient.getObject(bucketName,
                            objectName,
                            getConfiguration().getOffset(),
                            getConfiguration().getLength());
                } else {
                    minioObject = minioClient.getObject(bucketName,
                            objectName,
                            getConfiguration().getOffset());
                }
            }
        } else {
            if (getConfiguration().getServerSideEncryption() != null) {
                if (getConfiguration().getFileName() != null) {
                    minioClient.getObject(bucketName,
                            objectName,
                            getConfiguration().getServerSideEncryption(),
                            getConfiguration().getFileName());
                } else {
                    minioObject = minioClient.getObject(bucketName,
                            objectName,
                            getConfiguration().getServerSideEncryption());
                }
            } else {
                if (getConfiguration().getFileName() != null) {
                    minioClient.getObject(bucketName,
                            objectName,
                            getConfiguration().getFileName());
                } else {
                    minioObject = minioClient.getObject(bucketName,
                            objectName);
                }
            }
        }
        return minioObject;
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
                String srcObjectName = getConfiguration().getSrcObjectName();

                if (getConfiguration().getSrcObjectName() == null) {
                    srcObjectName = key;
                }

                LOG.trace("Moving object from bucket {} with key {} to bucket {}...",
                        bucketName, key, getConfiguration().getSrcBucketName());

                getMinioClient().copyObject(bucketName,
                        key,
                        null,
                        getConfiguration().getServerSideEncryption(),
                        getConfiguration().getSrcBucketName(),
                        srcObjectName,
                        getConfiguration().getSrcServerSideEncryption(),
                        getConfiguration().getCopyConditions());

                LOG.trace("Moved object from bucket {} with key {} to bucket {}...",
                        bucketName, key, getConfiguration().getSrcBucketName());
            }
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(MinioConstants.KEY, String.class);

                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);

                getMinioClient().removeObject(bucketName, key);

                LOG.trace("Deleted object from bucket {} with key {}...", bucketName, key);
            }
        } catch (MinioException e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.",
                    exchange, e);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeyException e) {
            e.printStackTrace();
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
