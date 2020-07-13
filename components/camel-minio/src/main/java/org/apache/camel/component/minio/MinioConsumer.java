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

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.MinioException;
import io.minio.messages.Contents;
import io.minio.messages.ListBucketResultV2;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
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

    private String continuationToken;
    private transient String minioConsumerToString;

    public MinioConsumer(MinioEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String bucketName = getConfiguration().getBucketName();
        String objectName = getConfiguration().getObjectName();
        MinioClient minioClient = getMinioClient();
        Queue<Exchange> exchanges = null;

        if (bucketExists(minioClient, bucketName)) {
            LOG.trace("Bucket {} exists", bucketName);
        } else {
            throw new InvalidBucketNameException("Bucket {} does not exists", bucketName);
        }

        if (objectName != null) {
            LOG.trace("Getting object in bucket {} with object name {}...", bucketName, objectName);

            try {
                InputStream minioObject = getObject(bucketName, minioClient, objectName);
                if (minioObject != null) {
                    exchanges = createExchanges(minioObject, objectName);
                }

            } catch (Throwable e) {
                LOG.warn("Failed to create exchanges in bucket {} with object name {}, Error message {}", bucketName, objectName, e.getMessage());
                throw e;

            }
        } else {

            try {
                LOG.trace("Queueing objects in bucket [{}]...", bucketName);

                ListObjectsArgs.Builder listObjectRequest = ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .includeUserMetadata(getConfiguration().isIncludeUserMetadata())
                        .includeVersions(getConfiguration().isIncludeVersions())
                        .recursive(getConfiguration().isRecursive())
                        .useApiVersion1(getConfiguration().isUseVersion1());

                if (getConfiguration().getDelimiter() != null) {
                    listObjectRequest.delimiter(getConfiguration().getDelimiter());
                }

                if (maxMessagesPerPoll > 0) {
                    listObjectRequest.maxKeys(maxMessagesPerPoll);
                }

                if (getConfiguration().getPrefix() != null) {
                    listObjectRequest.prefix(getConfiguration().getPrefix());
                }

                if (getConfiguration().getStartAfter() != null) {
                    listObjectRequest.startAfter(getConfiguration().getStartAfter());
                }

                // if there was a marker from previous poll then use that to
                // continue from where we left last time
                if (continuationToken != null) {
                    LOG.trace("Resuming from marker: {}", continuationToken);
                    listObjectRequest.continuationToken(continuationToken);
                }

                // TODO: Check for validity of the statement
                ListBucketResultV2 listObjects = (ListBucketResultV2) getMinioClient().listObjects(listObjectRequest.build());

                if (listObjects.isTruncated()) {
                    continuationToken = listObjects.nextContinuationToken();
                    LOG.trace("Returned list is truncated, so setting next marker: {}", continuationToken);
                } else {
                    // no more data so clear marker
                    continuationToken = null;
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Found {} objects in bucket [{}]...", listObjects.contents().size(), bucketName);
                }

                exchanges = createExchanges(listObjects.contents());
            } catch (Throwable e) {
                LOG.warn("Failed to create exchanges in bucket {} with object list, Error message {}", bucketName, e.getMessage());
                throw e;
            }
        }

        if (CastUtils.cast(exchanges) != null) {
            return processBatch(CastUtils.cast(exchanges));
        } else {
            throw new IllegalAccessException("Cannot process null exchanges");
        }
    }

    protected Queue<Exchange> createExchanges(InputStream objectStream, String objectName) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(objectStream, objectName);
        answer.add(exchange);
        IOHelper.close(objectStream);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<Contents> minioObjectSummaries) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", minioObjectSummaries.size());
        }

        Collection<InputStream> minioObjects = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<>();
        try {
            GetObjectArgs.Builder minioObjectRequest = GetObjectArgs.builder().bucket(getConfiguration().getBucketName());
            if (getConfiguration().isIncludeFolders()) {
                for (Contents minioObjectSummary : minioObjectSummaries) {
                    InputStream minioObject = getMinioClient().getObject(minioObjectRequest
                            .object(minioObjectSummary.objectName())
                            .build());
                    minioObjects.add(minioObject);
                    Exchange exchange = getEndpoint().createExchange(minioObject, minioObjectSummary.objectName());
                    answer.add(exchange);

                }
            } else {
                for (Contents minioObjectSummary : minioObjectSummaries) {
                    // ignore if directory
                    if (!minioObjectSummary.isDir()) {
                        InputStream minioObject = getMinioClient().getObject(minioObjectRequest
                                .object(minioObjectSummary.objectName())
                                .build());
                        minioObjects.add(minioObject);
                        Exchange exchange = getEndpoint().createExchange(minioObject, minioObjectSummary.objectName());
                        answer.add(exchange);
                    }
                }
            }

        } catch (Throwable e) {
            LOG.warn("Error getting MinioObject due: {}", e.getMessage());
            throw e;

        } finally {
            // ensure all previous gathered minio objects are closed
            // if there was an exception creating the exchanges in this batch
            minioObjects.forEach(IOHelper::close);
        }

        return answer;
    }

    private boolean bucketExists(MinioClient minioClient, String bucketName) throws Exception {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());

        } catch (Throwable e) {
            LOG.warn("Error checking bucket, due: {}", e.getMessage());
            throw e;
        }
    }

    private InputStream getObject(String bucketName, MinioClient minioClient, String objectName) throws Exception {
        try {
            GetObjectArgs.Builder getObjectRequest = GetObjectArgs.builder().bucket(bucketName).object(objectName);

            if (getConfiguration().getServerSideEncryptionCustomerKey() != null) {
                getObjectRequest.ssec(getConfiguration().getServerSideEncryptionCustomerKey());
            }
            if (getConfiguration().getOffset() != 0) {
                getObjectRequest.offset(getConfiguration().getOffset());
            }
            if (getConfiguration().getLength() != 0) {
                getObjectRequest.length(getConfiguration().getLength());
            }
            if (getConfiguration().getVersionId() != null) {
                getObjectRequest.versionId(getConfiguration().getVersionId());
            }
            if (getConfiguration().getMatchETag() != null) {
                getObjectRequest.matchETag(getConfiguration().getMatchETag());
            }
            if (getConfiguration().getNotMatchETag() != null) {
                getObjectRequest.notMatchETag(getConfiguration().getNotMatchETag());
            }
            if (getConfiguration().getModifiedSince() != null) {
                getObjectRequest.modifiedSince(getConfiguration().getModifiedSince());
            }
            if (getConfiguration().getUnModifiedSince() != null) {
                getObjectRequest.unmodifiedSince(getConfiguration().getUnModifiedSince());
            }

            return minioClient.getObject(getObjectRequest.build());
        } catch (Throwable e) {
            LOG.warn("Error getting object: {} in bucket: {}, due: {}", objectName, bucketName, e.getMessage());
            throw e;
        }
    }

    @Override
    public int processBatch(Queue<Object> exchanges) {
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

            LOG.trace("Processing exchange ...");
            getAsyncProcessor().process(exchange, doneSync -> LOG.trace("Processing exchange done."));
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
            if (getConfiguration().isMoveAfterRead()) {
                copyObject(srcBucketName, srcObjectName);
                LOG.trace("Copied object from bucket {} with objectName {} to bucket {}...",
                        srcBucketName, srcObjectName, getConfiguration().getDestinationBucketName());
            }
            if (getConfiguration().isDeleteAfterRead() || getConfiguration().isMoveAfterRead()) {

                LOG.trace("Deleting object from bucket {} with objectName {}...", srcBucketName, srcObjectName);

                RemoveObjectArgs.Builder removeObjectRequest = RemoveObjectArgs.builder()
                        .bucket(srcBucketName)
                        .object(srcObjectName)
                        .bypassGovernanceMode(getConfiguration().isBypassGovernanceMode());

                if (getConfiguration().getVersionId() != null) {
                    removeObjectRequest.versionId(getConfiguration().getVersionId());
                }
                if (getConfiguration().isBypassGovernanceMode()) {
                    removeObjectRequest.versionId(getConfiguration().getVersionId());
                }
                getMinioClient().removeObject(removeObjectRequest.build());

                LOG.trace("Deleted object from bucket {} with objectName {}...", srcBucketName, srcObjectName);
            }
        } catch (MinioException e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.",
                    exchange, e);
        } catch (Exception e) {
            LOG.trace("Error process commit...");
        }
    }

    private void copyObject(String srcBucketName, String srcObjectName) {
        String destinationBucketName = getConfiguration().getDestinationBucketName();
        if (destinationBucketName == null) {
            throw new IllegalArgumentException("Destination Bucket name must be specified to copy operation");
        }

        try {
            // set destination object name as source object name, if not specified
            String destinationObjectName = (getConfiguration().getDestinationObjectName() != null)
                    ? getConfiguration().getDestinationObjectName()
                    : srcObjectName;


            LOG.trace("Copying object from bucket {} with objectName {} to bucket {}...",
                    srcBucketName, srcObjectName, destinationBucketName);

            CopySource.Builder copySource = CopySource.builder().bucket(srcBucketName).object(srcObjectName);
            if (getConfiguration().getServerSideEncryptionCustomerKey() != null) {
                copySource.ssec(getConfiguration().getServerSideEncryptionCustomerKey());
            }
            if (getConfiguration().getOffset() != 0) {
                copySource.offset(getConfiguration().getOffset());
            }
            if (getConfiguration().getLength() != 0) {
                copySource.length(getConfiguration().getLength());
            }
            if (getConfiguration().getVersionId() != null) {
                copySource.versionId(getConfiguration().getVersionId());
            }
            if (getConfiguration().getMatchETag() != null) {
                copySource.matchETag(getConfiguration().getMatchETag());
            }
            if (getConfiguration().getNotMatchETag() != null) {
                copySource.notMatchETag(getConfiguration().getNotMatchETag());
            }
            if (getConfiguration().getModifiedSince() != null) {
                copySource.modifiedSince(getConfiguration().getModifiedSince());
            }
            if (getConfiguration().getUnModifiedSince() != null) {
                copySource.unmodifiedSince(getConfiguration().getUnModifiedSince());
            }

            CopyObjectArgs.Builder copyObjectRequest = CopyObjectArgs.builder()
                    .source(copySource.build())
                    .bucket(getConfiguration().getDestinationBucketName())
                    .object(destinationObjectName);

            if (getConfiguration().getServerSideEncryption() != null) {
                copyObjectRequest.sse(getConfiguration().getServerSideEncryption());
            }

            getMinioClient().copyObject(copyObjectRequest.build());

        } catch (Exception e) {
            LOG.warn("Error copy object from bucket {} with objectName {} to bucket {}...",
                    srcBucketName, srcObjectName, destinationBucketName);
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
