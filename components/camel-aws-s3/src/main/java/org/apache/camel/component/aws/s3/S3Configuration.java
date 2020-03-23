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
package org.apache.camel.component.aws.s3;

import com.amazonaws.Protocol;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class S3Configuration implements Cloneable {

    private String bucketName;
    @UriParam
    private AmazonS3 amazonS3Client;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    private EndpointConfiguration endpointConfiguration;
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam(label = "consumer")
    private String prefix;
    @UriParam(label = "consumer")
    private String delimiter;
    @UriParam
    private String region;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(label = "producer")
    private boolean deleteAfterWrite;
    @UriParam(label = "producer")
    private boolean multiPartUpload;
    @UriParam(label = "producer", defaultValue = "" + 25 * 1024 * 1024)
    private long partSize = 25 * 1024 * 1024;
    @UriParam
    private String policy;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "producer")
    private String serverSideEncryption;

    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam
    private boolean pathStyleAccess;
    @UriParam(label = "producer", enums = "copyObject,deleteBucket,listBuckets,downloadLink")
    private S3Operations operation;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean autocloseBody = true;
    @UriParam(label = "common,advanced")
    private EncryptionMaterials encryptionMaterials;
    @UriParam(label = "common,advanced", defaultValue = "false")
    private boolean useEncryption;
    @UriParam(label = "common, advanced", defaultValue = "false")
    private boolean chunkedEncodingDisabled;
    @UriParam(label = "common, advanced", defaultValue = "false")
    private boolean accelerateModeEnabled;
    @UriParam(label = "common, advanced", defaultValue = "false")
    private boolean dualstackEnabled;
    @UriParam(label = "common, advanced", defaultValue = "false")
    private boolean payloadSigningEnabled;
    @UriParam(label = "common, advanced", defaultValue = "false")
    private boolean forceGlobalBucketAccessEnabled;
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoCreateBucket = true;
    @UriParam(label = "producer,advanced", defaultValue = "false")
    private boolean useAwsKMS;
    @UriParam(label = "producer,advanced")
    private String awsKMSKeyId;
    @UriParam(defaultValue = "false")
    private boolean useIAMCredentials;
    @UriParam(label = "producer")
    private String keyName;

    public long getPartSize() {
        return partSize;
    }

    /**
     * Setup the partSize which is used in multi part upload, the default size
     * is 25M.
     */
    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public boolean isMultiPartUpload() {
        return multiPartUpload;
    }

    /**
     * If it is true, camel will upload the file with multi part format, the
     * part size is decided by the option of `partSize`
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

    public EndpointConfiguration getEndpointConfiguration() {
        return endpointConfiguration;
    }

    /**
     * Amazon AWS Endpoint Configuration
     */
    public void setEndpointConfiguration(EndpointConfiguration endpointConfiguration) {
        this.endpointConfiguration = endpointConfiguration;
    }

    public AmazonS3 getAmazonS3Client() {
        return amazonS3Client;
    }

    /**
     * Reference to a `com.amazonaws.services.s3.AmazonS3` in the registry.
     */
    public void setAmazonS3Client(AmazonS3 amazonS3Client) {
        this.amazonS3Client = amazonS3Client;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * The prefix which is used in the
     * com.amazonaws.services.s3.model.ListObjectsRequest to only consume
     * objects we are interested in.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter which is used in the
     * com.amazonaws.services.s3.model.ListObjectsRequest to only consume
     * objects we are interested in.
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Name of the bucket. The bucket will be created if it doesn't already
     * exists.
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
     * The region in which S3 client needs to work. When using this parameter,
     * the configuration will expect the capitalized name of the region (for
     * example AP_EAST_1) You'll need to use the name Regions.EU_WEST_1.name()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * If it is true, the exchange body will be set to a stream to the contents
     * of the file. If false, the headers will be set with the S3 object
     * metadata, but the body will be null. This option is strongly related to
     * autocloseBody option. In case of setting includeBody to true and
     * autocloseBody to false, it will be up to the caller to close the S3Object
     * stream. Setting autocloseBody to true, will close the S3Object stream
     * automatically.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete objects from S3 after they have been retrieved. The delete is only
     * performed if the Exchange is committed. If a rollback occurs, the object
     * is not deleted.
     * <p/>
     * If this option is false, then the same objects will be retrieve over and
     * over again on the polls. Therefore you need to use the Idempotent
     * Consumer EIP in the route to filter out duplicates. You can filter using
     * the {@link S3Constants#BUCKET_NAME} and {@link S3Constants#KEY} headers,
     * or only the {@link S3Constants#KEY} header.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
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
     * The policy for this queue to set in the
     * `com.amazonaws.services.s3.AmazonS3#setBucketPolicy()` method.
     */
    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getStorageClass() {
        return storageClass;
    }

    /**
     * The storage class to set in the
     * `com.amazonaws.services.s3.model.PutObjectRequest` request.
     */
    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public String getServerSideEncryption() {
        return serverSideEncryption;
    }

    /**
     * Sets the server-side encryption algorithm when encrypting the object
     * using AWS-managed keys. For example use <tt>AES256</tt>.
     */
    public void setServerSideEncryption(String serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
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

    /**
     * Whether or not the S3 client should use path style access
     */
    public void setPathStyleAccess(final boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public S3Operations getOperation() {
        return operation;
    }

    /**
     * The operation to do in case the user don't want to do only an upload
     */
    public void setOperation(S3Operations operation) {
        this.operation = operation;
    }

    public boolean isAutocloseBody() {
        return autocloseBody;
    }

    /**
     * If this option is true and includeBody is true, then the S3Object.close()
     * method will be called on exchange completion. This option is strongly
     * related to includeBody option. In case of setting includeBody to true and
     * autocloseBody to false, it will be up to the caller to close the S3Object
     * stream. Setting autocloseBody to true, will close the S3Object stream
     * automatically.
     */
    public void setAutocloseBody(boolean autocloseBody) {
        this.autocloseBody = autocloseBody;
    }

    public EncryptionMaterials getEncryptionMaterials() {
        return encryptionMaterials;
    }

    /**
     * The encryption materials to use in case of Symmetric/Asymmetric client
     * usage
     */
    public void setEncryptionMaterials(EncryptionMaterials encryptionMaterials) {
        this.encryptionMaterials = encryptionMaterials;
    }

    public boolean isUseEncryption() {
        return useEncryption;
    }

    /**
     * Define if encryption must be used or not
     */
    public void setUseEncryption(boolean useEncryption) {
        this.useEncryption = useEncryption;
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

    public boolean isChunkedEncodingDisabled() {
        return chunkedEncodingDisabled;
    }

    /**
     * Define if disabled Chunked Encoding is true or false
     */
    public void setChunkedEncodingDisabled(boolean chunkedEncodingDisabled) {
        this.chunkedEncodingDisabled = chunkedEncodingDisabled;
    }

    public boolean isAccelerateModeEnabled() {
        return accelerateModeEnabled;
    }

    /**
     * Define if Accelerate Mode enabled is true or false
     */
    public void setAccelerateModeEnabled(boolean accelerateModeEnabled) {
        this.accelerateModeEnabled = accelerateModeEnabled;
    }

    public boolean isDualstackEnabled() {
        return dualstackEnabled;
    }

    /**
     * Define if Dualstack enabled is true or false
     */
    public void setDualstackEnabled(boolean dualstackEnabled) {
        this.dualstackEnabled = dualstackEnabled;
    }

    public boolean isPayloadSigningEnabled() {
        return payloadSigningEnabled;
    }

    /**
     * Define if Payload Signing enabled is true or false
     */
    public void setPayloadSigningEnabled(boolean payloadSigningEnabled) {
        this.payloadSigningEnabled = payloadSigningEnabled;
    }

    public boolean isForceGlobalBucketAccessEnabled() {
        return forceGlobalBucketAccessEnabled;
    }

    /**
     * Define if Force Global Bucket Access enabled is true or false
     */
    public void setForceGlobalBucketAccessEnabled(boolean forceGlobalBucketAccessEnabled) {
        this.forceGlobalBucketAccessEnabled = forceGlobalBucketAccessEnabled;
    }

    /**
     * Set whether the S3 client should expect to load credentials on an EC2
     * instance or to expect static credentials to be passed in.
     */
    public void setUseIAMCredentials(Boolean useIAMCredentials) {
        this.useIAMCredentials = useIAMCredentials;
    }

    public Boolean isUseIAMCredentials() {
        return useIAMCredentials;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Setting the autocreation of the bucket
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public String getKeyName() {
        return keyName;
    }

    /**
     * Setting the key name for an element in the bucket through endpoint
     * parameter
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean hasProxyConfiguration() {
        return ObjectHelper.isNotEmpty(getProxyHost()) && ObjectHelper.isNotEmpty(getProxyPort());
    }

    // *************************************************
    //
    // *************************************************

    public S3Configuration copy() {
        try {
            return (S3Configuration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
