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

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Store and retrieve objects from Minio Storage Service using Minio SDK.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "minio", title = "Minio", syntax = "minio:bucketName",
             category = { Category.CLOUD, Category.FILE }, headersClass = MinioConstants.class)
public class MinioEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MinioEndpoint.class);

    private MinioClient minioClient;

    @UriParam
    private MinioConfiguration configuration;

    public MinioEndpoint(String uri, Component component, MinioConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        MinioConsumer minioConsumer = new MinioConsumer(this, processor);
        configureConsumer(minioConsumer);
        minioConsumer.setMaxMessagesPerPoll(configuration.getMaxMessagesPerPoll());
        return minioConsumer;
    }

    @Override
    public Producer createProducer() {
        return new MinioProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        minioClient
                = isNotEmpty(getConfiguration().getMinioClient()) ? getConfiguration().getMinioClient() : createMinioClient();

        String objectName = getConfiguration().getObjectName();

        if (isNotEmpty(objectName)) {
            LOG.trace("Object name {} requested, so skipping bucket check...", objectName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket {} already exists...", bucketName);

        if (bucketExists(bucketName)) {
            LOG.trace("Bucket {} already exists", bucketName);
        } else {
            if (getConfiguration().isAutoCreateBucket()) {
                LOG.trace("AutoCreateBucket set to true, Creating bucket {}...", bucketName);
                makeBucket(bucketName);
                LOG.trace("Bucket created");
            }
        }

        if (isNotEmpty(getConfiguration().getPolicy())) {
            LOG.trace("Updating bucket {} with policy {}", bucketName, configuration.getPolicy());
            setBucketPolicy(bucketName);
            LOG.trace("Bucket policy updated");
        }
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
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

    MinioClient createMinioClient() {
        if (isNotEmpty(configuration.getEndpoint())) {
            MinioClient.Builder minioClientRequest = MinioClient.builder();

            if (isNotEmpty(configuration.getProxyPort())) {
                minioClientRequest.endpoint(configuration.getEndpoint(), configuration.getProxyPort(),
                        configuration.isSecure());
            } else {
                minioClientRequest.endpoint(configuration.getEndpoint());
            }
            if (isNotEmpty(configuration.getAccessKey()) && isNotEmpty(configuration.getSecretKey())) {
                minioClientRequest.credentials(configuration.getAccessKey(), configuration.getSecretKey());
            }
            if (isNotEmpty(configuration.getRegion())) {
                minioClientRequest.region(configuration.getRegion());
            }
            if (isNotEmpty(configuration.getCustomHttpClient())) {
                minioClientRequest.httpClient(configuration.getCustomHttpClient());
            }
            return minioClientRequest.build();

        } else {
            throw new IllegalArgumentException("Endpoint must be specified");
        }
    }

    private boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    private void makeBucket(String bucketName) throws Exception {
        MakeBucketArgs.Builder makeBucketRequest
                = MakeBucketArgs.builder().bucket(bucketName).objectLock(getConfiguration().isObjectLock());
        if (isNotEmpty(getConfiguration().getRegion())) {
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

    void getObjectStat(String objectName, Message message) throws Exception {

        String bucketName = getConfiguration().getBucketName();
        StatObjectArgs.Builder statObjectRequest = StatObjectArgs.builder().bucket(bucketName).object(objectName);

        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getServerSideEncryptionCustomerKey,
                statObjectRequest::ssec);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getOffset, statObjectRequest::offset);
        MinioChecks.checkLengthAndSetConfig(getConfiguration()::getLength, statObjectRequest::length);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getVersionId, statObjectRequest::versionId);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getMatchETag, statObjectRequest::matchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getNotMatchETag,
                statObjectRequest::notMatchETag);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getModifiedSince,
                statObjectRequest::modifiedSince);
        MinioChecks.checkIfConfigIsNotEmptyAndSetAndConfig(getConfiguration()::getUnModifiedSince,
                statObjectRequest::unmodifiedSince);

        StatObjectResponse stat = minioClient.statObject(statObjectRequest.build());

        // set all stat as message headers
        message.setHeader(MinioConstants.OBJECT_NAME, stat.object());
        message.setHeader(MinioConstants.BUCKET_NAME, stat.bucket());
        message.setHeader(MinioConstants.E_TAG, stat.etag());
        message.setHeader(MinioConstants.LAST_MODIFIED, stat.headers().get("last-modified"));
        message.setHeader(MinioConstants.VERSION_ID, stat.headers().get("x-amz-version-id"));
        message.setHeader(MinioConstants.CONTENT_TYPE, stat.contentType());
        message.setHeader(MinioConstants.CONTENT_LENGTH, stat.size());
        message.setHeader(MinioConstants.CONTENT_ENCODING, stat.headers().get("content-encoding"));
        message.setHeader(MinioConstants.CONTENT_DISPOSITION, stat.headers().get("content-disposition"));
        message.setHeader(MinioConstants.CACHE_CONTROL, stat.headers().get("cache-control"));
        message.setHeader(MinioConstants.SERVER_SIDE_ENCRYPTION, stat.headers().get("x-amz-server-side-encryption"));
        message.setHeader(MinioConstants.EXPIRATION_TIME, stat.headers().get("x-amz-expiration"));
        message.setHeader(MinioConstants.REPLICATION_STATUS, stat.headers().get("x-amz-replication-status"));
        message.setHeader(MinioConstants.STORAGE_CLASS, stat.headers().get("x-amz-storage-class"));
    }
}
