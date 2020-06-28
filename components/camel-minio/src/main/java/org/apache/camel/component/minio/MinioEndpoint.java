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

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.InvalidBucketNameException;
import io.minio.messages.Item;
import org.apache.camel.*;
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
@UriEndpoint(firstVersion = "3.5.0", scheme = "minio", title = "Minio Storage Service", syntax = "minio:url", category = {Category.CLOUD, Category.FILE})
public class MinioEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MinioEndpoint.class);

    private MinioClient minioClient;

    @UriPath(description = "Qualified url")
    @Metadata(required = true)
    private String url; // to support component docs
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
    public Producer createProducer() throws Exception {
        return new MinioProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        minioClient = configuration.getMinioClient() != null
                ? configuration.getMinioClient()
                : MinioClientFactory.getClient(configuration).getMinioClient();

        String fileName = getConfiguration().getFileName();

        if (fileName != null) {
            LOG.trace("File name {} requested, so skipping bucket check...", fileName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket {} already exists...", bucketName);

        String prefix = getConfiguration().getPrefix();

        if (bucketExists(minioClient, bucketName)) {
            LOG.trace("Bucket {} already exists", bucketName);
        } else {
            if (!getConfiguration().isAutoCreateBucket()) {
                throw new InvalidBucketNameException("Bucket {} does not exists", bucketName);
            } else {
                LOG.trace("Bucket {} doesn't exist yet", bucketName);
                // creates the new bucket because it doesn't exist yet

                LOG.trace("Creating bucket {} in region {} with request...", bucketName, configuration.getRegion());

                makeBucket(bucketName, configuration.getRegion(), configuration.isObjectLock());

                LOG.trace("Bucket created");
            }
        }

        if (configuration.getPolicy() != null) {
            LOG.trace("Updating bucket {} with policy {}", bucketName, configuration.getPolicy());

            minioClient.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucketName).policy(configuration.getPolicy()).build());

            LOG.trace("Bucket policy updated");
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getMinioClient())) {
            if (minioClient != null) {
                minioClient.close();
            }
        }
        super.doStop();
    }

    public Exchange createExchange(InputStream minioObject, String key) {
        return createExchange(getExchangePattern(), minioObject, key);
    }

    public Exchange createExchange(ExchangePattern pattern, ResponseInputStream<GetObjectResponse> s3Object, String key) {
        LOG.trace("Getting object with key {} from bucket {}...", key, getConfiguration().getBucketName());

        LOG.trace("Got object {}", s3Object);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();

        if (configuration.isIncludeBody()) {
            try {
                message.setBody(readInputStream(s3Object));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            message.setBody(null);
        }

        message.setHeader(MinioConstants.KEY, key);
        message.setHeader(MinioConstants.BUCKET_NAME, getConfiguration().getBucketName());
        message.setHeader(MinioConstants.E_TAG, s3Object.response().eTag());
        message.setHeader(MinioConstants.LAST_MODIFIED, s3Object.response().lastModified());
        message.setHeader(MinioConstants.VERSION_ID, s3Object.response().versionId());
        message.setHeader(MinioConstants.CONTENT_TYPE, s3Object.response().contentType());
        message.setHeader(MinioConstants.CONTENT_LENGTH, s3Object.response().contentLength());
        message.setHeader(MinioConstants.CONTENT_ENCODING, s3Object.response().contentEncoding());
        message.setHeader(MinioConstants.CONTENT_DISPOSITION, s3Object.response().contentDisposition());
        message.setHeader(MinioConstants.CACHE_CONTROL, s3Object.response().cacheControl());
        message.setHeader(MinioConstants.SERVER_SIDE_ENCRYPTION, s3Object.response().serverSideEncryption());
        message.setHeader(MinioConstants.EXPIRATION_TIME, s3Object.response().expiration());
        message.setHeader(MinioConstants.REPLICATION_STATUS, s3Object.response().replicationStatus());
        message.setHeader(MinioConstants.STORAGE_CLASS, s3Object.response().storageClass());

        /**
         * If includeBody != true, it is safe to close the object here. If
         * includeBody == true, the caller is responsible for closing the stream
         * and object once the body has been fully consumed. As of 2.17, the
         * consumer does not close the stream or object on commit.
         */
        if (!configuration.isIncludeBody()) {
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

    public MinioConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinioConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public MinioClient getMinioClient() {
        return minioClient;
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
     * Set the maxConnections parameter in the S3 client configuration
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    private String readInputStream(ResponseInputStream<GetObjectResponse> s3Object) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(s3Object, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char)c);
            }
        }
        return textBuilder.toString();
    }

    private boolean bucketExists(MinioClient minioClient, String bucketName) throws Exception {
        try {
            return minioClient.bucketExists(bucketName);

        } catch (Throwable e) {
            LOG.warn("Error checking bucket, due: {}", e.getMessage());
            throw e;
        }
    }

    private void makeBucket(String bucketName, String region, boolean isObjectLock) {
        if (getConfiguration().)
    }
}
