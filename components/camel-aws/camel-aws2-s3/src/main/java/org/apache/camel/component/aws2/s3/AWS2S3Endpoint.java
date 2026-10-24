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

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.s3.client.AWS2S3ClientFactory;
import org.apache.camel.component.aws2.s3.stream.AWS2S3StreamUploadProducer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

/**
 * Store and retrieve objects from AWS S3 Storage Service.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-s3", title = "AWS S3 Storage Service",
             syntax = "aws2-s3://bucketNameOrArn", category = { Category.CLOUD, Category.FILE },
             headersClass = AWS2S3Constants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                   byOption = {
                                           @BodyType.When(option = "includeBody", value = "false", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "ignoreBody", value = "true", carriers = Carrier.NULL) },
                                   description = "The content of the object as a byte array, one message per object, with its"
                                                 + " key and metadata in headers. With includeBody=false the stream of the"
                                                 + " object, and with ignoreBody=true no body"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  dependsOn = { "operation", "pojoRequest" },
                                  description = "The content to upload, when no operation is set: text, bytes, a stream or a"
                                                + " file. A Map or a List fails: marshal it first. The operations take their"
                                                + " input from headers, or with pojoRequest=true from the request of the AWS SDK"
                                                + " in the body, such as a GetObjectRequest"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getObject", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "getObjectRange",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "listObjects", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.s3.model.S3Object>"),
                                           @BodyType.When(option = "operation", value = "listBuckets", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.s3.model.Bucket>"),
                                           @BodyType.When(option = "operation", value = "getObjectTagging",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.s3.model.Tag>"),
                                           @BodyType.When(option = "operation", value = "getBucketTagging",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.s3.model.Tag>"),
                                           @BodyType.When(option = "operation", value = "createDownloadLink",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "createUploadLink",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getBucketPolicy",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "deleteObject",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "headBucket",
                                                          carriers = { Carrier.POJO, Carrier.UNCHANGED }),
                                           @BodyType.When(option = "operation", value = "headObject", carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.HeadObjectResponse"),
                                           @BodyType.When(option = "operation", value = "deleteObjects",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.DeleteObjectsResponse"),
                                           @BodyType.When(option = "operation", value = "deleteBucket", carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.DeleteBucketResponse"),
                                           @BodyType.When(option = "operation", value = "createBucket", carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.CreateBucketResponse"),
                                           @BodyType.When(option = "operation", value = "restoreObject",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.RestoreObjectResponse"),
                                           @BodyType.When(option = "operation", value = "putObjectTagging",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.PutObjectTaggingResponse"),
                                           @BodyType.When(option = "operation", value = "deleteObjectTagging",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.DeleteObjectTaggingResponse"),
                                           @BodyType.When(option = "operation", value = "getObjectAcl", carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.GetObjectAclResponse"),
                                           @BodyType.When(option = "operation", value = "putObjectAcl", carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.PutObjectAclResponse"),
                                           @BodyType.When(option = "operation", value = "putBucketTagging",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.PutBucketTaggingResponse"),
                                           @BodyType.When(option = "operation", value = "deleteBucketTagging",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.DeleteBucketTaggingResponse"),
                                           @BodyType.When(option = "operation", value = "getBucketVersioning",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse"),
                                           @BodyType.When(option = "operation", value = "putBucketVersioning",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse"),
                                           @BodyType.When(option = "operation", value = "putBucketPolicy",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.PutBucketPolicyResponse"),
                                           @BodyType.When(option = "operation", value = "deleteBucketPolicy",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.s3.model.DeleteBucketPolicyResponse") },
                                   description = "Unchanged after an upload and a copyObject, with the ETag in a header. By the"
                                                 + " operation: the stream of the object for getObject and getObjectRange, a"
                                                 + " List for the lists and the tagging gets, the URL as text for the links, the"
                                                 + " policy JSON for getBucketPolicy, true for deleteObject, and the response of"
                                                 + " the AWS SDK for the others. getObject with ignoreBody=true, headBucket for a"
                                                 + " bucket that does not exist and streamingUploadMode keep the body"))
public class AWS2S3Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Endpoint.class);
    private static final int DEFAULT_IN_PROGRESS_CACHE_SIZE = 10000;

    private S3Client s3Client;

    @UriPath(description = "Bucket name or ARN")
    @Metadata(required = true)
    private String bucketNameOrArn; // to support component docs
    @UriParam
    private AWS2S3Configuration configuration;
    @UriParam(label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @Deprecated
    @UriParam(label = "consumer", defaultValue = "60",
              description = "Set the maxConnections parameter in the S3 client configuration. This option is"
                            + " deprecated and has no effect. Use the httpClientBuilder option on the S3 client"
                            + " configuration to customize the HTTP client connection pool.")
    private int maxConnections = 50 + maxMessagesPerPoll;
    @UriParam(label = "consumer,advanced", description = "A pluggable in-progress repository "
                                                         + "org.apache.camel.spi.IdempotentRepository. The in-progress repository is used to account the current in "
                                                         + "progress files being consumed. By default a memory based repository is used.")
    private IdempotentRepository inProgressRepository
            = MemoryIdempotentRepository.memoryIdempotentRepositoryFifo(DEFAULT_IN_PROGRESS_CACHE_SIZE);

    public AWS2S3Endpoint(String uri, Component comp, AWS2S3Configuration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (configuration.isForcePathStyle()) {
                return getServiceProtocol() + "." + configuration.getRegion() + "." + ".amazonaws.com" + "/"
                       + configuration.getBucketName() + "/";
            } else {
                return configuration.getBucketName() + "." + configuration.getRegion() + "." + getServiceProtocol()
                       + ".amazonaws.com/";
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "s3";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            return Map.of("region", configuration.getRegion());
        }
        return null;
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
        if (!configuration.isStreamingUploadMode()) {
            return new AWS2S3Producer(this);
        } else {
            return new AWS2S3StreamUploadProducer(this);
        }
    }

    @Override
    public AWS2S3Component getComponent() {
        return (AWS2S3Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        s3Client = ObjectHelper.isNotEmpty(configuration.getAmazonS3Client())
                ? configuration.getAmazonS3Client() : AWS2S3ClientFactory.getS3Client(configuration);

        String fileName = getConfiguration().getFileName();

        if (ObjectHelper.isNotEmpty(fileName)) {
            LOG.trace("File name [{}] requested, so skipping bucket check...", fileName);
            return;
        }

        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Querying whether bucket [{}] already exists...", bucketName);

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            LOG.trace("Bucket [{}] already exists", bucketName);
            return;
        } catch (AwsServiceException ase) {
            if (ase.statusCode() == 403) { // means we can't check if the bucket exists
                if (!getConfiguration().isAutoCreateBucket()) {
                    // We are not requested to create it if it doesn't, so we can only assume that it does
                    return;
                }
            }
            /* 404 means the bucket doesn't exist */
            if (!(ase.awsErrorDetails().sdkHttpResponse().statusCode() == 404)) {
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

        if (ObjectHelper.isNotEmpty(configuration.getPolicy())) {
            LOG.trace("Updating bucket [{}] with policy [{}]", bucketName, configuration.getPolicy());

            s3Client.putBucketPolicy(
                    PutBucketPolicyRequest.builder().bucket(bucketName).policy(configuration.getPolicy()).build());

            LOG.trace("Bucket policy updated");
        }

        ServiceHelper.startService(inProgressRepository);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonS3Client())) {
            if (ObjectHelper.isNotEmpty(s3Client)) {
                s3Client.close();
            }
        }
        ServiceHelper.stopService(inProgressRepository);
        super.doStop();
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

    /**
     * @deprecated This option has no effect and will be removed in a future release.
     */
    @Deprecated
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * @deprecated This option has no effect and will be removed in a future release.
     */
    @Deprecated
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public IdempotentRepository getInProgressRepository() {
        return inProgressRepository;
    }

    public void setInProgressRepository(IdempotentRepository inProgressRepository) {
        this.inProgressRepository = inProgressRepository;
    }
}
