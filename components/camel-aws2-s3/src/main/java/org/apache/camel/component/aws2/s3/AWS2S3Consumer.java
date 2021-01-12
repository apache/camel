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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
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
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * A Consumer of messages from the Amazon Web Service Simple Storage Service <a href="http://aws.amazon.com/s3/">AWS
 * S3</a>
 */
public class AWS2S3Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Consumer.class);

    private String marker;
    private transient String s3ConsumerToString;

    public AWS2S3Consumer(AWS2S3Endpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().isMoveAfterRead()) {
            try {
                ListObjectsRequest.Builder builder = ListObjectsRequest.builder();
                builder.bucket(getConfiguration().getDestinationBucket());
                builder.maxKeys(maxMessagesPerPoll);
                getAmazonS3Client().listObjects(builder.build());
                LOG.trace("Bucket [{}] already exists", getConfiguration().getDestinationBucket());
                return;
            } catch (AwsServiceException ase) {
                /* 404 means the bucket doesn't exist */
                if (ase.awsErrorDetails().errorCode().equalsIgnoreCase("404")) {
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
        Queue<Exchange> exchanges;

        if (fileName != null) {
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

            if (listObjects.isTruncated()) {
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
        return processBatch(CastUtils.cast(exchanges));
    }

    protected Queue<Exchange> createExchanges(ResponseInputStream<GetObjectResponse> s3Object, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(s3Object, key);
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
                    Exchange exchange = getEndpoint().createExchange(s3Object, s3ObjectSummary.key());
                    answer.add(exchange);
                } else {
                    // If includeFolders != true and the object is not included, it is safe to close the object here. 
                    // If includeFolders == true, the exchange will close the object.
                    IOHelper.close(s3Object);
                }
            }
        } catch (Throwable e) {
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
            return !Optional.of(((GetObjectResponse) s3Object.response()).contentType()).orElse("")
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
                    return "S3ConsumerOnCompletion";
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
                        .copySource(bucketName + "/" + key).build());

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

    @Override
    public String toString() {
        if (s3ConsumerToString == null) {
            s3ConsumerToString = "S3Consumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return s3ConsumerToString;
    }
}
