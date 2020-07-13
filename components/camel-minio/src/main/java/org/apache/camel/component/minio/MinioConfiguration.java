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

import java.time.ZonedDateTime;

import io.minio.MinioClient;
import io.minio.ServerSideEncryption;
import io.minio.ServerSideEncryptionCustomerKey;
import okhttp3.OkHttpClient;
import org.apache.camel.RuntimeCamelException;
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
    private boolean secure;

    @UriParam
    private String region;

    @UriParam
    private OkHttpClient customHttpClient;

    private String bucketName;
    @UriParam(defaultValue = "true")
    private boolean autoCreateBucket = true;
    @UriParam(defaultValue = "false")
    private boolean objectLock;

    @UriParam
    private ServerSideEncryptionCustomerKey serverSideEncryptionCustomerKey;
    @UriParam
    private ServerSideEncryption serverSideEncryption;

    @UriParam
    private MinioClient minioClient;

    @UriParam(label = "consumer")
    private String objectName;
    @UriParam(label = "consumer")
    private String delimiter;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean includeUserMetadata;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean includeVersions;
    @UriParam(label = "consumer")
    private String prefix;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean recursive;
    @UriParam(label = "consumer")
    private String startAfter;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean useVersion1;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean includeFolders;
    @UriParam(label = "consumer")
    private long offset;
    @UriParam(label = "consumer")
    private long length;
    @UriParam(label = "consumer")
    private String matchETag;
    @UriParam(label = "consumer")
    private String notMatchETag;
    @UriParam(label = "consumer")
    private ZonedDateTime modifiedSince;
    @UriParam(label = "consumer")
    private ZonedDateTime unModifiedSince;
    @UriParam(label = "consumer")
    private String destinationBucketName;
    @UriParam(label = "consumer")
    private String destinationObjectName;
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
    private String policy;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "producer", enums = "copyObject,listObjects,deleteObject,deleteObjects,deleteBucket,listBuckets,getObject,getObjectRange")
    private MinioOperations operation;

    @UriParam(defaultValue = "false")
    private boolean pojoRequest;
    private String versionId;
    @UriParam(defaultValue = "false")
    private boolean bypassGovernanceMode;


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

    public boolean isSecure() {
        return secure;
    }

    /**
     * (Optional) Flag to indicate to use secure connection to minio service or not.
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
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

    public boolean isObjectLock() {
        return objectLock;
    }

    /**
     * (Optional) Set when creating new bucket.
     */
    public void setObjectLock(boolean objectLock) {
        this.objectLock = objectLock;
    }

    public ServerSideEncryptionCustomerKey getServerSideEncryptionCustomerKey() {
        return serverSideEncryptionCustomerKey;
    }

    /**
     * (Optional) Server-side encryption for source object while copy/move objects.
     */
    public void setServerSideEncryptionCustomerKey(ServerSideEncryptionCustomerKey serverSideEncryptionCustomerKey) {
        this.serverSideEncryptionCustomerKey = serverSideEncryptionCustomerKey;
    }

    public ServerSideEncryption getServerSideEncryption() {
        return serverSideEncryption;
    }

    /**
     * (Optional) Server-side encryption.
     */
    public void setServerSideEncryption(ServerSideEncryptionCustomerKey serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
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

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter which is used in the
     * ListObjectsRequest to only consume
     * objects we are interested in.
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isIncludeUserMetadata() {
        return includeUserMetadata;
    }

    /**
     * The flag which is used in the
     * ListObjectsRequest to get objects with user meta data.
     */
    public void setIncludeUserMetadata(boolean includeUserMetadata) {
        this.includeUserMetadata = includeUserMetadata;
    }

    public boolean isIncludeVersions() {
        return includeVersions;
    }

    /**
     * The flag which is used in the
     * ListObjectsRequest to get objects with versioning.
     */
    public void setIncludeVersions(boolean includeVersions) {
        this.includeVersions = includeVersions;
    }

    public boolean isIncludeFolders() {
        return includeFolders;
    }

    /**
     * The flag which is used in the
     * ListObjectsRequest to set include folders.
     */
    public void setIncludeFolders(boolean includeFolders) {
        this.includeFolders = includeFolders;
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

    public String getStartAfter() {
        return startAfter;
    }

    /**
     * list objects in bucket after this object name.
     */
    public void setStartAfter(String startAfter) {
        this.startAfter = startAfter;
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

    public long getLength() {
        return length;
    }

    /**
     * (Optional) Number of bytes of object data from offset.
     */
    public void setLength(long length) {
        this.length = length;
    }

    public String getMatchETag() {
        return matchETag;
    }

    /**
     * Set match ETag parameter for get object(s).
     */
    public void setMatchETag(String matchETag) {
        this.matchETag = matchETag;
    }

    public String getNotMatchETag() {
        return notMatchETag;
    }

    /**
     * Set not match ETag parameter for get object(s).
     */
    public void setNotMatchETag(String notMatchETag) {
        this.notMatchETag = notMatchETag;
    }

    public ZonedDateTime getModifiedSince() {
        return modifiedSince;
    }

    /**
     * Set modified since parameter for get object(s).
     */
    public void setModifiedSince(ZonedDateTime modifiedSince) {
        this.modifiedSince = modifiedSince;
    }

    public ZonedDateTime getUnModifiedSince() {
        return unModifiedSince;
    }

    /**
     * Set un modified since parameter for get object(s).
     */
    public void setUnModifiedSince(ZonedDateTime unModifiedSince) {
        this.unModifiedSince = unModifiedSince;
    }

    public String getDestinationBucketName() {
        return destinationBucketName;
    }

    /**
     * Source bucket name.
     */
    public void setDestinationBucketName(String destinationBucketName) {
        this.destinationBucketName = destinationBucketName;
    }

    public String getDestinationObjectName() {
        return destinationObjectName;
    }

    /**
     * (Optional) Source object name.
     */
    public void setDestinationObjectName(String destinationObjectName) {
        this.destinationObjectName = destinationObjectName;
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
     * the {@link MinioConstants#BUCKET_NAME} and {@link MinioConstants#OBJECT_NAME}
     * headers, or only the {@link MinioConstants#OBJECT_NAME} header.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    /**
     * Move objects from bucket to a different bucket after they have been retrieved. To accomplish the operation
     * the destinationBucket option must be set.
     * The copy bucket operation is only performed if the Exchange is committed. If a rollback occurs, the object
     * is not moved.
     */
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

    public String getPolicy() {
        return policy;
    }

    /**
     * The policy for this queue to set in the method.
     */
    public void setPolicy(String policy) {
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

    public MinioOperations getOperation() {
        return operation;
    }

    /**
     * The operation to do in case the user don't want to do only an upload
     */
    public void setOperation(MinioOperations operation) {
        this.operation = operation;
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

    public String getVersionId() {
        return versionId;
    }

    /**
     * Set specific version_ID of a object when deleting the object
     */
    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public boolean isBypassGovernanceMode() {
        return bypassGovernanceMode;
    }

    /**
     * Set this flag if you want to bypassGovernanceMode when deleting a particular object
     */
    public void setBypassGovernanceMode(boolean bypassGovernanceMode) {
        this.bypassGovernanceMode = bypassGovernanceMode;
    }

    public MinioConfiguration copy() {
        try {
            return (MinioConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
