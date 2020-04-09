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
package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class BlobConfiguration implements Cloneable {

    @UriPath
    private String accountName;
    @UriPath
    private String containerName;
    @UriParam
    private StorageSharedKeyCredential credentials;
    @UriParam
    private BlobServiceClient serviceClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "producer", enums = "listBlobContainers,createBlobContainer,deleteBlobContainer,listBlobs,getBlob,deleteBlob,downloadBlobToFile,downloadLink,"
            + "uploadBlockBlob,stageBlockBlobList,commitBlobBlockList,getBlobBlockList,createAppendBlob,commitAppendBlob,createPageBlob,uploadPageBlob,resizePageBlob,"
            + "clearPageBlob,getPageBlobRanges", defaultValue = "listBlobContainers")
    private BlobOperationsDefinition operation = BlobOperationsDefinition.listBlobContainers;
    @UriParam(label = "common")
    private String blobName;
    @UriParam(label = "common", enums = "blockblob,appendblob,pageblob", defaultValue = "blockblob")
    private BlobType blobType = BlobType.blockblob;
    @UriParam(label = "common")
    private String fileDir;
    @UriParam(label = "common", defaultValue = "0")
    private long blobOffset;
    @UriParam(label = "common")
    private Long dataCount;
    @UriParam(label = "common", defaultValue = "0")
    private int maxRetryRequests;
    @UriParam(defaultValue = "true")
    private boolean closeStreamAfterRead = true;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean closeStreamAfterWrite = true;


    /**
     * Azure account name to be used for authentication with azure blob services
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * The blob container name
     */
    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    /**
     * StorageSharedKeyCredential can be injected to create the azure client, this holds the important authentication information
     */
    public StorageSharedKeyCredential getCredentials() {
        return credentials;
    }

    public void setCredentials(StorageSharedKeyCredential credentials) {
        this.credentials = credentials;
    }

    /**
     * Client to a storage account. This client does not hold any state about a particular storage account
     * but is instead a convenient way of sending off appropriate requests to the resource on the service.
     * It may also be used to construct URLs to blobs and containers.
     *
     * This client contains operations on a service account. Operations on a container are available on {@link BlobContainerClient}
     * through {@link #getBlobContainerClient(String)}, and operations on a blob are available on {@link BlobClient} through
     * {@link #getBlobContainerClient(String).getBlobClient(String)}.
     */
    public BlobServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(BlobServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    /**
     * Access key for the associated azure account name to be used for authentication with azure blob services
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * The blob operation that can be used with this component on the producer
     */
    public BlobOperationsDefinition getOperation() {
        return operation;
    }

    public void setOperation(BlobOperationsDefinition operation) {
        this.operation = operation;
    }

    /**
     * The blob name, required for consumer. However on producer, is only required for the operations on the blob level
     */
    public String getBlobName() {
        return blobName;
    }

    public void setBlobName(String blobName) {
        this.blobName = blobName;
    }

    /**
     * The blob type in order to initiate the appropriate settings for each blob type
     */
    public BlobType getBlobType() {
        return blobType;
    }

    public void setBlobType(BlobType blobType) {
        this.blobType = blobType;
    }

    /**
     * The file directory where the downloaded blobs will be saved to, this can be used in both, producer and consumer
     */
    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    /**
     * Set the blob offset for the upload or download operations, default is 0
     */
    public long getBlobOffset() {
        return blobOffset;
    }

    public void setBlobOffset(long blobOffset) {
        this.blobOffset = blobOffset;
    }

    /**
     * How many bytes to include in the range. Must be greater than or equal to 0 if specified.
     */
    public Long getDataCount() {
        return dataCount;
    }

    public void setDataCount(Long dataCount) {
        this.dataCount = dataCount;
    }

    /**
     * Specifies the maximum number of additional HTTP Get requests that will be made while reading the data from a response body.
     */
    public int getMaxRetryRequests() {
        return maxRetryRequests;
    }

    public void setMaxRetryRequests(int maxRetryRequests) {
        this.maxRetryRequests = maxRetryRequests;
    }

    /**
     * Close the stream after read or keep it open, default is true
     */
    public boolean isCloseStreamAfterRead() {
        return closeStreamAfterRead;
    }

    public void setCloseStreamAfterRead(boolean closeStreamAfterRead) {
        this.closeStreamAfterRead = closeStreamAfterRead;
    }

    /**
     * Close the stream after write or keep it open, default is true
     */
    public boolean isCloseStreamAfterWrite() {
        return closeStreamAfterWrite;
    }

    public void setCloseStreamAfterWrite(boolean closeStreamAfterWrite) {
        this.closeStreamAfterWrite = closeStreamAfterWrite;
    }

    // *************************************************
    //
    // *************************************************

    public BlobConfiguration copy() {
        try {
            return (BlobConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
