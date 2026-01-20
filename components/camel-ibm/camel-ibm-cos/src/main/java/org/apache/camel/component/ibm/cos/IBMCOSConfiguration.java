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
package org.apache.camel.component.ibm.cos;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * IBM COS component configuration
 */
@UriParams
public class IBMCOSConfiguration implements Cloneable {

    private String bucketName;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private AmazonS3 cosClient;
    @UriParam(label = "security", secret = true)
    private String apiKey;
    @UriParam(label = "security", secret = true)
    private String serviceInstanceId;
    @UriParam
    private String endpointUrl;
    @UriParam
    private String location;
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam
    private String prefix;
    @UriParam
    private String delimiter;
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
    private long partSize = 25L * 1024 * 1024;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam(label = "producer")
    private IBMCOSOperations operation;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean autocloseBody = true;
    @UriParam(label = "common", defaultValue = "false")
    private boolean autoCreateBucket;
    @UriParam(label = "producer")
    private String keyName;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeFolders = true;

    public IBMCOSConfiguration copy() {
        try {
            return (IBMCOSConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Name of the bucket
     */
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public AmazonS3 getCosClient() {
        return cosClient;
    }

    /**
     * Reference to an IBM COS Client instance in the registry
     */
    public void setCosClient(AmazonS3 cosClient) {
        this.cosClient = cosClient;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * IBM Cloud API Key for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceInstanceId() {
        return serviceInstanceId;
    }

    /**
     * IBM COS Service Instance ID (CRN)
     */
    public void setServiceInstanceId(String serviceInstanceId) {
        this.serviceInstanceId = serviceInstanceId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    /**
     * IBM COS Endpoint URL (e.g., https://s3.us-south.cloud-object-storage.appdomain.cloud) Note that some operations
     * requires to use a regional endpoint URL instead of a cross-region one.
     */
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getLocation() {
        return location;
    }

    /**
     * IBM COS Location/Region (e.g., us-south, eu-gb)
     */
    public void setLocation(String location) {
        this.location = location;
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

    public String getPrefix() {
        return prefix;
    }

    /**
     * The prefix to use for listing objects
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter to use for listing objects
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete the object from IBM COS after it has been retrieved
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    /**
     * Move the object to a different bucket after it has been retrieved
     */
    public void setMoveAfterRead(boolean moveAfterRead) {
        this.moveAfterRead = moveAfterRead;
    }

    public String getDestinationBucket() {
        return destinationBucket;
    }

    /**
     * The destination bucket to move objects to
     */
    public void setDestinationBucket(String destinationBucket) {
        this.destinationBucket = destinationBucket;
    }

    public String getDestinationBucketPrefix() {
        return destinationBucketPrefix;
    }

    /**
     * The prefix to add to objects in the destination bucket
     */
    public void setDestinationBucketPrefix(String destinationBucketPrefix) {
        this.destinationBucketPrefix = destinationBucketPrefix;
    }

    public String getDestinationBucketSuffix() {
        return destinationBucketSuffix;
    }

    /**
     * The suffix to add to objects in the destination bucket
     */
    public void setDestinationBucketSuffix(String destinationBucketSuffix) {
        this.destinationBucketSuffix = destinationBucketSuffix;
    }

    public boolean isDeleteAfterWrite() {
        return deleteAfterWrite;
    }

    /**
     * Delete the object from the local filesystem after uploading
     */
    public void setDeleteAfterWrite(boolean deleteAfterWrite) {
        this.deleteAfterWrite = deleteAfterWrite;
    }

    public boolean isMultiPartUpload() {
        return multiPartUpload;
    }

    /**
     * Use multi-part upload for large files
     */
    public void setMultiPartUpload(boolean multiPartUpload) {
        this.multiPartUpload = multiPartUpload;
    }

    public long getPartSize() {
        return partSize;
    }

    /**
     * Part size for multi-part uploads (default 25MB)
     */
    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public String getStorageClass() {
        return storageClass;
    }

    /**
     * The storage class to use when storing objects (e.g., STANDARD, VAULT, COLD, FLEX)
     */
    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    /**
     * Include the object body in the exchange
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public IBMCOSOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(IBMCOSOperations operation) {
        this.operation = operation;
    }

    public boolean isAutocloseBody() {
        return autocloseBody;
    }

    /**
     * Whether to automatically close the object input stream after processing
     */
    public void setAutocloseBody(boolean autocloseBody) {
        this.autocloseBody = autocloseBody;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Automatically create the bucket if it doesn't exist
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public String getKeyName() {
        return keyName;
    }

    /**
     * The key name for the object
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean isIncludeFolders() {
        return includeFolders;
    }

    /**
     * Include folders/directories when listing objects
     */
    public void setIncludeFolders(boolean includeFolders) {
        this.includeFolders = includeFolders;
    }
}
