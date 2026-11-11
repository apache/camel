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

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.PooledExchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws2.s3.utils.AWS2S3Utils;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
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
    private boolean markerIsContinuationToken = true;
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
                if (ase.statusCode() != 404) {
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
        Queue<Object> batch;

        if (!doneFileCheckPasses(bucketName, doneFileName)) {
            batch = new LinkedList<>();
        } else if (fileName != null) {
            // check if file is already in progress (add false = duplicate file)
            if (!getEndpoint().getInProgressRepository().add(fileName)) {
                LOG.trace("Skipping as s3 object is already in progress: {}", fileName);
                batch = new LinkedList<>();
            } else {
                ResponseInputStream<GetObjectResponse> s3Object = null;
                try {
                    if (getConfiguration().isIgnoreBody()) {
                        batch = new LinkedList<>();
                        batch.add(createExchange(headObject(fileName), fileName));
                    } else {
                        s3Object = getObject(fileName);
                        batch = new LinkedList<>(createExchanges(s3Object, fileName));
                    }
                } catch (Exception e) {
                    // close the stream to release the underlying HTTP connection
                    IOHelper.close(s3Object);
                    // remove in progress as we failed
                    getEndpoint().getInProgressRepository().remove(fileName);
                    throw e;
                }
            }
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            ListObjectsV2Request.Builder listObjectsRequest = ListObjectsV2Request.builder();
            listObjectsRequest.bucket(bucketName);
            if (ObjectHelper.isNotEmpty(getConfiguration().getPrefix())) {
                listObjectsRequest.prefix(getConfiguration().getPrefix());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getDelimiter())) {
                listObjectsRequest.delimiter(getConfiguration().getDelimiter());
            }

            if (maxMessagesPerPoll > 0) {
                listObjectsRequest.maxKeys(maxMessagesPerPoll);
            }
            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            if (marker != null) {
                LOG.trace("Resuming from marker: {}", marker);
                if (markerIsContinuationToken) {
                    listObjectsRequest.continuationToken(marker);
                } else {
                    listObjectsRequest.startAfter(marker);
                }
            }

            ListObjectsV2Response listObjects = getAmazonS3Client().listObjectsV2(listObjectsRequest.build());

            if (Boolean.TRUE.equals(listObjects.isTruncated())) {
                String next = listObjects.nextContinuationToken();
                if (next != null) {
                    markerIsContinuationToken = true;
                } else if (listObjects.hasContents()) {
                    // S3-compatible stores may omit nextContinuationToken;
                    // fall back to startAfter with the last returned key
                    int size = listObjects.contents().size();
                    if (size > 0) {
                        S3Object last = listObjects.contents().get(size - 1);
                        next = last.key();
                    }
                    markerIsContinuationToken = false;
                }
                marker = next;
                LOG.trace("Returned list is truncated, so setting next marker: {}", marker);
            } else {
                // no more data so clear marker
                marker = null;
                markerIsContinuationToken = true;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", listObjects.contents().size(), bucketName);
            }

            batch = takeListedObjects(listObjects.contents());
        }

        // okay we have some response from azure so lets mark the consumer as ready
        forceConsumerAsReady();

        return processBatch(batch);
    }

    private GetObjectRequest getObjectRequest(String key) {
        GetObjectRequest.Builder getRequest = GetObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(key);
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
        return getRequest.build();
    }

    private ResponseInputStream<GetObjectResponse> getObject(String key) {
        LOG.trace("Getting object in bucket [{}] with key [{}]...", getConfiguration().getBucketName(), key);
        return getAmazonS3Client().getObject(getObjectRequest(key));
    }

    /**
     * Gets only the metadata of the object (ignoreBody=true): a HEAD request does not download the content.
     */
    private HeadObjectResponse headObject(String key) {
        LOG.trace("Getting the metadata of object in bucket [{}] with key [{}]...", getConfiguration().getBucketName(),
                key);
        return getAmazonS3Client().headObject(AWS2S3Utils.toHeadObjectRequest(getObjectRequest(key)));
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

    /**
     * Takes the listed objects that are not in progress. Each of them is got by {@link #processBatch(Queue)} right
     * before its exchange is routed, so that only the objects of the exchanges in flight are in memory, or hold a
     * connection of the S3 client (with includeBody=false).
     */
    private Queue<Object> takeListedObjects(List<S3Object> s3ObjectSummaries) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", s3ObjectSummaries.size());
        }

        Queue<Object> answer = new LinkedList<>();
        try {
            for (S3Object s3ObjectSummary : s3ObjectSummaries) {
                String key = s3ObjectSummary.key();

                // check if file is already in progress (add false = duplicate file)
                if (!getEndpoint().getInProgressRepository().add(key)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Skipping as s3 object is already in progress: {}", key);
                    }
                    continue;
                }

                LOG.debug("S3Object to be consumed: {}", key);
                answer.add(s3ObjectSummary);
            }
        } catch (RuntimeException e) {
            // remove all in-progress as we failed
            for (Object taken : answer) {
                getEndpoint().getInProgressRepository().remove(((S3Object) taken).key());
            }
            throw e;
        }
        return answer;
    }

    /**
     * Gets the listed object and creates its exchange, or returns null when the object is not included (a folder).
     */
    private Exchange getListedObject(String key) {
        if (getConfiguration().isIgnoreBody()) {
            HeadObjectResponse head = headObject(key);
            return includeContentType(head.contentType()) ? createExchange(head, key) : null;
        }
        ResponseInputStream<GetObjectResponse> s3Object = getObject(key);
        try {
            if (!includeS3Object(s3Object)) {
                // If includeFolders != true and the object is not included, it is safe to close the object here.
                // If includeFolders == true, the exchange will close the object.
                IOHelper.close(s3Object);
                return null;
            }
            return createExchange(s3Object, key);
        } catch (RuntimeException e) {
            // close the stream to release the underlying HTTP connection
            IOHelper.close(s3Object);
            throw e;
        }
    }

    /**
     * Decide whether to include the S3Objects in the results
     *
     * @return true to include, false to exclude
     */
    protected boolean includeS3Object(ResponseInputStream<GetObjectResponse> s3Object) {
        return includeContentType(s3Object.response().contentType());
    }

    private boolean includeContentType(String contentType) {
        if (getConfiguration().isIncludeFolders()) {
            return true;
        } else {
            // Config says to ignore folders/directories
            return !Optional.ofNullable(contentType).orElse("")
                    .toLowerCase().startsWith("application/x-directory");
        }
    }

    /**
     * Routes the exchanges of the batch. A listed object ({@link S3Object}) is got right before its exchange is routed.
     * The batch properties count the listed objects: an object that is skipped when it is got (it no longer exists, it
     * cannot be got, or it is a folder) leaves a gap in {@code CamelBatchIndex}, and when it is the last listed object,
     * no exchange of the batch has {@code CamelBatchComplete} set to true.
     */
    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        // the listed objects that were skipped when they were got
        int skipped = 0;
        // the listed objects that could not be got, other than those that no longer exist, by key
        Map<String, RuntimeException> failures = new LinkedHashMap<>();
        // whether S3 answered the get of at least one listed object
        boolean anyGot = false;

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Object next = exchanges.poll();

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            Exchange exchange;
            if (next instanceof S3Object listed) {
                String key = listed.key();
                // a failure to get one listed object, or to create its exchange, does not fail the poll as long as
                // another listed object could be got: the object is skipped, the other listed objects are consumed,
                // and a later poll tries it again
                try {
                    exchange = getListedObject(key);
                    anyGot = true;
                } catch (NoSuchKeyException e) {
                    // deleted after it was listed (by another consumer, a lifecycle rule, or the exchange of an
                    // earlier poll that completed meanwhile)
                    LOG.debug("Skipping as s3 object no longer exists: {}", key);
                    exchange = null;
                } catch (RuntimeException e) {
                    failures.put(key, e);
                    exchange = null;
                }
                if (exchange == null) {
                    // not consumed by this poll (it no longer exists, it could not be got, or it is a folder that is
                    // not included): a later poll can consume it
                    getEndpoint().getInProgressRepository().remove(key);
                    skipped++;
                    continue;
                }
            } else {
                exchange = ObjectHelper.cast(Exchange.class, next);
            }

            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    final String key = exchange.getProperty(AWS2S3Constants.KEY, String.class);
                    try {
                        processCommit(exchange);
                    } finally {
                        if (key != null) {
                            getEndpoint().getInProgressRepository().remove(key);
                        }
                    }
                }

                public void onFailure(Exchange exchange) {
                    final String key = exchange.getProperty(AWS2S3Constants.KEY, String.class);
                    try {
                        processRollback(exchange);
                    } finally {
                        if (key != null) {
                            getEndpoint().getInProgressRepository().remove(key);
                        }
                    }
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
        // the remaining exchanges are not processed as the consumer is stopping: a later poll must consume their
        // objects again, and with includeBody=false their bodies are the object streams, which nothing else closes
        for (Object notRouted : exchanges) {
            if (notRouted instanceof S3Object listed) {
                // not got yet
                getEndpoint().getInProgressRepository().remove(listed.key());
                continue;
            }
            Exchange exchange = (Exchange) notRouted;
            String key = exchange.getProperty(AWS2S3Constants.KEY, String.class);
            if (key != null) {
                getEndpoint().getInProgressRepository().remove(key);
            }
            if (exchange.getIn().getBody() instanceof Closeable body) {
                IOHelper.close(body);
            }
            // nothing else releases an exchange that is never routed to the exchange factory (which resets a pooled
            // exchange, so this comes last)
            releaseExchange(exchange, true);
        }

        if (!failures.isEmpty()) {
            if (!anyGot) {
                // none of the listed objects could be got, so this is not about one object (expired credentials, no
                // permission to get, throttling, no connection): the poll fails as it does when the listing fails, and
                // the error handling and the backoff of the scheduled consumer apply
                Map.Entry<String, RuntimeException> first = failures.entrySet().iterator().next();
                LOG.warn("None of the listed objects of bucket {} could be got ({} failed, the first is {}): the poll fails",
                        getConfiguration().getBucketName(), failures.size(), first.getKey());
                throw first.getValue();
            }
            // as processCommit does: the consumer goes on, and a custom exception handler or bridgeErrorHandler sees
            // the failure
            for (Map.Entry<String, RuntimeException> failure : failures.entrySet()) {
                String message = "Error getting S3Object " + failure.getKey() + " from bucket "
                                 + getConfiguration().getBucketName()
                                 + ". The object is skipped for this poll.";
                getExceptionHandler().handleException(message, failure.getValue());
            }
        }

        // the skipped objects were not polled
        return total - skipped;
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

                builder.append(AWS2S3Utils.evaluateDestinationBucketPrefix(exchange, getConfiguration()));
                builder.append(key);
                builder.append(AWS2S3Utils.evaluateDestinationBucketSuffix(exchange, getConfiguration()));

                String destinationKey = builder.toString();
                if (getConfiguration().isRemovePrefixOnMove()
                        && ObjectHelper.isNotEmpty(getConfiguration().getPrefix())
                        && destinationKey.startsWith(getConfiguration().getPrefix())) {
                    destinationKey = destinationKey.substring(getConfiguration().getPrefix().length());
                }

                getAmazonS3Client().copyObject(CopyObjectRequest.builder().destinationKey(destinationKey)
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
        try {
            populateExchange(exchange, pattern, s3Object, key);
        } catch (RuntimeException e) {
            // the object is not consumed (for example its download failed), so the exchange created for it is not
            // routed, and nothing else gives it back to the exchange factory
            if (exchange instanceof PooledExchange pooled) {
                // created with autoRelease, so done() also gives it back to the pool, once: releaseExchange would
                // then add it to the pool a second time, and two later objects would share the exchange
                pooled.done();
            } else {
                releaseExchange(exchange, true);
            }
            throw e;
        }
        return exchange;
    }

    private void populateExchange(
            Exchange exchange, ExchangePattern pattern, ResponseInputStream<GetObjectResponse> s3Object, String key) {
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

        message.getExchange().setProperty(AWS2S3Constants.KEY, key);
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
         * was consumed already. The same goes for ignoreBody == true, as the stream is then not
         * the body, so nothing else can read or close it. Otherwise the caller is responsible for
         * closing the stream once the body has been fully consumed or use the autoCloseBody
         * configuration to automatically schedule the body closing at the end of exchange.
         */
        if (getConfiguration().isIncludeBody() || getConfiguration().isIgnoreBody()) {
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
    }

    /**
     * Creates the exchange of an object of which only the metadata was got (ignoreBody=true): it has no body, and the
     * same headers as the exchange of an object that was got.
     */
    private Exchange createExchange(HeadObjectResponse head, String key) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(getEndpoint().getExchangePattern());
        Message message = exchange.getIn();
        exchange.setProperty(AWS2S3Constants.KEY, key);
        message.setHeader(AWS2S3Constants.KEY, key);
        message.setHeader(AWS2S3Constants.BUCKET_NAME, getConfiguration().getBucketName());
        message.setHeader(AWS2S3Constants.E_TAG, head.eTag());
        message.setHeader(AWS2S3Constants.VERSION_ID, head.versionId());
        message.setHeader(AWS2S3Constants.CONTENT_TYPE, head.contentType());
        message.setHeader(AWS2S3Constants.CONTENT_LENGTH, head.contentLength());
        message.setHeader(AWS2S3Constants.CONTENT_ENCODING, head.contentEncoding());
        message.setHeader(AWS2S3Constants.CONTENT_DISPOSITION, head.contentDisposition());
        message.setHeader(AWS2S3Constants.CACHE_CONTROL, head.cacheControl());
        message.setHeader(AWS2S3Constants.SERVER_SIDE_ENCRYPTION, head.serverSideEncryption());
        message.setHeader(AWS2S3Constants.EXPIRATION_TIME, head.expiration());
        message.setHeader(AWS2S3Constants.REPLICATION_STATUS, head.replicationStatus());
        message.setHeader(AWS2S3Constants.STORAGE_CLASS, head.storageClass());
        message.setHeader(AWS2S3Constants.METADATA, head.metadata());
        if (head.lastModified() != null) {
            message.setHeader(AWS2S3Constants.LAST_MODIFIED, head.lastModified());
            message.setHeader(AWS2S3Constants.MESSAGE_TIMESTAMP, head.lastModified().getEpochSecond() * 1000);
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
