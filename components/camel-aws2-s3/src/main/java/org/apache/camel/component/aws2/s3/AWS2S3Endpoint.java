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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws2.s3.client.AWS2S3ClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.utils.IoUtils;

/**
 * Store and retrieve objects from AWS S3 Storage Service using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-s3", title = "AWS 2 S3 Storage Service",
             syntax = "aws2-s3://bucketNameOrArn", category = { Category.CLOUD, Category.FILE })
public class AWS2S3Endpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Endpoint.class);

    private S3Client s3Client;

    @UriPath(description = "Bucket name or ARN")
    @Metadata(required = true)
    private String bucketNameOrArn; // to support component docs
    @UriParam
    private AWS2S3Configuration configuration;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @UriParam(label = "consumer", defaultValue = "60")
    private int maxConnections = 50 + maxMessagesPerPoll;

    public AWS2S3Endpoint(String uri, Component comp, AWS2S3Configuration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AWS2S3Consumer s3Consumer = new AWS2S3Consumer(this, processor);
        configureConsumer(s3Consumer);
        s3Consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return s3Consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AWS2S3Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        s3Client = configuration.getAmazonS3Client() != null
                ? configuration.getAmazonS3Client() : AWS2S3ClientFactory.getAWSS3Client(configuration).getS3Client();

        String fileName = getConfiguration().getFileName();

        if (fileName != null) {
            LOG.trace("File name [{}] requested, so skipping bucket check...", fileName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket [{}] already exists...", bucketName);

        String prefix = getConfiguration().getPrefix();

        try {
            ListObjectsRequest.Builder builder = ListObjectsRequest.builder();
            builder.bucket(bucketName);
            builder.prefix(prefix);
            builder.maxKeys(maxMessagesPerPoll);
            s3Client.listObjects(builder.build());
            LOG.trace("Bucket [{}] already exists", bucketName);
            return;
        } catch (AwsServiceException ase) {
            /* 404 means the bucket doesn't exist */
            if (ase.awsErrorDetails().errorCode().equalsIgnoreCase("404")) {
                throw ase;
            }
        }

        LOG.trace("Bucket [{}] doesn't exist yet", bucketName);

        if (getConfiguration().isAutoCreateBucket()) {
            // creates the new bucket because it doesn't exist yet
            CreateBucketRequest createBucketRequest
                    = CreateBucketRequest.builder().bucket(getConfiguration().getBucketName()).build();

            LOG.trace("Creating bucket [{}] in region [{}] with request [{}]...", configuration.getBucketName(),
                    configuration.getRegion(), createBucketRequest);

            s3Client.createBucket(createBucketRequest);

            LOG.trace("Bucket created");
        }

        if (configuration.getPolicy() != null) {
            LOG.trace("Updating bucket [{}] with policy [{}]", bucketName, configuration.getPolicy());

            s3Client.putBucketPolicy(
                    PutBucketPolicyRequest.builder().bucket(bucketName).policy(configuration.getPolicy()).build());

            LOG.trace("Bucket policy updated");
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonS3Client())) {
            if (s3Client != null) {
                s3Client.close();
            }
        }
        super.doStop();
    }

    public Exchange createExchange(ResponseInputStream<GetObjectResponse> s3Object, String key) {
        return createExchange(getExchangePattern(), s3Object, key);
    }

    public Exchange createExchange(ExchangePattern pattern, ResponseInputStream<GetObjectResponse> s3Object, String key) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", key, getConfiguration().getBucketName());

        LOG.trace("Got object [{}]", s3Object);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();

        if (configuration.isIncludeBody()) {
            try {
                message.setBody(IoUtils.toByteArray(s3Object));
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            message.setBody(s3Object);
        }

        message.setHeader(AWS2S3Constants.KEY, key);
        message.setHeader(AWS2S3Constants.BUCKET_NAME, getConfiguration().getBucketName());
        message.setHeader(AWS2S3Constants.E_TAG, s3Object.response().eTag());
        message.setHeader(AWS2S3Constants.LAST_MODIFIED, s3Object.response().lastModified());
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

        /*
         * If includeBody == true, it is safe to close the object here because the S3Object
         * was consumed already. If includeBody != true, the caller is responsible for
         * closing the stream once the body has been fully consumed or use the autoCloseBody
         * configuration to automatically schedule the body closing at the end of exchange.
         */
        if (configuration.isIncludeBody()) {
            IOHelper.close(s3Object);
        } else {
            if (configuration.isAutocloseBody()) {
                exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        IOHelper.close(s3Object);
                    }
                });
            }
        }

        return exchange;
    }

    public AWS2S3Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AWS2S3Configuration configuration) {
        this.configuration = configuration;
    }

    public void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public S3Client getS3Client() {
        return s3Client;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Gets the maximum number of messages as a limit to poll at each polling. The default value is 10. Use 0 or a
     * negative number to set it as unlimited.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Set the maxConnections parameter in the S3 client configuration
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
}
