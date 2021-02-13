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
package org.apache.camel.component.google.storage;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.Builder;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store and retrieve objects from Google Cloud Storage Service using the google-cloud-storage library.
 * 
 * Google Storage Endpoint definition represents a bucket within the storage and contains configuration to customize the
 * behavior of Consumer and Producer.
 * 
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "google-storage", title = "Google Storage", syntax = "google-storage:bucketName",
             category = {
                     Category.CLOUD })
public class GoogleCloudStorageEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageEndpoint.class);

    @UriParam
    private GoogleCloudStorageComponentConfiguration configuration;

    private Storage storageClient;

    public GoogleCloudStorageEndpoint(String uri, GoogleCloudStorageComponent component,
                                      GoogleCloudStorageComponentConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCloudStorageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new GoogleCloudStorageConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.storageClient = configuration.getStorageClient();
        if (this.storageClient == null) {
            this.storageClient = GoogleCloudStorageConnectionFactory.create(configuration);
        }

        if (configuration.isAutoCreateBucket()) {
            LOG.info("getting the bucket {}", configuration.getBucketName());
            try {

                Bucket bucket = this.storageClient.get(configuration.getBucketName());
                if (bucket != null) {
                    LOG.trace("Bucket [{}] already exists", bucket.getName());
                    return;
                } else {
                    // creates the new bucket because it doesn't exist yet
                    final String location = configuration.getStorageLocation();
                    final StorageClass storageClass = configuration.getStorageClass();

                    Builder bucketBuilder = BucketInfo.newBuilder(configuration.getBucketName())
                            .setStorageClass(storageClass)
                            .setLocation(location);
                    BucketInfo bucketInfo = bucketBuilder.build();
                    bucket = storageClient.create(bucketInfo);
                    LOG.trace("Bucket [{}] has been created", bucket.getName());
                }
            } catch (Exception e) {
                LOG.error("Error - autocreatebucket", e);
                throw e;
            }
        }
    }

    public GoogleCloudStorageComponentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     * 
     * @param configuration
     */
    public void setConfiguration(GoogleCloudStorageComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    public Exchange createExchange(Blob blob, String key) {
        return createExchange(getExchangePattern(), blob, key);
    }

    public Exchange createExchange(ExchangePattern pattern, Blob blob, String key) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", key, getConfiguration().getBucketName());

        LOG.trace("Got object [{}]", blob);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();

        if (configuration.isIncludeBody()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                blob.downloadTo(baos);
                message.setBody(baos.toByteArray());
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            message.setBody(blob);
        }

        message.setHeader(GoogleCloudStorageConstants.OBJECT_NAME, key);
        message.setHeader(GoogleCloudStorageConstants.BUCKET_NAME, getConfiguration().getBucketName());
        //OTHER METADATA        
        message.setHeader(GoogleCloudStorageConstants.CACHE_CONTROL, blob.getCacheControl());
        message.setHeader(GoogleCloudStorageConstants.METADATA_COMPONENT_COUNT, blob.getComponentCount());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_DISPOSITION, blob.getContentDisposition());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, blob.getContentEncoding());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CONTENT_LANGUAGE, blob.getContentLanguage());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_TYPE, blob.getContentType());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CUSTOM_TIME, blob.getCustomTime());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CRC32C_hex, blob.getCrc32cToHexString());
        message.setHeader(GoogleCloudStorageConstants.METADATA_ETAG, blob.getEtag());
        message.setHeader(GoogleCloudStorageConstants.METADATA_GENERATION, blob.getGeneration());
        message.setHeader(GoogleCloudStorageConstants.METADATA_BLOB_ID, blob.getBlobId());
        message.setHeader(GoogleCloudStorageConstants.METADATA_KMS_KEY_NAME, blob.getKmsKeyName());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_MD5, blob.getMd5ToHexString());
        message.setHeader(GoogleCloudStorageConstants.METADATA_MEDIA_LINK, blob.getMediaLink());
        message.setHeader(GoogleCloudStorageConstants.METADATA_METAGENERATION, blob.getMetageneration());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, blob.getSize());
        message.setHeader(GoogleCloudStorageConstants.METADATA_STORAGE_CLASS, blob.getStorageClass());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CREATE_TIME, blob.getCreateTime());
        message.setHeader(GoogleCloudStorageConstants.METADATA_LAST_UPDATE, new Date(blob.getUpdateTime()));

        return exchange;
    }

}
