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

import io.minio.CopyConditions;
import io.minio.MinioClient;
import io.minio.ServerSideEncryption;
import io.minio.ServerSideEncryptionCustomerKey;
import io.minio.SetBucketPolicyArgs;
import okhttp3.OkHttpClient;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class MinioConfiguration implements Cloneable {

    @UriParam
    private String endpoint;
    @UriParam
    private Integer proxyPort;

    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(defaultValue = "false")
    private boolean useAWSIAMCredentials;
    @UriParam(defaultValue = "false")
    private boolean isSecure;

    @UriParam
    private String region;

    @UriParam(defaultValue = "false")
    private boolean objectLock;

    @UriParam
    private OkHttpClient customHttpClient;

    @UriParam
    private String bucketName;
    @UriParam(defaultValue = "true")
    private boolean autoCreateBucket = true;

    @UriParam
    private ServerSideEncryptionCustomerKey serverSideEncryption;
    @UriParam
    private ServerSideEncryption srcServerSideEncryption;

    @UriParam
    private MinioClient minioClient;

    @UriParam(label = "consumer")
    private String objectName;
    @UriParam(label = "consumer")
    private String prefix;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean recursive;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean useVersion1;
    @UriParam(label = "consumer")
    private long offset;
    @UriParam(label = "consumer")
    private long length;
    @UriParam(label = "consumer")
    private String srcBucketName;
    @UriParam(label = "consumer")
    private String srcObjectName;
    @UriParam(label = "consumer")
    private CopyConditions copyConditions;
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean moveAfterRead;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean autocloseBody = true;

    @UriParam(label = "producer")
    private String keyName;
    @UriParam(label = "producer")
    private boolean deleteAfterWrite;
    @UriParam(label = "producer", defaultValue = "" + 25 * 1024 * 1024)
    private long partSize = 25 * 1024 * 1024;
    @UriParam
    private SetBucketPolicyArgs policy;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "producer", enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,getObjectRange")
    private MinioOperations operation;
    @UriParam
    private boolean pathStyleAccess;


    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Endpoint can be an URL, domain name, IPv4 address or IPv6 address
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * (Optional) TCP/IP port number. 80 and 443 are used as defaults for HTTP and HTTPS.
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Secret Access Key or Minio Access Key.
     * If not set camel will connect to service for anonymous access.
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Access Key Id or Minio Secret Key.
     * If not set camel will connect to service for anonymous access.
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isUseAWSIAMCredentials() {
        return useAWSIAMCredentials;
    }

    /**
     * Set this flag true if you use AWS IAM Credentials to create MinIO client object
     * if this is set to false (as by default value) camel will not look for AWS connection.
     */
    public void setUseAWSIAMCredentials(boolean useAWSIAMCredentials) {
        this.useAWSIAMCredentials = useAWSIAMCredentials;
    }

    public boolean isSecure() {
        return isSecure;
    }

    /**
     * (Optional) Flag to indicate to use secure connection to minio service or not.
     */
    public void setSecure(boolean secure) {
        isSecure = secure;
    }

    public String getRegion() {
        return region;
    }

    /**
     * (Optional) The region in which Minio client needs to work. When using this parameter,
     * the configuration will expect the lowercase name of the region (for
     * example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isObjectLock() {
        return objectLock;
    }

    public void setObjectLock(boolean objectLock) {
        this.objectLock = objectLock;
    }

    public OkHttpClient getCustomHttpClient() {
        return customHttpClient;
    }

    /**
     * (Optional) Set custom HTTP client for authenticated access.
     */
    public void setCustomHttpClient(OkHttpClient customHttpClient) {
        this.customHttpClient = customHttpClient;
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

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Setting the autocreation of the bucket if bucket name not exist.
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public ServerSideEncryptionCustomerKey getServerSideEncryption() {
        return serverSideEncryption;
    }

    /**
     * (Optional) Server-side encryption.
     */
    public void setServerSideEncryption(ServerSideEncryptionCustomerKey serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
    }

    public ServerSideEncryption getSrcServerSideEncryption() {
        return srcServerSideEncryption;
    }

    /**
     * (Optional) Server-side encryption for source object while copy/move objects.
     */
    public void setSrcServerSideEncryption(ServerSideEncryption srcServerSideEncryption) {
        this.srcServerSideEncryption = srcServerSideEncryption;
    }

    public MinioClient getMinioClient() {
        return minioClient;
    }

    /**
     * Reference to a Minio Client object in the registry.
     */
    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String getObjectName() {
        return objectName;
    }

    /**
     * To get the object from the bucket with the given object name
     */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * (Optional) Object name starts with prefix.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isRecursive() {
        return recursive;
    }

    /**
     * (Optional) List recursively than directory structure emulation.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public boolean isUseVersion1() {
        return useVersion1;
    }

    /**
     * (Optional) when true, version 1 of REST API is used.
     */
    public void setUseVersion1(boolean useVersion1) {
        this.useVersion1 = useVersion1;
    }

    public long getOffset() {
        return offset;
    }

    /**
     * (Optional) Start byte position of object data.
     */
    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * (Optional) Number of bytes of object data from offset.
     */
    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getSrcBucketName() {
        return srcBucketName;
    }

    /**
     * Source bucket name.
     */
    public void setSrcBucketName(String srcBucketName) {
        this.srcBucketName = srcBucketName;
    }

    public String getSrcObjectName() {
        return srcObjectName;
    }

    /**
     * (Optional) Source object name.
     */
    public void setSrcObjectName(String srcObjectName) {
        this.srcObjectName = srcObjectName;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * (Optional) Set destination folder when downloads object in bucket.
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * (Optional) Delete objects from Minio after they have been retrieved. The delete is only
     * performed if the Exchange is committed. If a rollback occurs, the object
     * is not deleted.
     * <p/>
     * If this option is false, then the same objects will be retrieve over and
     * over again on the polls. Therefore you need to use the Idempotent
     * Consumer EIP in the route to filter out duplicates. You can filter using
     * the {@link MinioConstants#BUCKET_NAME} and {@link MinioConstants#KEY}
     * headers, or only the {@link MinioConstants#KEY} header.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    /**
     * Move objects from S3 bucket to a different bucket after they have been retrieved. To accomplish the operation
     * the destinationBucket option must be set.
     * The copy bucket operation is only performed if the Exchange is committed. If a rollback occurs, the object
     * is not moved.
     */
    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    public void setMoveAfterRead(boolean moveAfterRead) {
        this.moveAfterRead = moveAfterRead;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    /**
     * If it is true, the exchange body will be set to a stream to the contents
     * of the file. If false, the headers will be set with the Minio object
     * metadata, but the body will be null. This option is strongly related to
     * autocloseBody option. In case of setting includeBody to true and
     * autocloseBody to false, it will be up to the caller to close the MinioObject
     * stream. Setting autocloseBody to true, will close the MinioObject stream
     * automatically.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isAutocloseBody() {
        return autocloseBody;
    }

    /**
     * If this option is true and includeBody is true, then the MinioObject.close()
     * method will be called on exchange completion. This option is strongly
     * related to includeBody option. In case of setting includeBody to true and
     * autocloseBody to false, it will be up to the caller to close the MinioObject
     * stream. Setting autocloseBody to true, will close the MinioObject stream
     * automatically.
     */
    public void setAutocloseBody(boolean autocloseBody) {
        this.autocloseBody = autocloseBody;
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

    public boolean isDeleteAfterWrite() {
        return deleteAfterWrite;
    }

    /**
     * Delete file object after the Minio file has been uploaded
     */
    public void setDeleteAfterWrite(boolean deleteAfterWrite) {
        this.deleteAfterWrite = deleteAfterWrite;
    }

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

    public SetBucketPolicyArgs getPolicy() {
        return policy;
    }

    /**
     * The policy for this queue to set in the method.
     */
    public void setPolicy(SetBucketPolicyArgs policy) {
        this.policy = policy;
    }

    public String getStorageClass() {
        return storageClass;
    }

    /**
     * The storage class to set in the request.
     */
    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public CopyConditions getCopyConditions() {
        return copyConditions;
    }

    /**
     * (Optional) Conditions to be used in copy operation.
     */
    public void setCopyConditions(CopyConditions copyConditions) {
        this.copyConditions = copyConditions;
    }

    public MinioOperations getOperation() {
        return operation;
    }

    /**
     * The operation to do in case the user don't want to do only an upload
     */
    public void setOperation(MinioOperations operation) {
        this.operation = operation;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    /**
     * Some description of this option(isPathStyleAccess), and what it does
     */
    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }
}
