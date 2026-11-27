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
import okhttp3.OkHttpClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
@Deprecated(since = "4.23")
public class MinioConfiguration implements Cloneable {

    @UriPath(description = "The name of the bucket. Unless objectName is set, the endpoint checks at startup that the "
                           + "bucket exists, and creates it if autoCreateBucket is true. The CamelMinioOverrideBucketName "
                           + "header replaces it for the producer.")
    @Metadata(required = true)
    private String bucketName;

    @UriParam(description = "The MinIO server, or other Amazon S3 compatible service, to connect to: a URL such as "
                            + "http://localhost:9000, or a host name or IP address, for which the client uses HTTPS on port "
                            + "443. Required unless minioClient is set.",
              label = "common")
    private String endpoint;
    @UriParam(description = "The TCP port of the server. When set, it replaces the port of the endpoint option, and the "
                            + "secure option sets the scheme. Not used when minioClient is set.",
              label = "common")
    private Integer proxyPort;
    @UriParam(description = "Whether the client uses HTTPS (true) or HTTP (false) when proxyPort is set. Without proxyPort, "
                            + "this option is not used and the scheme of the endpoint option applies.",
              label = "common")
    private boolean secure;
    @UriParam(description = "The region of the server, for example us-east-1. The client signs its requests for this region, "
                            + "and the component creates buckets in it. If not set, the client asks the server for the region "
                            + "of the bucket.",
              label = "common")
    private String region;
    @UriParam(description = "To use a custom okhttp3.OkHttpClient for the requests to the server, for example to configure "
                            + "timeouts, a proxy or the connection pool. Not used when minioClient is set.",
              label = "advanced")
    private OkHttpClient customHttpClient;

    @UriParam(description = "The access key (user name) for the requests to the server. If accessKey or secretKey is not "
                            + "set, the client sends the requests without credentials. Not used when minioClient is set.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret key (password) of the accessKey. If accessKey or secretKey is not set, the client "
                            + "sends the requests without credentials. Not used when minioClient is set.",
              label = "security", security = "secret")
    private String secretKey;

    @UriParam(description = "Whether to create the bucket at startup if it does not exist (skipped when objectName is set), "
                            + "and, for a consumer with moveAfterRead, the destinationBucketName bucket. If false, a missing "
                            + "destination bucket fails the start of the consumer.",
              label = "common", defaultValue = "true")
    private boolean autoCreateBucket = true;
    @UriParam(description = "Whether to enable object locking on the buckets that the component creates (see "
                            + "autoCreateBucket). It does not change an existing bucket.",
              label = "common", defaultValue = "false")
    private boolean objectLock;
    @UriParam(description = "The bucket policy, as JSON, that the endpoint sets on the bucket at startup. Not applied when "
                            + "objectName is set.",
              label = "common")
    private String policy;
    @UriParam(description = "The key for server-side encryption with a customer-provided key (SSE-C), as an "
                            + "io.minio.ServerSideEncryption.CustomerKey. The consumer uses it to get the objects and to copy "
                            + "them with moveAfterRead; the producer to get, upload and copy objects. The producer fails to "
                            + "start if serverSideEncryption is also set.",
              label = "security")
    private ServerSideEncryption.CustomerKey serverSideEncryptionCustomerKey;
    @UriParam(description = "The server-side encryption, as an io.minio.ServerSideEncryption such as ServerSideEncryption.S3 "
                            + "or ServerSideEncryption.KMS, of the objects that the producer uploads or copies with "
                            + "copyObject, and that the consumer copies with moveAfterRead. The producer fails to start if "
                            + "serverSideEncryptionCustomerKey is also set.",
              label = "security")
    private ServerSideEncryption serverSideEncryption;
    @UriParam(description = "To use a custom io.minio.MinioClient. When this option is set or autowired, the endpoint, "
                            + "proxyPort, secure, region, accessKey, secretKey and customHttpClient options are not used.",
              label = "common")
    @Metadata(autowired = true)
    private MinioClient minioClient;

    @UriParam(description = "The maximum number of objects that the consumer lists and consumes in one poll, which is also "
                            + "the page size of its listing requests. Use 0 or a negative number for no limit. Not used when "
                            + "objectName is set.",
              label = "consumer", defaultValue = "10")
    private int maxMessagesPerPoll = 10;
    @UriParam(description = "Not used: the component does not pass this value to the MinIO client. Configure the connection "
                            + "pool of the OkHttpClient of the customHttpClient option instead.",
              label = "consumer", defaultValue = "60")
    private int maxConnections = 50 + maxMessagesPerPoll;
    @UriParam(description = "The name of the one object that the consumer gets in each poll, instead of listing the bucket. "
                            + "When set, the endpoint does not check or create the bucket, and does not set the policy, at "
                            + "startup.",
              label = "consumer")
    private String objectName;
    @UriParam(description = "The delimiter that the listing requests of the consumer use to group object names into folders. If "
                            + "not set, the listing uses /. Not used when recursive is true.",
              label = "consumer")
    private String delimiter;
    @UriParam(description = "Whether the listing requests of the consumer ask for the user metadata of the objects (a MinIO "
                            + "extension of ListObjectsV2). The consumer does not set this metadata on the message. The "
                            + "listing fails if useVersion1 or includeVersions is also true.",
              label = "consumer")
    private boolean includeUserMetadata;
    @UriParam(description = "Whether the consumer lists all versions of the objects. It still gets each listed object name "
                            + "once per poll, in the version that the versionId option selects, else the current version.",
              label = "consumer")
    private boolean includeVersions;
    @UriParam(description = "The prefix of the names of the objects that the consumer lists.",
              label = "consumer")
    private String prefix;
    @UriParam(description = "Whether the consumer lists all the objects below the prefix. If false, the listing stops at the "
                            + "delimiter and returns the folders as entries, which the consumer skips unless includeFolders is "
                            + "true.",
              label = "consumer")
    private boolean recursive;
    @UriParam(description = "The object name after which the consumer lists the bucket, at the start of each pass through "
                            + "the bucket. The listing returns only the names that sort after it.",
              label = "consumer")
    private String startAfter;
    @UriParam(description = "Whether the consumer lists the bucket with version 1 of the ListObjects API instead of "
                            + "ListObjectsV2.",
              label = "consumer")
    private boolean useVersion1;
    @UriParam(description = "Whether the consumer also creates an exchange for the folder entries of the listing. By "
                            + "default, it skips them.",
              label = "consumer")
    private boolean includeFolders;
    @UriParam(description = "The position of the first byte of the object that the consumer gets. With moveAfterRead, the "
                            + "consumer copies only the bytes from this position. The default 0 is the start of the object.",
              label = "consumer")
    private long offset;
    @UriParam(description = "The number of bytes of the object, from offset, that the consumer gets. With moveAfterRead, the "
                            + "consumer copies only these bytes. The default 0 means up to the end of the object.",
              label = "consumer")
    private long length;
    @UriParam(description = "The version of the objects that the consumer gets, deletes with deleteAfterRead and copies with "
                            + "moveAfterRead. If not set, the current version.",
              label = "consumer")
    private String versionId;
    @UriParam(description = "The consumer gets an object only if its ETag matches this value (the If-Match condition). For "
                            + "an object that does not match, the request fails and the consumer reports an error.",
              label = "consumer")
    private String matchETag;
    @UriParam(description = "The consumer gets an object only if its ETag does not match this value (the If-None-Match "
                            + "condition). For an object that matches, the request fails and the consumer reports an error.",
              label = "consumer")
    private String notMatchETag;
    @UriParam(description = "The consumer gets an object only if it was modified after this time (the If-Modified-Since "
                            + "condition). For another object, the request fails and the consumer reports an error.",
              label = "consumer")
    private ZonedDateTime modifiedSince;
    @UriParam(description = "The consumer gets an object only if it was not modified after this time (the "
                            + "If-Unmodified-Since condition). For another object, the request fails and the consumer reports "
                            + "an error.",
              label = "consumer")
    private ZonedDateTime unModifiedSince;
    @UriParam(description = "The bucket that the consumer moves the consumed objects to, with moveAfterRead, which requires "
                            + "it. The producer reads the bucket of copyObject from the CamelMinioDestinationBucketName header "
                            + "instead.",
              label = "consumer")
    private String destinationBucketName;
    @UriParam(description = "The object name that the consumer moves the consumed objects to, in destinationBucketName, with "
                            + "moveAfterRead. If not set, the name of the consumed object. With a fixed name, each moved "
                            + "object replaces the previous one.",
              label = "consumer")
    private String destinationObjectName;
    @UriParam(description = "Whether the consumer deletes the object from the bucket after the exchange completes "
                            + "successfully. If false, the consumer gets the same objects again in later polls: use the "
                            + "Idempotent Consumer EIP to filter out duplicates, for example by the CamelMinioObjectName "
                            + "header.",
              label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(description = "Whether the consumer copies the object to destinationBucketName after the exchange completes "
                            + "successfully, and then deletes it from the bucket, also when deleteAfterRead is false.",
              label = "consumer", defaultValue = "false")
    private boolean moveAfterRead;
    @UriParam(description = "Whether the consumer bypasses the governance mode retention of object locking when it deletes "
                            + "an object with deleteAfterRead or moveAfterRead. This needs the s3:BypassGovernanceRetention "
                            + "permission.",
              label = "consumer", defaultValue = "false")
    private boolean bypassGovernanceMode;
    @UriParam(description = "Whether the consumer reads the content of the object into the body, as a byte array. If false, "
                            + "the body is null and the headers carry the metadata of the object.",
              label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam(description = "Whether the consumer closes the stream that it read the content of the object from, when the "
                            + "exchange is done. Only used when includeBody is true. If false, the consumer does not close the "
                            + "stream.",
              label = "consumer", defaultValue = "true")
    private boolean autoCloseBody = true;

    @UriParam(description = "The name of the object that the producer uploads, gets, deletes, copies (the source of "
                            + "copyObject) or creates a link for. The CamelMinioObjectName header replaces it.",
              label = "producer")
    private String keyName;
    @UriParam(description = "Whether the producer deletes the file after it uploaded it, when the body is a java.io.File, "
                            + "for example the file of a file consumer. Other bodies are not affected.",
              label = "producer")
    private boolean deleteAfterWrite;
    @UriParam(description = "The storage class of the objects that the producer uploads, sent as the x-amz-storage-class "
                            + "header, for example STANDARD or REDUCED_REDUNDANCY. The CamelMinioStorageClass header replaces "
                            + "it.",
              label = "producer")
    private String storageClass;
    @UriParam(description = "The operation that the producer performs. If not set, the producer uploads the body as an "
                            + "object. The CamelMinioOperation header replaces it.",
              label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteObjects,deleteBucket,listBuckets,getObject,getObjectRange,createDownloadLink,createUploadLink")
    private MinioOperations operation;
    @UriParam(description = "Whether the body is the builder of the request of the MinIO Java SDK, such as "
                            + "io.minio.GetObjectArgs.Builder for getObject, instead of the producer building the request from "
                            + "options and headers. deleteObjects works only with true, and listBuckets does not use it.",
              label = "producer")
    private boolean pojoRequest;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public OkHttpClient getCustomHttpClient() {
        return customHttpClient;
    }

    public void setCustomHttpClient(OkHttpClient customHttpClient) {
        this.customHttpClient = customHttpClient;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public boolean isObjectLock() {
        return objectLock;
    }

    public void setObjectLock(boolean objectLock) {
        this.objectLock = objectLock;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public ServerSideEncryption.CustomerKey getServerSideEncryptionCustomerKey() {
        return serverSideEncryptionCustomerKey;
    }

    public void setServerSideEncryptionCustomerKey(ServerSideEncryption.CustomerKey serverSideEncryptionCustomerKey) {
        this.serverSideEncryptionCustomerKey = serverSideEncryptionCustomerKey;
    }

    public ServerSideEncryption getServerSideEncryption() {
        return serverSideEncryption;
    }

    public void setServerSideEncryption(ServerSideEncryption serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
    }

    public MinioClient getMinioClient() {
        return minioClient;
    }

    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isIncludeUserMetadata() {
        return includeUserMetadata;
    }

    public void setIncludeUserMetadata(boolean includeUserMetadata) {
        this.includeUserMetadata = includeUserMetadata;
    }

    public boolean isIncludeVersions() {
        return includeVersions;
    }

    public void setIncludeVersions(boolean includeVersions) {
        this.includeVersions = includeVersions;
    }

    public boolean isIncludeFolders() {
        return includeFolders;
    }

    public void setIncludeFolders(boolean includeFolders) {
        this.includeFolders = includeFolders;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getStartAfter() {
        return startAfter;
    }

    public void setStartAfter(String startAfter) {
        this.startAfter = startAfter;
    }

    public boolean isUseVersion1() {
        return useVersion1;
    }

    public void setUseVersion1(boolean useVersion1) {
        this.useVersion1 = useVersion1;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getMatchETag() {
        return matchETag;
    }

    public void setMatchETag(String matchETag) {
        this.matchETag = matchETag;
    }

    public String getNotMatchETag() {
        return notMatchETag;
    }

    public void setNotMatchETag(String notMatchETag) {
        this.notMatchETag = notMatchETag;
    }

    public ZonedDateTime getModifiedSince() {
        return modifiedSince;
    }

    public void setModifiedSince(ZonedDateTime modifiedSince) {
        this.modifiedSince = modifiedSince;
    }

    public ZonedDateTime getUnModifiedSince() {
        return unModifiedSince;
    }

    public void setUnModifiedSince(ZonedDateTime unModifiedSince) {
        this.unModifiedSince = unModifiedSince;
    }

    public String getDestinationBucketName() {
        return destinationBucketName;
    }

    public void setDestinationBucketName(String destinationBucketName) {
        this.destinationBucketName = destinationBucketName;
    }

    public String getDestinationObjectName() {
        return destinationObjectName;
    }

    public void setDestinationObjectName(String destinationObjectName) {
        this.destinationObjectName = destinationObjectName;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    public void setMoveAfterRead(boolean moveAfterRead) {
        this.moveAfterRead = moveAfterRead;
    }

    public boolean isBypassGovernanceMode() {
        return bypassGovernanceMode;
    }

    public void setBypassGovernanceMode(boolean bypassGovernanceMode) {
        this.bypassGovernanceMode = bypassGovernanceMode;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isAutoCloseBody() {
        return autoCloseBody;
    }

    public void setAutoCloseBody(boolean autoCloseBody) {
        this.autoCloseBody = autoCloseBody;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean isDeleteAfterWrite() {
        return deleteAfterWrite;
    }

    public void setDeleteAfterWrite(boolean deleteAfterWrite) {
        this.deleteAfterWrite = deleteAfterWrite;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public MinioOperations getOperation() {
        return operation;
    }

    public void setOperation(MinioOperations operation) {
        this.operation = operation;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public MinioConfiguration copy() {
        try {
            return (MinioConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
