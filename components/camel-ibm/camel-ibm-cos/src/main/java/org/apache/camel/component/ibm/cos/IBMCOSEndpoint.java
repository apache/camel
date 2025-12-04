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

package org.apache.camel.component.ibm.cos;

import java.util.Map;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store and retrieve objects from IBM Cloud Object Storage.
 */
@UriEndpoint(
        firstVersion = "4.16.0",
        scheme = "ibm-cos",
        title = "IBM Cloud Object Storage",
        syntax = "ibm-cos:bucketName",
        category = {Category.CLOUD, Category.FILE},
        headersClass = IBMCOSConstants.class)
public class IBMCOSEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(IBMCOSEndpoint.class);
    private static final int DEFAULT_IN_PROGRESS_CACHE_SIZE = 10000;

    private AmazonS3 cosClient;

    @UriPath(description = "Bucket name")
    @Metadata(required = true)
    private String bucketName;

    @UriParam
    private IBMCOSConfiguration configuration;

    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;

    @UriParam(label = "consumer,advanced")
    private IdempotentRepository inProgressRepository =
            MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IN_PROGRESS_CACHE_SIZE);

    public IBMCOSEndpoint(String uri, Component comp, IBMCOSConfiguration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getEndpointUrl())) {
            return configuration.getEndpointUrl() + "/" + configuration.getBucketName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "cos";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getLocation() != null) {
            return Map.of("location", configuration.getLocation());
        }
        return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        IBMCOSConsumer cosConsumer = new IBMCOSConsumer(this, processor);
        configureConsumer(cosConsumer);
        cosConsumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return cosConsumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IBMCOSProducer(this);
    }

    @Override
    public IBMCOSComponent getComponent() {
        return (IBMCOSComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        cosClient = configuration.getCosClient() != null ? configuration.getCosClient() : createCosClient();

        String fileName = getConfiguration().getFileName();

        if (fileName != null) {
            LOG.trace("File name [{}] requested, so skipping bucket check...", fileName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket [{}] already exists...", bucketName);

        try {
            boolean bucketExists = cosClient.doesBucketExistV2(bucketName);
            if (bucketExists) {
                LOG.trace("Bucket [{}] already exists", bucketName);
                return;
            }
        } catch (Exception e) {
            LOG.warn("Error checking if bucket exists: {}", e.getMessage());
        }

        LOG.trace("Bucket [{}] doesn't exist yet", bucketName);

        if (getConfiguration().isAutoCreateBucket()) {
            LOG.trace("Creating bucket [{}]...", bucketName);
            // The location is specified via the client's endpoint configuration
            cosClient.createBucket(bucketName);
            LOG.trace("Bucket created");
        }

        ServiceHelper.startService(inProgressRepository);
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getCosClient())) {
            if (cosClient != null) {
                cosClient.shutdown();
            }
        }
        ServiceHelper.stopService(inProgressRepository);
        super.doStop();
    }

    private AmazonS3 createCosClient() {
        AWSCredentials credentials;
        if (configuration.getApiKey() != null) {
            credentials = new BasicIBMOAuthCredentials(configuration.getApiKey(), configuration.getServiceInstanceId());
        } else {
            throw new IllegalArgumentException("API Key must be provided");
        }

        ClientConfiguration clientConfig =
                new ClientConfiguration().withRequestTimeout(5000).withTcpKeepAlive(true);

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig);

        if (configuration.getEndpointUrl() != null) {
            // For IBM COS, use null as the signing region - it will be determined from the endpoint
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(configuration.getEndpointUrl(), null));
        }

        return builder.build();
    }

    public IBMCOSConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IBMCOSConfiguration configuration) {
        this.configuration = configuration;
    }

    public AmazonS3 getCosClient() {
        return cosClient;
    }

    public void setCosClient(AmazonS3 cosClient) {
        this.cosClient = cosClient;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public IdempotentRepository getInProgressRepository() {
        return inProgressRepository;
    }

    /**
     * A pluggable in-progress repository to track objects being consumed
     */
    public void setInProgressRepository(IdempotentRepository inProgressRepository) {
        this.inProgressRepository = inProgressRepository;
    }
}
