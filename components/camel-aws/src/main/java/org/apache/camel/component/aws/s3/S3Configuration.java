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
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam(label = "consumer")
    private String prefix;
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
    private String amazonS3Endpoint;
    @UriParam
    private String policy;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "producer")
    private String serverSideEncryption;
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
    @UriParam(label = "common")
    private EncryptionMaterials encryptionMaterials;
    @UriParam(label = "common", defaultValue = "false")
    private boolean useEncryption;

    public long getPartSize() {
        return partSize;
    }

    /**
     * Setup the partSize which is used in multi part upload,
     * the default size is 25M.
     */
    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public boolean isMultiPartUpload() {
        return multiPartUpload;
    }

    /**
     * If it is true, camel will upload the file with multi part
     * format, the part size is decided by the option of `partSize`
     */
    public void setMultiPartUpload(boolean multiPartUpload) {
        this.multiPartUpload = multiPartUpload;
    }

    /**
     * The region with which the AWS-S3 client wants to work with.
     */
    public void setAmazonS3Endpoint(String amazonS3Endpoint) {
        this.amazonS3Endpoint = amazonS3Endpoint;
    }

    public String getAmazonS3Endpoint() {
        return amazonS3Endpoint;
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

    public AmazonS3 getAmazonS3Client() {
        return amazonS3Client;
    }

    /**
     * Reference to a `com.amazonaws.services.sqs.AmazonS3` in the
     * link:registry.html[Registry].
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
     * The region where the bucket is located. This option is used in the
     * `com.amazonaws.services.s3.model.CreateBucketRequest`.
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
     * Sets the server-side encryption algorithm when encrypting
     * the object using AWS-managed keys. For example use <tt>AES256</tt>.
     */
    public void setServerSideEncryption(String serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
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

    boolean hasProxyConfiguration() {
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
