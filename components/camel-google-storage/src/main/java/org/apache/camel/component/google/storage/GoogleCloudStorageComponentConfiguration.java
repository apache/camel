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
package org.apache.camel.component.google.storage;

import com.google.cloud.storage.Storage;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudStorageComponentConfiguration implements Cloneable {

    @UriPath(label = "common", description = "Bucket name")
    @Metadata(required = true)
    private String bucketName;

    @UriParam(label = "common", description = "Service account key")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,createDownloadLink")
    private GoogleCloudStorageComponentOperations operation;

    @UriParam(label = "producer", description = "Object name")
    private String objectName;

    @UriParam(label = "common", defaultValue = "US-EAST1",
              description = "The Cloud Storage location to use when creating the new buckets")
    private String storageLocation;

    @UriParam(label = "common", defaultValue = "true")
    private boolean autoCreateBucket = true;

    @UriParam(label = "consumer")
    private boolean moveAfterRead;

    @UriParam(label = "consumer")
    private String destinationBucket;

    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;

    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;

    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeFolders = true;

    @UriParam
    private Storage storageClient;

    public String getBucketName() {
        return this.bucketName;
    }

    /**
     * Bucket name
     */
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * The Service account key that can be used as credentials for the Storage client.
     * 
     * @param serviceAccountKey
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getObjectName() {
        return objectName;
    }

    /**
     * The bjectName (the file insisde the bucket)
     */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    /**
     * The Cloud Storage location to use when creating the new buckets. The complete available locations list at
     * https://cloud.google.com/storage/docs/locations#location-mr
     */
    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    /**
     * Set strage client
     * 
     * @param storageClient
     */
    public void setStorageClient(Storage storageClient) {
        this.storageClient = storageClient;
    }

    public GoogleCloudStorageComponentOperations getOperation() {
        return operation;
    }

    /**
     * Set the operation for the producer
     * 
     * @param operation
     */
    public void setOperation(GoogleCloudStorageComponentOperations operation) {
        this.operation = operation;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Setting the autocreation of the bucket bucketName.
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    /**
     * Move objects from the origin bucket to a different bucket after they have been retrieved. To accomplish the
     * operation the destinationBucket option must be set. The copy bucket operation is only performed if the Exchange
     * is committed. If a rollback occurs, the object is not moved.
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

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete objects from the bucket after they have been retrieved. The delete is only performed if the Exchange is
     * committed. If a rollback occurs, the object is not deleted.
     * <p/>
     * If this option is false, then the same objects will be retrieve over and over again on the polls.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    /**
     * If it is true, the Object exchange will be consumed and put into the body and closed. If false the Object stream
     * will be put raw into the body and the headers will be set with the object metadata.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    public GoogleCloudStorageComponentConfiguration copy() {
        try {
            return (GoogleCloudStorageComponentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
