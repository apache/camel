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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.cast;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * A Consumer of messages from the Minio Storage Service.
 */
public class MinioConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MinioConsumer.class);

    private long totalCounter;
    private String continuationToken;
    private transient String minioConsumerToString;

    public MinioConsumer(MinioEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().isMoveAfterRead()) {
            String destinationBucketName = getConfiguration().getDestinationBucketName();

            if (isNotEmpty(destinationBucketName)) {

                if (bucketExists(destinationBucketName)) {
                    LOG.trace("Bucket {} already exists", destinationBucketName);
                } else {
                    LOG.trace("Destination Bucket {} doesn't exist yet", destinationBucketName);

                    if (getConfiguration().isAutoCreateBucket()) {
                        // creates the new bucket because it doesn't exist yet
                        LOG.trace("Creating Destination bucket {}...", destinationBucketName);
                        makeBucket(destinationBucketName);
                        LOG.trace("Destination Bucket created");
                    } else {
                        throw new IllegalArgumentException(
                                "Destination Bucket does not exist, set autoCreateBucket option for bucket auto creation");
                    }
                }
            } else {
                LOG.warn("invalid destinationBucketName found: {}", destinationBucketName);
            }
        }
    }

    private boolean bucketExists(String bucketName) throws Exception {
        return getMinioClient().bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    private void makeBucket(String bucketName) throws Exception {
        MakeBucketArgs.Builder makeBucketRequest
                = MakeBucketArgs.builder().bucket(bucketName).objectLock(getConfiguration().isObjectLock());
        if (isNotEmpty(getConfiguration().getRegion())) {
            makeBucketRequest.region(getConfiguration().getRegion());
        }
        getMinioClient().makeBucket(makeBucketRequest.build());
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String bucketName = getConfiguration().getBucketName();
        String objectName = getConfiguration().getObjectName();
        Deque<Exchange> exchanges;

        if (isNotEmpty(objectName)) {
            LOG.trace("Getting object in bucket {} with object name {}...", bucketName, objectName);

            exchanges = createExchanges(objectName);
            return processBatch(CastUtils.cast(exchanges));

        } else {

            LOG.trace("Queueing objects in bucket {}...", bucketName);

            ListObjectsArgs.Builder listObjectRequest = ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .includeUserMetadata(getConfiguration().isIncludeUserMetadata())
                    .includeVersions(getConfiguration().isIncludeVersions())
                    .recursive(getConfiguration().isRecursive())
                    .useApiVersion1(getConfiguration().isUseVersion1());

            if (isNotEmpty(getConfiguration().getDelimiter())) {
                listObjectRequest.delimiter(getConfiguration().getDelimiter());
            }

            if (maxMessagesPerPoll > 0) {
                listObjectRequest.maxKeys(maxMessagesPerPoll);
            }

            if (isNotEmpty(getConfiguration().getPrefix())) {
                listObjectRequest.prefix(getConfiguration().getPrefix());
            }

            if (isNotEmpty(getConfiguration().getStartAfter())) {
                listObjectRequest.startAfter(getConfiguration().getStartAfter());
                continuationToken = null;
            }

            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            if (isNotEmpty(continuationToken)) {
                LOG.trace("Resuming from marker: {}", continuationToken);
                listObjectRequest.startAfter(continuationToken);
            }

            Iterator<Result<Item>> listObjects = getMinioClient().listObjects(listObjectRequest.build()).iterator();

            // we have listed some objects so mark the consumer as ready
            forceConsumerAsReady();

            if (listObjects.hasNext()) {
                exchanges = createExchanges(listObjects);
                if (maxMessagesPerPoll <= 0 || exchanges.size() < maxMessagesPerPoll) {
                    continuationToken = null;
                } else {
                    continuationToken = exchanges.getLast().getIn().getHeader(MinioConstants.OBJECT_NAME, String.class);
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Found {} objects in bucket {}...", totalCounter, bucketName);
                }
                return processBatch(CastUtils.cast(exchanges));

            } else {
                // no more data so clear marker
                continuationToken = null;
                return 0;
            }
        }
    }

    protected Deque<Exchange> createExchanges(String objectName) throws Exception {
        Deque<Exchange> answer = new LinkedList<>();
        Exchange exchange = createExchange(objectName);
        answer.add(exchange);
        return answer;
    }

    protected Deque<Exchange> createExchanges(Iterator<Result<Item>> minioObjectSummaries) throws Exception {
        int messageCounter = 0;
        Deque<Exchange> answer = new LinkedList<>();
        try {
            if (getConfiguration().isIncludeFolders()) {
                do {
                    messageCounter++;
                    Item minioObjectSummary = minioObjectSummaries.next().get();
                    Exchange exchange = createExchange(minioObjectSummary.objectName());
                    answer.add(exchange);
                } while (minioObjectSummaries.hasNext());
            } else {
                do {
                    messageCounter++;
                    Item minioObjectSummary = minioObjectSummaries.next().get();
                    // ignore if directory
                    if (!minioObjectSummary.isDir()) {
                        Exchange exchange = createExchange(minioObjectSummary.objectName());
                        answer.add(exchange);
                    }
                } while (minioObjectSummaries.hasNext());
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Received {} messages in this poll", messageCounter);
                totalCounter += messageCounter;
            }

        } catch (Exception e) {
            LOG.warn("Error getting MinioObject due: {}", e.getMessage());
            throw e;
        }

        return answer;
    }

    private InputStream getObject(String bucketName, MinioClient minioClient, String objectName) throws Exception {
        GetObjectArgs.Builder getObjectRequest = GetObjectArgs.builder().bucket(bucketName).object(objectName);

        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getServerSideEncryptionCustomerKey,
                getObjectRequest::ssec);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getOffset, getObjectRequest::offset);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getLength, getObjectRequest::length);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getVersionId, getObjectRequest::versionId);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getMatchETag, getObjectRequest::matchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getNotMatchETag, getObjectRequest::notMatchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getModifiedSince,
                getObjectRequest::modifiedSince);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getUnModifiedSince,
                getObjectRequest::unmodifiedSince);

        return minioClient.getObject(getObjectRequest.build());
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            String srcBucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);
            String srcObjectName = exchange.getIn().getHeader(MinioConstants.OBJECT_NAME, String.class);
            if (getConfiguration().isIncludeBody()) {
                InputStream minioObject;
                try {
                    minioObject = getObject(srcBucketName, getMinioClient(), srcObjectName);
                    exchange.getIn().setBody(IOUtils.toByteArray(minioObject));
                    if (getConfiguration().isAutoCloseBody()) {
                        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                IOHelper.close(minioObject);
                            }
                        });
                    }
                } catch (Exception e) {
                    LOG.warn("Error getting MinioObject due: {}", e.getMessage());
                    throw e;
                }
            }

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
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

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
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
            String srcBucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);
            String srcObjectName = exchange.getIn().getHeader(MinioConstants.OBJECT_NAME, String.class);

            if (getConfiguration().isDeleteAfterRead() || getConfiguration().isMoveAfterRead()) {
                if (getConfiguration().isMoveAfterRead()) {
                    copyObject(srcBucketName, srcObjectName);
                    LOG.trace("Copied object from bucket {} with objectName {} to bucket {}...",
                            srcBucketName, srcObjectName, getConfiguration().getDestinationBucketName());
                }

                LOG.trace("Deleting object from bucket {} with objectName {}...", srcBucketName, srcObjectName);
                removeObject(srcBucketName, srcObjectName);
                LOG.trace("Deleted object from bucket {} with objectName {}...", srcBucketName, srcObjectName);
            }
        } catch (MinioException | NoSuchAlgorithmException | InvalidKeyException | IOException e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.",
                    exchange, e);
        }
    }

    private void removeObject(String srcBucketName, String srcObjectName)
            throws MinioException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        RemoveObjectArgs.Builder removeObjectRequest = RemoveObjectArgs.builder()
                .bucket(srcBucketName)
                .object(srcObjectName)
                .bypassGovernanceMode(getConfiguration().isBypassGovernanceMode());

        if (isNotEmpty(getConfiguration().getVersionId())) {
            removeObjectRequest.versionId(getConfiguration().getVersionId());
        }

        getMinioClient().removeObject(removeObjectRequest.build());
    }

    private void copyObject(String srcBucketName, String srcObjectName)
            throws MinioException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        String destinationBucketName = getConfiguration().getDestinationBucketName();
        if (isEmpty(destinationBucketName)) {
            throw new IllegalArgumentException("Destination Bucket name must be specified to copy operation");
        }

        // set destination object name as source object name, if not specified
        String destinationObjectName = (isNotEmpty(getConfiguration().getDestinationObjectName()))
                ? getConfiguration().getDestinationObjectName()
                : srcObjectName;

        LOG.trace("Copying object from bucket {} with objectName {} to bucket {}...",
                srcBucketName, srcObjectName, destinationBucketName);

        CopySource.Builder copySourceBuilder = CopySource.builder().bucket(srcBucketName).object(srcObjectName);

        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getServerSideEncryptionCustomerKey,
                copySourceBuilder::ssec);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getOffset, copySourceBuilder::offset);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getLength, copySourceBuilder::length);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getVersionId, copySourceBuilder::versionId);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getMatchETag, copySourceBuilder::matchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getNotMatchETag,
                copySourceBuilder::notMatchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getModifiedSince,
                copySourceBuilder::modifiedSince);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getUnModifiedSince,
                copySourceBuilder::unmodifiedSince);

        CopyObjectArgs.Builder copyObjectRequest = CopyObjectArgs.builder()
                .source(copySourceBuilder.build())
                .bucket(getConfiguration().getDestinationBucketName())
                .object(destinationObjectName);

        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getServerSideEncryption, copyObjectRequest::sse);

        getMinioClient().copyObject(copyObjectRequest.build());
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (isNotEmpty(cause)) {
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

    private Exchange createExchange(String objectName) throws Exception {
        LOG.trace("Getting object with objectName {} from bucket {}...", objectName, getConfiguration().getBucketName());

        Exchange exchange = createExchange(true);
        exchange.setPattern(getEndpoint().getExchangePattern());
        Message message = exchange.getIn();
        LOG.trace("Got object!");

        getEndpoint().getObjectStat(objectName, message);

        return exchange;
    }

    @Override
    public String toString() {
        if (isEmpty(minioConsumerToString)) {
            minioConsumerToString = "MinioConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return minioConsumerToString;
    }
}
