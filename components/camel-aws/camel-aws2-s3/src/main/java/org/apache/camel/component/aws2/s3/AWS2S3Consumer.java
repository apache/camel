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
package org.apache.camel.component.aws2.s3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest.Builder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.utils.IoUtils;

/**
 * A Consumer of messages from the Amazon Web Service Simple Storage Service <a href="http://aws.amazon.com/s3/">AWS
 * S3</a>
 */
public class AWS2S3Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Consumer.class);

    private String marker;
    private transient String s3ConsumerToString;

    public AWS2S3Consumer(AWS2S3Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().isMoveAfterRead()) {
            try {
                getAmazonS3Client()
                        .headBucket(HeadBucketRequest.builder().bucket(getConfiguration().getDestinationBucket()).build());
                LOG.trace("Bucket [{}] already exists", getConfiguration().getDestinationBucket());
                return;
            } catch (AwsServiceException ase) {
                /* 404 means the bucket doesn't exist */
                if (ase.awsErrorDetails().sdkHttpResponse().statusCode() != 404) {
                    throw ase;
                }
            }

            LOG.trace("Destination Bucket [{}] doesn't exist yet", getConfiguration().getDestinationBucket());

            if (getConfiguration().isAutoCreateBucket()) {
                // creates the new bucket because it doesn't exist yet
                CreateBucketRequest createBucketRequest
                        = CreateBucketRequest.builder().bucket(getConfiguration().getDestinationBucket()).build();

                LOG.trace("Creating Destination bucket [{}] in region [{}] with request [{}]...",
                        getConfiguration().getDestinationBucket(), getConfiguration().getRegion(),
                        createBucketRequest);

                getAmazonS3Client().createBucket(createBucketRequest);

                LOG.trace("Destination Bucket created");
            }
        }
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String fileName = getConfiguration().getFileName();
        String bucketName = getConfiguration().getBucketName();
        String doneFileName = getConfiguration().getDoneFileName();
        Queue<Exchange> exchanges;

        if (!doneFileCheckPasses(bucketName, doneFileName)) {
            exchanges = new LinkedList<>();
        } else if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            ResponseInputStream<GetObjectResponse> s3Object
                    = getAmazonS3Client().getObject(GetObjectRequest.builder().bucket(bucketName).key(fileName).build());
            exchanges = createExchanges(s3Object, fileName);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            ListObjectsRequest.Builder listObjectsRequest = ListObjectsRequest.builder();
            listObjectsRequest.bucket(bucketName);
            listObjectsRequest.prefix(getConfiguration().getPrefix());
            listObjectsRequest.delimiter(getConfiguration().getDelimiter());

            if (maxMessagesPerPoll > 0) {
                listObjectsRequest.maxKeys(maxMessagesPerPoll);
            }
            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            if (marker != null) {
                LOG.trace("Resuming from marker: {}", marker);
                listObjectsRequest.marker(marker);
            }

            ListObjectsResponse listObjects = getAmazonS3Client().listObjects(listObjectsRequest.build());

            if (Boolean.TRUE.equals(listObjects.isTruncated())) {
                marker = listObjects.nextMarker();
                LOG.trace("Returned list is truncated, so setting next marker: {}", marker);
            } else {
                // no more data so clear marker
                marker = null;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", listObjects.contents().size(), bucketName);
            }

            exchanges = createExchanges(listObjects.contents());
        }

        // okay we have some response from azure so lets mark the consumer as ready
        forceConsumerAsReady();

        return processBatch(CastUtils.cast(exchanges));
    }

    private boolean doneFileCheckPasses(String bucketName, String doneFileName) {
        if (doneFileName == null) {
            return true;
        } else {
            return checkFileExists(bucketName, doneFileName);
        }
    }

    private boolean checkFileExists(String bucketName, String doneFileName) {
        HeadObjectRequest.Builder headObjectsRequest = HeadObjectRequest.builder();
        headObjectsRequest.bucket(bucketName);
        headObjectsRequest.key(doneFileName);
        try {
            getAmazonS3Client().headObject(headObjectsRequest.build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    protected Queue<Exchange> createExchanges(ResponseInputStream<GetObjectResponse> s3Object, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = createExchange(s3Object, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<S3Object> s3ObjectSummaries) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", s3ObjectSummaries.size());
        }

        Collection<ResponseInputStream<GetObjectResponse>> s3Objects = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<>();
        try {
            for (S3Object s3ObjectSummary : s3ObjectSummaries) {
                Builder getRequest
                        = GetObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(s3ObjectSummary.key());
                if (getConfiguration().isUseCustomerKey()) {
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyId())) {
                        getRequest.sseCustomerKey(getConfiguration().getCustomerKeyId());
                    }
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyMD5())) {
                        getRequest.sseCustomerKeyMD5(getConfiguration().getCustomerKeyMD5());
                    }
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerAlgorithm())) {
                        getRequest.sseCustomerAlgorithm(getConfiguration().getCustomerAlgorithm());
                    }
                }
                ResponseInputStream<GetObjectResponse> s3Object
                        = getAmazonS3Client().getObject(getRequest.build(), ResponseTransformer.toInputStream());

                if (includeS3Object(s3Object)) {
                    s3Objects.add(s3Object);
                    Exchange exchange = createExchange(s3Object, s3ObjectSummary.key());
                    answer.add(exchange);
                } else {
                    // If includeFolders != true and the object is not included, it is safe to close the object here.
                    // If includeFolders == true, the exchange will close the object.
                    IOHelper.close(s3Object);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error getting S3Object due: {}", e.getMessage(), e);
            // ensure all previous gathered s3 objects are closed
            // if there was an exception creating the exchanges in this batch
            s3Objects.forEach(IOHelper::close);
            throw e;
        }

        return answer;
    }

    /**
     * Decide whether to include the S3Objects in the results
     *
     * @param  s3Object
     * @return          true to include, false to exclude
     */
    protected boolean includeS3Object(ResponseInputStream<GetObjectResponse> s3Object) {
        if (getConfiguration().isIncludeFolders()) {
            return true;
        } else {
            // Config says to ignore folders/directories
            return !Optional.of(s3Object.response().contentType()).orElse("")
                    .toLowerCase().startsWith("application/x-directory");
        }
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
                    return "S3ConsumerOnCompletion";
                }
            });

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
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
                String bucketName = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(AWS2S3Constants.KEY, String.class);

                LOG.trace("Moving object from bucket {} with key {} to bucket {}...", bucketName, key,
                        getConfiguration().getDestinationBucket());

                StringBuilder builder = new StringBuilder();

                if (ObjectHelper.isNotEmpty(getConfiguration().getDestinationBucketPrefix())) {
                    builder.append(getConfiguration().getDestinationBucketPrefix());
                }
                builder.append(key);
                if (ObjectHelper.isNotEmpty(getConfiguration().getDestinationBucketSuffix())) {
                    builder.append(getConfiguration().getDestinationBucketSuffix());
                }
                getAmazonS3Client().copyObject(CopyObjectRequest.builder().destinationKey(builder.toString())
                        .destinationBucket(getConfiguration().getDestinationBucket())
                        .sourceBucket(bucketName)
                        .sourceKey(key)
                        .build());

                LOG.trace("Moved object from bucket {} with key {} to bucket {}...", bucketName, key,
                        getConfiguration().getDestinationBucket());
            }
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(AWS2S3Constants.KEY, String.class);

                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);

                getAmazonS3Client().deleteObject(
                        DeleteObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(key).build());

                LOG.trace("Deleted object from bucket {} with key {}...", bucketName, key);
            }
        } catch (AwsServiceException e) {
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

    protected AWS2S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected S3Client getAmazonS3Client() {
        return getEndpoint().getS3Client();
    }

    @Override
    public AWS2S3Endpoint getEndpoint() {
        return (AWS2S3Endpoint) super.getEndpoint();
    }

    public Exchange createExchange(ResponseInputStream<GetObjectResponse> s3Object, String key) {
        return createExchange(getEndpoint().getExchangePattern(), s3Object, key);
    }

    public Exchange createExchange(ExchangePattern pattern, ResponseInputStream<GetObjectResponse> s3Object, String key) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", key, getConfiguration().getBucketName());

        LOG.trace("Got object [{}]", s3Object);

        Exchange exchange = createExchange(true);
        exchange.setPattern(pattern);
        Message message = exchange.getIn();

        if (!getConfiguration().isIgnoreBody()) {
            if (getConfiguration().isIncludeBody()) {
                try {
                    message.setBody(IoUtils.toByteArray(s3Object));
                } catch (IOException e) {
                    throw new RuntimeCamelException(e);
                }
            } else {
                message.setBody(s3Object);
            }
        }

        message.setHeader(AWS2S3Constants.KEY, key);
        message.setHeader(AWS2S3Constants.BUCKET_NAME, getConfiguration().getBucketName());
        message.setHeader(AWS2S3Constants.E_TAG, s3Object.response().eTag());
        message.setHeader(AWS2S3Constants.VERSION_ID, s3Object.response().versionId());
        message.setHeader(AWS2S3Constants.CONTENT_TYPE, s3Object.response().contentType());
        message.setHeader(AWS2S3Constants.CONTENT_LENGTH, s3Object.response().contentLength());
        message.setHeader(AWS2S3Constants.CONTENT_ENCODING, s3Object.response().contentEncoding());
        message.setHeader(AWS2S3Constants.CONTENT_DISPOSITION, s3Object.response().contentDisposition());
        message.setHeader(AWS2S3Constants.CACHE_CONTROL, s3Object.response().cacheControl());
        message.setHeader(AWS2S3Constants.SERVER_SIDE_ENCRYPTION, s3Object.response().serverSideEncryption());
        message.setHeader(AWS2S3Constants.EXPIRATION_TIME, s3Object.response().expiration());
        message.setHeader(AWS2S3Constants.REPLICATION_STATUS, s3Object.response().replicationStatus());
        message.setHeader(AWS2S3Constants.STORAGE_CLASS, s3Object.response().storageClass());
        message.setHeader(AWS2S3Constants.METADATA, s3Object.response().metadata());
        if (s3Object.response().lastModified() != null) {
            message.setHeader(AWS2S3Constants.LAST_MODIFIED, s3Object.response().lastModified());
            long ts = s3Object.response().lastModified().getEpochSecond() * 1000;
            message.setHeader(AWS2S3Constants.MESSAGE_TIMESTAMP, ts);
        }

        /*
         * If includeBody == true, it is safe to close the object here because the S3Object
         * was consumed already. If includeBody != true, the caller is responsible for
         * closing the stream once the body has been fully consumed or use the autoCloseBody
         * configuration to automatically schedule the body closing at the end of exchange.
         */
        if (getConfiguration().isIncludeBody()) {
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

        return exchange;
    }

    @Override
    public String toString() {
        if (s3ConsumerToString == null) {
            s3ConsumerToString = "S3Consumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return s3ConsumerToString;
    }
}
