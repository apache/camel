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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws2.s3.stream.AWSS3NamingStrategyEnum;
import org.apache.camel.component.aws2.s3.stream.AWSS3RestartingPolicyEnum;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@UriParams
public class AWS2S3Configuration implements Cloneable {

    private String bucketName;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private S3Client amazonS3Client;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private S3Presigner amazonS3Presigner;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam
    private String prefix;
    @UriParam
    private String delimiter;
    @UriParam(label = "consumer")
    private String doneFileName;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeFolders = true;
    @UriParam
    private String region;
    @UriParam
    private boolean forcePathStyle;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(label = "consumer")
    private boolean moveAfterRead;
    @UriParam(label = "consumer")
    private String destinationBucket;
    @UriParam(label = "consumer")
    private String destinationBucketPrefix;
    @UriParam(label = "consumer")
    private String destinationBucketSuffix;
    @UriParam(label = "producer")
    private boolean deleteAfterWrite;
    @UriParam(label = "producer")
    private boolean multiPartUpload;
    @UriParam(label = "producer", defaultValue = "" + 25 * 1024 * 1024)
    private long partSize = (long) 25 * 1024 * 1024;
    @UriParam
    private String policy;
    @UriParam(label = "producer")
    private String storageClass;

    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean ignoreBody;
    @UriParam(label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,getObjectRange,createDownloadLink")
    private AWS2S3Operations operation;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean autocloseBody = true;
    @UriParam(label = "common", defaultValue = "false")
    private boolean autoCreateBucket;
    @UriParam(label = "producer,advanced", defaultValue = "false")
    private boolean useAwsKMS;
    @UriParam(label = "producer,advanced")
    private String awsKMSKeyId;
    @UriParam(label = "producer,advanced", defaultValue = "false")
    private boolean useCustomerKey;
    @UriParam(label = "common,advanced")
    private String customerKeyId;
    @UriParam(label = "common,advanced")
    private String customerKeyMD5;
    @UriParam(label = "common,advanced")
    private String customerAlgorithm;
    @UriParam(label = "producer,advanced", defaultValue = "false")
    private boolean useSSES3;
    @UriParam(label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private String profileCredentialsName;
    @UriParam(label = "producer")
    private String keyName;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    private String uriEndpointOverride;
    @UriParam
    private boolean pojoRequest;
    @UriParam(label = "producer")
    private boolean streamingUploadMode;
    @UriParam(defaultValue = "10", label = "producer")
    private int batchMessageNumber = 10;
    @UriParam(defaultValue = "1000000", label = "producer")
    private int batchSize = 1000000;
    @UriParam(defaultValue = "1000000", label = "producer")
    private int bufferSize = 1000000;
    @UriParam(defaultValue = "progressive", label = "producer")
    private AWSS3NamingStrategyEnum namingStrategy = AWSS3NamingStrategyEnum.progressive;
    @UriParam(label = "producer")
    private long streamingUploadTimeout;
    @UriParam(defaultValue = "override", label = "producer")
    private AWSS3RestartingPolicyEnum restartingPolicy = AWSS3RestartingPolicyEnum.override;

    public long getPartSize() {
        return partSize;
    }

    /**
     * Setup the partSize which is used in multi part upload, the default size is 25M.
     */
    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public boolean isMultiPartUpload() {
        return multiPartUpload;
    }

    /**
     * If it is true, camel will upload the file with multi part format, the part size is decided by the option of
     * `partSize`
     */
    public void setMultiPartUpload(boolean multiPartUpload) {
        this.multiPartUpload = multiPartUpload;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public S3Client getAmazonS3Client() {
        return amazonS3Client;
    }

    /**
     * Reference to a `com.amazonaws.services.s3.AmazonS3` in the registry.
     */
    public void setAmazonS3Client(S3Client amazonS3Client) {
        this.amazonS3Client = amazonS3Client;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * The prefix which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only consume objects we are
     * interested in.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only consume objects we
     * are interested in.
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDoneFileName() {
        return doneFileName;
    }

    /**
     * If provided, Camel will only consume files if a done file exists.
     */
    public void setDoneFileName(String doneFileName) {
        this.doneFileName = doneFileName;
    }

    /**
     * If it is true, the folders/directories will be consumed. If it is false, they will be ignored, and Exchanges will
     * not be created for those
     */
    public void setIncludeFolders(boolean includeFolders) {
        this.includeFolders = includeFolders;
    }

    public boolean isIncludeFolders() {
        return includeFolders;
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Name of the bucket. The bucket will be created if it doesn't already exists.
     */
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * To get the object from the bucket with the given file name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which S3 client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * If it is true, the S3Object exchange will be consumed and put into the body and closed. If false the S3Object
     * stream will be put raw into the body and the headers will be set with the S3 object metadata. This option is
     * strongly related to autocloseBody option. In case of setting includeBody to true because the S3Object stream will
     * be consumed then it will also be closed, while in case of includeBody false then it will be up to the caller to
     * close the S3Object stream. However setting autocloseBody to true when includeBody is false it will schedule to
     * close the S3Object stream automatically on exchange completion.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    /**
     * If it is true, the S3 Object Body will be ignored completely, if it is set to false the S3 Object will be put in
     * the body. Setting this to true, will override any behavior defined by includeBody option.
     */
    public boolean isIgnoreBody() {
        return ignoreBody;
    }

    public void setIgnoreBody(boolean ignoreBody) {
        this.ignoreBody = ignoreBody;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete objects from S3 after they have been retrieved. The delete is only performed if the Exchange is committed.
     * If a rollback occurs, the object is not deleted.
     * <p/>
     * If this option is false, then the same objects will be retrieve over and over again on the polls. Therefore you
     * need to use the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
     * {@link AWS2S3Constants#BUCKET_NAME} and {@link AWS2S3Constants#KEY} headers, or only the
     * {@link AWS2S3Constants#KEY} header.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    /**
     * Move objects from S3 bucket to a different bucket after they have been retrieved. To accomplish the operation the
     * destinationBucket option must be set. The copy bucket operation is only performed if the Exchange is committed.
     * If a rollback occurs, the object is not moved.
     */
    public void setMoveAfterRead(boolean moveAfterRead) {
        this.moveAfterRead = moveAfterRead;
    }

    public String getDestinationBucket() {
        return destinationBucket;
    }

    /**
     * Define the destination bucket where an object must be moved when moveAfterRead is set to true.
     */
    public void setDestinationBucket(String destinationBucket) {
        this.destinationBucket = destinationBucket;
    }

    public String getDestinationBucketPrefix() {
        return destinationBucketPrefix;
    }

    /**
     * Define the destination bucket prefix to use when an object must be moved and moveAfterRead is set to true.
     */
    public void setDestinationBucketPrefix(String destinationBucketPrefix) {
        this.destinationBucketPrefix = destinationBucketPrefix;
    }

    public String getDestinationBucketSuffix() {
        return destinationBucketSuffix;
    }

    /**
     * Define the destination bucket suffix to use when an object must be moved and moveAfterRead is set to true.
     */
    public void setDestinationBucketSuffix(String destinationBucketSuffix) {
        this.destinationBucketSuffix = destinationBucketSuffix;
    }

    public boolean isDeleteAfterWrite() {
        return deleteAfterWrite;
    }

    /**
     * Delete file object after the S3 file has been uploaded
     */
    public void setDeleteAfterWrite(boolean deleteAfterWrite) {
        this.deleteAfterWrite = deleteAfterWrite;
    }

    public String getPolicy() {
        return policy;
    }

    /**
     * The policy for this queue to set in the `com.amazonaws.services.s3.AmazonS3#setBucketPolicy()` method.
     */
    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getStorageClass() {
        return storageClass;
    }

    /**
     * The storage class to set in the `com.amazonaws.services.s3.model.PutObjectRequest` request.
     */
    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the S3 client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the SQS client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * Specify a proxy port to be used inside the client definition.
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public AWS2S3Operations getOperation() {
        return operation;
    }

    /**
     * The operation to do in case the user don't want to do only an upload
     */
    public void setOperation(AWS2S3Operations operation) {
        this.operation = operation;
    }

    public boolean isAutocloseBody() {
        return autocloseBody;
    }

    /**
     * If this option is true and includeBody is false, then the S3Object.close() method will be called on exchange
     * completion. This option is strongly related to includeBody option. In case of setting includeBody to false and
     * autocloseBody to false, it will be up to the caller to close the S3Object stream. Setting autocloseBody to true,
     * will close the S3Object stream automatically.
     */
    public void setAutocloseBody(boolean autocloseBody) {
        this.autocloseBody = autocloseBody;
    }

    public boolean isUseAwsKMS() {
        return useAwsKMS;
    }

    /**
     * Define if KMS must be used or not
     */
    public void setUseAwsKMS(boolean useAwsKMS) {
        this.useAwsKMS = useAwsKMS;
    }

    public String getAwsKMSKeyId() {
        return awsKMSKeyId;
    }

    /**
     * Define the id of KMS key to use in case KMS is enabled
     */
    public void setAwsKMSKeyId(String awsKMSKeyId) {
        this.awsKMSKeyId = awsKMSKeyId;
    }

    public boolean isUseCustomerKey() {
        return useCustomerKey;
    }

    /**
     * Define if Customer Key must be used or not
     */
    public void setUseCustomerKey(boolean useCustomerKey) {
        this.useCustomerKey = useCustomerKey;
    }

    public String getCustomerKeyId() {
        return customerKeyId;
    }

    /**
     * Define the id of Customer key to use in case CustomerKey is enabled
     */
    public void setCustomerKeyId(String customerKeyId) {
        this.customerKeyId = customerKeyId;
    }

    public String getCustomerKeyMD5() {
        return customerKeyMD5;
    }

    /**
     * Define the MD5 of Customer key to use in case CustomerKey is enabled
     */
    public void setCustomerKeyMD5(String customerKeyMD5) {
        this.customerKeyMD5 = customerKeyMD5;
    }

    public String getCustomerAlgorithm() {
        return customerAlgorithm;
    }

    /**
     * Define the customer algorithm to use in case CustomerKey is enabled
     */
    public void setCustomerAlgorithm(String customerAlgorithm) {
        this.customerAlgorithm = customerAlgorithm;
    }

    public boolean isForcePathStyle() {
        return forcePathStyle;
    }

    /**
     * Set whether the S3 client should use path-style URL instead of virtual-hosted-style
     */
    public void setForcePathStyle(boolean forcePathStyle) {
        this.forcePathStyle = forcePathStyle;
    }

    /**
     * Set whether the S3 client should expect to load credentials through a default credentials provider.
     */
    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public Boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    /**
     * Set whether the S3 client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Setting the autocreation of the S3 bucket bucketName. This will apply also in case of moveAfterRead option
     * enabled and it will create the destinationBucket if it doesn't exist already.
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public String getKeyName() {
        return keyName;
    }

    /**
     * Setting the key name for an element in the bucket through endpoint parameter
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overidding the endpoint. This option needs to be used in combination with uriEndpointOverride
     * option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public S3Presigner getAmazonS3Presigner() {
        return amazonS3Presigner;
    }

    /**
     * An S3 Presigner for Request, used mainly in createDownloadLink operation
     */
    public void setAmazonS3Presigner(S3Presigner amazonS3Presigner) {
        this.amazonS3Presigner = amazonS3Presigner;
    }

    public boolean isStreamingUploadMode() {
        return streamingUploadMode;
    }

    /**
     * When stream mode is true the upload to bucket will be done in streaming
     */
    public void setStreamingUploadMode(boolean streamingUploadMode) {
        this.streamingUploadMode = streamingUploadMode;
    }

    public int getBatchMessageNumber() {
        return batchMessageNumber;
    }

    /**
     * The number of messages composing a batch in streaming upload mode
     */
    public void setBatchMessageNumber(int batchMessageNumber) {
        this.batchMessageNumber = batchMessageNumber;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * The batch size (in bytes) in streaming upload mode
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * The buffer size (in bytes) in streaming upload mode
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public AWSS3NamingStrategyEnum getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * The naming strategy to use in streaming upload mode
     */
    public void setNamingStrategy(AWSS3NamingStrategyEnum namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public long getStreamingUploadTimeout() {
        return streamingUploadTimeout;
    }

    /**
     * While streaming upload mode is true, this option set the timeout to complete upload
     */
    public void setStreamingUploadTimeout(long streamingUploadTimeout) {
        this.streamingUploadTimeout = streamingUploadTimeout;
    }

    public AWSS3RestartingPolicyEnum getRestartingPolicy() {
        return restartingPolicy;
    }

    /**
     * The restarting policy to use in streaming upload mode
     */
    public void setRestartingPolicy(AWSS3RestartingPolicyEnum restartingPolicy) {
        this.restartingPolicy = restartingPolicy;
    }

    public boolean isUseSSES3() {
        return useSSES3;
    }

    /**
     * Define if SSE S3 must be used or not
     */
    public void setUseSSES3(boolean useSSES3) {
        this.useSSES3 = useSSES3;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider this parameter will set the profile name
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public AWS2S3Configuration copy() {
        try {
            return (AWS2S3Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
