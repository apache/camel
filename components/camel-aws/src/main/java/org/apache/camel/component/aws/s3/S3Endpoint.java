/**
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
package org.apache.camel.component.aws.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The aws-s3 component is used for storing and retrieving objecct from Amazon
 * S3 Storage Service.
 */
@UriEndpoint(firstVersion = "2.8.0", scheme = "aws-s3", title = "AWS S3 Storage Service", syntax = "aws-s3:bucketNameOrArn", consumerClass = S3Consumer.class, label = "cloud,file")
public class S3Endpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(S3Endpoint.class);

    private AmazonS3 s3Client;

    @UriPath(description = "Bucket name or ARN")
    @Metadata(required = "true")
    private String bucketNameOrArn; // to support component docs
    @UriParam
    private S3Configuration configuration;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @UriParam(label = "consumer", defaultValue = "60")
    private int maxConnections = 50 + maxMessagesPerPoll;

    @Deprecated
    public S3Endpoint(String uri, CamelContext context, S3Configuration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }

    public S3Endpoint(String uri, Component comp, S3Configuration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        S3Consumer s3Consumer = new S3Consumer(this, processor);
        configureConsumer(s3Consumer);
        s3Consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return s3Consumer;
    }

    public Producer createProducer() throws Exception {
        return new S3Producer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        s3Client = configuration.getAmazonS3Client() != null ? configuration.getAmazonS3Client() : createS3Client();

        if (ObjectHelper.isNotEmpty(configuration.getAmazonS3Endpoint())) {
            s3Client.setEndpoint(configuration.getAmazonS3Endpoint());
        }

        String fileName = getConfiguration().getFileName();

        if (fileName != null) {
            LOG.trace("File name [{}] requested, so skipping bucket check...", fileName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket [{}] already exists...", bucketName);

        String prefix = getConfiguration().getPrefix();

        try {
            s3Client.listObjects(new ListObjectsRequest(bucketName, prefix, null, null, 0));
            LOG.trace("Bucket [{}] already exists", bucketName);
            return;
        } catch (AmazonServiceException ase) {
            /* 404 means the bucket doesn't exist */
            if (ase.getStatusCode() != 404) {
                throw ase;
            }
        }

        LOG.trace("Bucket [{}] doesn't exist yet", bucketName);

        // creates the new bucket because it doesn't exist yet
        CreateBucketRequest createBucketRequest = new CreateBucketRequest(getConfiguration().getBucketName());

        LOG.trace("Creating bucket [{}] in region [{}] with request [{}]...", configuration.getBucketName(), configuration.getRegion(), createBucketRequest);

        s3Client.createBucket(createBucketRequest);

        LOG.trace("Bucket created");

        if (configuration.getPolicy() != null) {
            LOG.trace("Updating bucket [{}] with policy [{}]", bucketName, configuration.getPolicy());

            s3Client.setBucketPolicy(bucketName, configuration.getPolicy());

            LOG.trace("Bucket policy updated");
        }
    }

    public Exchange createExchange(S3Object s3Object) {
        return createExchange(getExchangePattern(), s3Object);
    }

    public Exchange createExchange(ExchangePattern pattern, S3Object s3Object) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", s3Object.getKey(), s3Object.getBucketName());

        ObjectMetadata objectMetadata = s3Object.getObjectMetadata();

        LOG.trace("Got object [{}]", s3Object);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();

        if (configuration.isIncludeBody()) {
            message.setBody(s3Object.getObjectContent());
        } else {
            message.setBody(null);
        }

        message.setHeader(S3Constants.KEY, s3Object.getKey());
        message.setHeader(S3Constants.BUCKET_NAME, s3Object.getBucketName());
        message.setHeader(S3Constants.E_TAG, objectMetadata.getETag());
        message.setHeader(S3Constants.LAST_MODIFIED, objectMetadata.getLastModified());
        message.setHeader(S3Constants.VERSION_ID, objectMetadata.getVersionId());
        message.setHeader(S3Constants.CONTENT_TYPE, objectMetadata.getContentType());
        message.setHeader(S3Constants.CONTENT_MD5, objectMetadata.getContentMD5());
        message.setHeader(S3Constants.CONTENT_LENGTH, objectMetadata.getContentLength());
        message.setHeader(S3Constants.CONTENT_ENCODING, objectMetadata.getContentEncoding());
        message.setHeader(S3Constants.CONTENT_DISPOSITION, objectMetadata.getContentDisposition());
        message.setHeader(S3Constants.CACHE_CONTROL, objectMetadata.getCacheControl());
        message.setHeader(S3Constants.S3_HEADERS, objectMetadata.getRawMetadata());
        message.setHeader(S3Constants.SERVER_SIDE_ENCRYPTION, objectMetadata.getSSEAlgorithm());

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
                exchange.addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        IOHelper.close(s3Object);
                    }
                });
            }
        }

        return exchange;
    }

    public S3Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(S3Configuration configuration) {
        this.configuration = configuration;
    }

    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    public AmazonS3 getS3Client() {
        return s3Client;
    }

    /**
     * Provide the possibility to override this method for an mock
     * implementation
     */
    AmazonS3 createS3Client() {

        AmazonS3 client = null;
        AmazonS3ClientBuilder clientBuilder = null;
        AmazonS3EncryptionClientBuilder encClientBuilder = null;
        ClientConfiguration clientConfiguration = null;
        boolean isClientConfigFound = false;
        if (configuration.hasProxyConfiguration()) {
            clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            clientConfiguration.setMaxConnections(getMaxConnections());
            isClientConfigFound = true;
        } else {
            clientConfiguration = new ClientConfiguration();
            clientConfiguration.setMaxConnections(getMaxConnections());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
            AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
            if (isClientConfigFound && !configuration.isUseEncryption()) {
                clientBuilder = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else if (isClientConfigFound && configuration.isUseEncryption()) {
                StaticEncryptionMaterialsProvider encryptionMaterialsProvider = new StaticEncryptionMaterialsProvider(configuration.getEncryptionMaterials());
                encClientBuilder = AmazonS3EncryptionClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider)
                    .withEncryptionMaterials(encryptionMaterialsProvider);
            } else {
                clientBuilder = AmazonS3ClientBuilder.standard().withCredentials(credentialsProvider);
            }
            if (!configuration.isUseEncryption()) {
                if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                    clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
                }
                clientBuilder = clientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
                client = clientBuilder.build();
            } else {
                if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                    encClientBuilder = encClientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
                }
                encClientBuilder = encClientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
                client = encClientBuilder.build();
            }
        } else {
            if (isClientConfigFound && !configuration.isUseEncryption()) {
                clientBuilder = AmazonS3ClientBuilder.standard();
            } else if (isClientConfigFound && configuration.isUseEncryption()) {
                StaticEncryptionMaterialsProvider encryptionMaterialsProvider = new StaticEncryptionMaterialsProvider(configuration.getEncryptionMaterials());
                encClientBuilder = AmazonS3EncryptionClientBuilder.standard().withClientConfiguration(clientConfiguration).withEncryptionMaterials(encryptionMaterialsProvider);
            } else {
                clientBuilder = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
            if (!configuration.isUseEncryption()) {
                if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                    clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
                }
                clientBuilder = clientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
                client = clientBuilder.build();
            } else {
                if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                    encClientBuilder = encClientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
                }
                encClientBuilder = encClientBuilder.withPathStyleAccessEnabled(configuration.isPathStyleAccess());
                client = encClientBuilder.build();
            }
        }

        return client;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as
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
}
