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
import com.google.cloud.storage.StorageClass;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudStorageConfiguration implements Cloneable {

    @UriPath(label = "common", description = "Bucket name or ARN")
    @Metadata(required = true)
    private String bucketName;

    @UriParam(label = "common",
              description = "The Service account key that can be used as credentials for the Storage client. It can be loaded by default from "
                            + " classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,createDownloadLink")
    private GoogleCloudStorageOperations operation;

    @UriParam(label = "producer", description = "The Object name inside the bucket")
    private String objectName;

    @UriParam(label = "common", defaultValue = "US-EAST1",
              description = "The Cloud Storage location to use when creating the new buckets")
    private String storageLocation = "US-EAST1";

    @UriParam(label = "common", defaultValue = "STANDARD",
              description = "The Cloud Storage class to use when creating the new buckets")
    private StorageClass storageClass = StorageClass.STANDARD;

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

    @UriParam(label = "consumer")
    private String downloadFileName;

    @UriParam
    @Metadata(autowired = true)
    private Storage storageClient;

    @UriParam(label = "consumer", description = "A regular expression to include only blobs with name matching it.")
    private String filter;

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
     * The Service account key that can be used as credentials for the Storage client. It can be loaded by default from
     * classpath, but you can prefix with "classpath:", "file:", or "http:" to load the resource from different systems.
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getObjectName() {
        return objectName;
    }

    /**
     * The ObjectName (the file insisde the bucket)
     */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
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

    public StorageClass getStorageClass() {
        return storageClass;
    }

    /**
     * The Cloud Storage class to use when creating the new buckets
     *
     * @param storageClass
     */
    public void setStorageClass(StorageClass storageClass) {
        this.storageClass = storageClass;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    /**
     * The storage client
     *
     * @param storageClient
     */
    public void setStorageClient(Storage storageClient) {
        this.storageClient = storageClient;
    }

    public GoogleCloudStorageOperations getOperation() {
        return operation;
    }

    /**
     * Set the operation for the producer
     *
     * @param operation
     */
    public void setOperation(GoogleCloudStorageOperations operation) {
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

    public String getDownloadFileName() {
        return downloadFileName;
    }

    /**
     * The folder or filename to use when downloading the blob. By default, this specifies the folder name, and the name
     * of the file is the blob name. For example, setting this to mydownload will be the same as setting
     * mydownload/${file:name}.
     *
     * You can use dynamic expressions for fine-grained control. For example, you can specify
     * ${date:now:yyyyMMdd}/${file:name} to store the blob in sub folders based on today's day.
     *
     * Only ${file:name} and ${file:name.noext} is supported as dynamic tokens for the blob name.
     */
    public void setDownloadFileName(String downloadFileName) {
        this.downloadFileName = downloadFileName;
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
     * If it is true, the Object exchange will be consumed and put into the body. If false the Object stream will be put
     * raw into the body and the headers will be set with the object metadata.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    public GoogleCloudStorageConfiguration copy() {
        try {
            return (GoogleCloudStorageConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A regular expression to include only blobs with name matching it.
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getFilter() {
        return filter;
    }

}
