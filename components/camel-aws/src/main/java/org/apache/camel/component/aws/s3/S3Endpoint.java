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
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/aws.html">AWS S3 Endpoint</a>.
 */
@UriEndpoint(scheme = "aws-s3", title = "AWS S3 Storage Service", syntax = "aws-s3:bucketName", consumerClass = S3Consumer.class, label = "cloud,file")
public class S3Endpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(S3Endpoint.class);

    private AmazonS3 s3Client;

    @UriParam
    private S3Configuration configuration;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;

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

        s3Client = configuration.getAmazonS3Client() != null
                ? configuration.getAmazonS3Client() : createS3Client();

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

        try {
            s3Client.listObjects(new ListObjectsRequest(bucketName, null, null, null, 0));
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
        if (getConfiguration().getRegion() != null) {
            createBucketRequest.setRegion(getConfiguration().getRegion());
        }

        LOG.trace("Creating bucket [{}] in region [{}] with request [{}]...", new Object[]{configuration.getBucketName(), configuration.getRegion(), createBucketRequest});

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
        message.setBody(s3Object.getObjectContent());
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
     * Provide the possibility to override this method for an mock implementation
     */
    AmazonS3 createS3Client() {
        AmazonS3 client = null;
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            client = new AmazonS3Client(credentials, clientConfiguration);
        } else {
            client = new AmazonS3Client(credentials);
        }
        return client;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
}