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

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.InvalidBucketNameException;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.minio.client.MinioClientFactory;
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

/**
 * Store and retrie objects from Minio Storage Service using Minio SDK.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "minio", title = "Minio Storage Service", syntax = "minio://bucketName", category = {Category.CLOUD, Category.FILE})
public class MinioEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MinioEndpoint.class);

    private MinioClient minioClient;

    @UriPath(description = "Bucket name")
    @Metadata(required = true)
    private String bucketName; // to support component docs
    @UriParam
    private MinioConfiguration configuration;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @UriParam(label = "consumer", defaultValue = "60")
    private int maxConnections = 50 + maxMessagesPerPoll;

    public MinioEndpoint(String uri, Component component, MinioConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        MinioConsumer minioConsumer = new MinioConsumer(this, processor);
        configureConsumer(minioConsumer);
        minioConsumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return minioConsumer;
    }

    @Override
    public Producer createProducer() {
        return new MinioProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        minioClient = getConfiguration().getMinioClient() != null
                ? getConfiguration().getMinioClient()
                : MinioClientFactory.getClient(getConfiguration()).getMinioClient();

        String objectName = getConfiguration().getObjectName();

        if (objectName != null) {
            LOG.trace("Object name {} requested, so skipping bucket check...", objectName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket {} already exists...", bucketName);

        if (bucketExists(bucketName)) {
            LOG.trace("Bucket {} already exists", bucketName);
        } else {
            if (!getConfiguration().isAutoCreateBucket()) {
                throw new InvalidBucketNameException("Bucket {} does not exists, set autoCreateBucket option for bucket auto creation", bucketName);
            } else {
                LOG.trace("AutoCreateBucket set to true, Creating bucket {}...", bucketName);
                makeBucket(bucketName);
                LOG.trace("Bucket created");
            }
        }

        if (getConfiguration().getPolicy() != null) {
            setBucketPolicy(bucketName);
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(getConfiguration().getMinioClient())) {
            if (minioClient != null) {
                minioClient = null;
            }
        }
        super.doStop();
    }

    public Exchange createExchange(InputStream minioObject, String objectName) throws Exception {
        return createExchange(getExchangePattern(), minioObject, objectName);
    }

    public Exchange createExchange(ExchangePattern pattern,
                                   InputStream minioObject, String objectName) throws Exception {
        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Getting object with objectName {} from bucket {}...", objectName, bucketName);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();
        LOG.trace("Got object!");

        getObjectTags(objectName, bucketName, message);

        if (getConfiguration().isIncludeBody()) {
            try {
                message.setBody(readInputStream(minioObject));
                if (getConfiguration().isAutocloseBody()) {
                    exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                        @Override
                        public void onDone(Exchange exchange) {
                            IOHelper.close(minioObject);
                        }
                    });
                }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            message.setBody(null);
            IOHelper.close(minioObject);
        }

        return exchange;
    }

    public MinioConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinioConfiguration configuration) {
        this.configuration = configuration;
    }

    public MinioClient getMinioClient() {
        return minioClient;
    }

    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Gets the maximum number of messages as a limit to poll at each polling.
     * The default value is 10. Use 0 or a negative number to set it as
     * unlimited.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Set the maxConnections parameter in the minio client configuration
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    private String readInputStream(InputStream minioObject) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(minioObject, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    private boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    private void makeBucket(String bucketName) throws Exception {
        MakeBucketArgs.Builder makeBucketRequest = MakeBucketArgs.builder().bucket(bucketName).objectLock(getConfiguration().isObjectLock());
        if (getConfiguration().getRegion() != null) {
            makeBucketRequest.region(getConfiguration().getRegion());
        }
        minioClient.makeBucket(makeBucketRequest.build());
    }

    private void setBucketPolicy(String bucketName) throws Exception {
        LOG.trace("Updating bucket {} with policy...", bucketName);
        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder().bucket(bucketName).config(getConfiguration().getPolicy()).build());
        LOG.trace("Bucket policy updated");
    }

    private void getObjectTags(String objectName, String bucketName, Message message) throws Exception {

        StatObjectArgs.Builder statObjectRequest = StatObjectArgs.builder().bucket(bucketName).object(objectName);

        if (getConfiguration().getServerSideEncryptionCustomerKey() != null) {
            statObjectRequest.ssec(getConfiguration().getServerSideEncryptionCustomerKey());
        }
        if (getConfiguration().getOffset() != 0) {
            statObjectRequest.offset(getConfiguration().getOffset());
        }
        if (getConfiguration().getLength() != 0) {
            statObjectRequest.length(getConfiguration().getLength());
        }
        if (getConfiguration().getVersionId() != null) {
            statObjectRequest.versionId(getConfiguration().getVersionId());
        }
        if (getConfiguration().getMatchETag() != null) {
            statObjectRequest.matchETag(getConfiguration().getMatchETag());
        }
        if (getConfiguration().getNotMatchETag() != null) {
            statObjectRequest.notMatchETag(getConfiguration().getNotMatchETag());
        }
        if (getConfiguration().getModifiedSince() != null) {
            statObjectRequest.modifiedSince(getConfiguration().getModifiedSince());
        }
        if (getConfiguration().getUnModifiedSince() != null) {
            statObjectRequest.unmodifiedSince(getConfiguration().getUnModifiedSince());
        }

        ObjectStat stat = minioClient.statObject(statObjectRequest.build());

        // set all stat as message headers
        message.setHeader(MinioConstants.OBJECT_NAME, objectName);
        message.setHeader(MinioConstants.BUCKET_NAME, bucketName);
        message.setHeader(MinioConstants.E_TAG, stat.etag());
        message.setHeader(MinioConstants.LAST_MODIFIED, stat.httpHeaders().get("last-modified"));
        message.setHeader(MinioConstants.VERSION_ID, stat.httpHeaders().get("x-amz-version-id"));
        message.setHeader(MinioConstants.CONTENT_TYPE, stat.contentType());
        message.setHeader(MinioConstants.CONTENT_LENGTH, stat.length());
        message.setHeader(MinioConstants.SERVER_SIDE_ENCRYPTION, stat.httpHeaders().get("x-amz-server-side-encryption"));
        message.setHeader(MinioConstants.EXPIRATION_TIME, stat.httpHeaders().get("x-amz-expiration"));
        message.setHeader(MinioConstants.REPLICATION_STATUS, stat.httpHeaders().get("x-amz-replication-status"));
        message.setHeader(MinioConstants.STORAGE_CLASS, stat.httpHeaders().get("x-amz-storage-class"));
    }
}
