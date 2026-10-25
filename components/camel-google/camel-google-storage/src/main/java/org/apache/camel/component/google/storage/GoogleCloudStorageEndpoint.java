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

import java.util.Map;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.Builder;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store and retrieve objects from Google Cloud Storage Service using the google-cloud-storage library.
 *
 * Google Storage Endpoint definition represents a bucket within the storage and contains configuration to customize the
 * behavior of Consumer and Producer.
 */
@UriEndpoint(firstVersion = "3.9.0", scheme = "google-storage", title = "Google Storage", syntax = "google-storage:bucketName",
             category = { Category.CLOUD }, headersClass = GoogleCloudStorageConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.BYTES, Carrier.FILE },
                                   byOption = @BodyType.When(option = "includeBody", value = "false", carriers = Carrier.POJO,
                                                             javaType = "com.google.cloud.storage.Blob"),
                                   description = "The content of the object, one message per object, with its metadata in"
                                                 + " headers: a stream cache, or a byte array when stream caching is off. With"
                                                 + " downloadFileName the java.io.File it is downloaded to, and with"
                                                 + " includeBody=false the Blob, which has the metadata and not the content"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  dependsOn = "operation",
                                  description = "The content to upload, when no operation is set: text, bytes, a stream or a"
                                                + " file. A Map, a List or no body fails: marshal it first. The operations take"
                                                + " their input from options and headers"),
           producerOut = @BodyType(value = Carrier.POJO, javaType = "com.google.cloud.storage.Blob",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getObject", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "operation", value = "listObjects", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.storage.Blob>"),
                                           @BodyType.When(option = "operation", value = "listBuckets", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.storage.Bucket>"),
                                           @BodyType.When(option = "operation", value = "copyObject", carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.storage.CopyWriter"),
                                           @BodyType.When(option = "operation", value = "deleteObject",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteBucket",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "createDownloadLink",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String") },
                                   description = "The Blob of the uploaded object. By the operation: the content as a byte array"
                                                 + " for getObject, with its metadata in headers, a List of Blob or Bucket for the"
                                                 + " lists, the CopyWriter for copyObject, whose getResult() gives the Blob of the"
                                                 + " copy, true or false for the deletes and the signed URL as text for"
                                                 + " createDownloadLink"))
public class GoogleCloudStorageEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageEndpoint.class);

    @UriParam
    private GoogleCloudStorageConfiguration configuration;

    private Storage storageClient;

    public GoogleCloudStorageEndpoint(String uri, GoogleCloudStorageComponent component,
                                      GoogleCloudStorageConfiguration configuration) {
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
    public GoogleCloudStorageComponent getComponent() {
        return (GoogleCloudStorageComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.storageClient = configuration.getStorageClient();
        if (this.storageClient == null) {
            this.storageClient = GoogleCloudStorageConnectionFactory.create(this.getCamelContext(), configuration);
        }

        if (configuration.isAutoCreateBucket()) {
            Bucket bucket = this.storageClient.get(configuration.getBucketName());
            if (bucket != null) {
                LOG.trace("Bucket [{}] already exists", bucket.getName());
                return;
            } else {
                // creates the new bucket because it doesn't exist yet
                createNewBucket(configuration.getBucketName(), configuration, this.storageClient);
            }
        }
    }

    public static Bucket createNewBucket(String bucketName, GoogleCloudStorageConfiguration conf, Storage storage) {
        final String location = conf.getStorageLocation();
        final StorageClass storageClass = conf.getStorageClass();

        Builder bucketBuilder = BucketInfo.newBuilder(bucketName)
                .setStorageClass(storageClass)
                .setLocation(location);
        BucketInfo bucketInfo = bucketBuilder.build();
        Bucket bucket = storage.create(bucketInfo);
        LOG.trace("Bucket [{}] has been created", bucket.getName());
        return bucket;
    }

    public GoogleCloudStorageConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     */
    public void setConfiguration(GoogleCloudStorageConfiguration configuration) {
        this.configuration = configuration;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getBucketName())
                && ObjectHelper.isNotEmpty(configuration.getStorageLocation())) {
            return getServiceProtocol() + ":" + configuration.getStorageLocation() + ":"
                   + configuration.getBucketName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "storage";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getStorageClass() != null) {
            return Map.of("storageClass", configuration.getStorageClass().name());
        }
        return null;
    }

}
