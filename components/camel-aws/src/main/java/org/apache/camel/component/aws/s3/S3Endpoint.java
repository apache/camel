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

import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/aws.html">AWS S3 Endpoint</a>.  
 *
 */
public class S3Endpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(S3Endpoint.class);

    private AmazonS3Client s3Client;
    private S3Configuration configuration;
    private int maxMessagesPerPoll = 10;
    
    public S3Endpoint(String uri, CamelContext context, S3Configuration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        S3Consumer s3Consumer = new S3Consumer(this, processor);
        configureConsumer(s3Consumer);
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
        
        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Quering whether bucket [{}] already exists...", bucketName);
        
        List<Bucket> buckets = getS3Client().listBuckets();
        for (Bucket bucket : buckets) {
            if (bucketName.equals(bucket.getName())) {
                LOG.trace("Bucket [{}] already exist", bucketName);
                return;
            }
        }
        
        LOG.trace("Bucket [{}] doesn't exist yet", bucketName);
        
        // creates the new bucket because it doesn't exist yet
        CreateBucketRequest createBucketRequest = new CreateBucketRequest(getConfiguration().getBucketName());
        if (getConfiguration().getRegion() != null) {
            createBucketRequest.setRegion(getConfiguration().getRegion());
        }
        
        LOG.trace("Creating bucket [{}] in region [{}] with request [{}]...", new Object[]{configuration.getBucketName(), configuration.getRegion(), createBucketRequest});
        
        getS3Client().createBucket(createBucketRequest);
        
        LOG.trace("Bucket created");
    }

    public Exchange createExchange(S3Object s3Object) {
        return createExchange(getExchangePattern(), s3Object);
    }

    public Exchange createExchange(ExchangePattern pattern, S3Object s3Object) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", s3Object.getKey(), s3Object.getBucketName());
        
        ObjectMetadata objectMetadata = s3Object.getObjectMetadata();
        
        LOG.trace("Got object [{}]", s3Object);
        
        Exchange exchange = new DefaultExchange(this, pattern);
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
        
        return exchange;
    }

    public S3Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(S3Configuration configuration) {
        this.configuration = configuration;
    }
    
    public void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    public AmazonS3Client getS3Client() {
        if (s3Client == null) {
            s3Client = configuration.getAmazonS3Client() != null
                ? configuration.getAmazonS3Client() : createS3Client();
        }
        
        return s3Client;
    }

    /**
     * Provide the possibility to override this method for an mock implementation
     *
     * @return AmazonS3Client
     */
    AmazonS3Client createS3Client() {
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        return new AmazonS3Client(credentials);
    }
    
    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
}