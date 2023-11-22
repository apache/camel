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

import java.time.Duration;
import java.time.OffsetDateTime;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.storage.blob.CredentialType.AZURE_IDENTITY;

@UriParams
public class BlobConfiguration implements Cloneable {

    @UriPath
    private String accountName;
    @UriPath
    private String containerName;
    @UriParam
    private StorageSharedKeyCredential credentials;
    @UriParam
    private String sasToken;
    @UriParam
    @Metadata(autowired = true)
    private BlobServiceClient serviceClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "producer",
              enums = "listBlobContainers,createBlobContainer,deleteBlobContainer,listBlobs,getBlob,deleteBlob,downloadBlobToFile,downloadLink,"
                      + "uploadBlockBlob,stageBlockBlobList,commitBlobBlockList,getBlobBlockList,createAppendBlob,commitAppendBlob,createPageBlob,uploadPageBlob,resizePageBlob,"
                      + "clearPageBlob,getPageBlobRanges",
              defaultValue = "listBlobContainers")
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
    @UriParam(label = "common")
    private Duration timeout;
    @UriParam(label = "common")
    private String prefix;
    @UriParam(label = "common")
    private Integer maxResultsPerPage;
    @UriParam(label = "common", defaultValue = "0")
    private int maxRetryRequests;
    @UriParam(defaultValue = "true")
    private boolean closeStreamAfterRead = true;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean closeStreamAfterWrite = true;
    @UriParam(label = "producer")
    private Long downloadLinkExpiration;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean commitBlockListLater = true;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean createAppendBlob = true;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean createPageBlob = true;
    @UriParam(label = "producer", defaultValue = "0")
    private Long blobSequenceNumber;
    @UriParam(label = "producer", defaultValue = "512")
    private Long pageBlobSize = BlobConstants.PAGE_BLOB_DEFAULT_SIZE;
    @UriParam(label = "producer", defaultValue = "COMMITTED")
    private BlockListType blockListType = BlockListType.COMMITTED;
    @UriParam(label = "producer")
    private OffsetDateTime changeFeedStartTime;
    @UriParam(label = "producer")
    private OffsetDateTime changeFeedEndTime;
    @UriParam(label = "producer")
    private Context changeFeedContext;
    @UriParam(label = "common")
    private String regex;
    @UriParam(label = "security", secret = true)
    private String sourceBlobAccessKey;
    @UriParam(label = "common", enums = "SHARED_ACCOUNT_KEY,SHARED_KEY_CREDENTIAL,AZURE_IDENTITY,AZURE_SAS",
              defaultValue = "AZURE_IDENTITY")
    private CredentialType credentialType = AZURE_IDENTITY;

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
     * StorageSharedKeyCredential can be injected to create the azure client, this holds the important authentication
     * information
     */
    public StorageSharedKeyCredential getCredentials() {
        return credentials;
    }

    public void setCredentials(StorageSharedKeyCredential credentials) {
        this.credentials = credentials;
    }

    /**
     * Client to a storage account. This client does not hold any state about a particular storage account but is
     * instead a convenient way of sending off appropriate requests to the resource on the service. It may also be used
     * to construct URLs to blobs and containers.
     * <p>
     * This client contains operations on a service account. Operations on a container are available on
     * {@link BlobContainerClient} through {@link BlobServiceClient#getBlobContainerClient(String)}, and operations on a
     * blob are available on {@link BlobClient} through {@link BlobContainerClient#getBlobClient(String)}.
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
     * The blob name, to consume specific blob from a container. However, on producer it is only required for the
     * operations on the blob level
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
     * Specifies the maximum number of additional HTTP Get requests that will be made while reading the data from a
     * response body.
     */
    public int getMaxRetryRequests() {
        return maxRetryRequests;
    }

    public void setMaxRetryRequests(int maxRetryRequests) {
        this.maxRetryRequests = maxRetryRequests;
    }

    /**
     * An optional timeout value beyond which a {@link RuntimeException} will be raised.
     */
    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Filters the results to return only blobs whose names begin with the specified prefix. May be null to return all
     * blobs.
     */
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Specifies the maximum number of blobs to return, including all BlobPrefix elements. If the request does not
     * specify maxResultsPerPage or specifies a value greater than 5,000, the server will return up to 5,000 items.
     */
    public Integer getMaxResultsPerPage() {
        return maxResultsPerPage;
    }

    public void setMaxResultsPerPage(Integer maxResultsPerPage) {
        this.maxResultsPerPage = maxResultsPerPage;
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

    /**
     * Specifies the maximum size for the page blob, up to 8 TB. The page blob size must be aligned to a 512-byte
     * boundary.
     */
    public Long getPageBlobSize() {
        return pageBlobSize;
    }

    public void setPageBlobSize(Long pageBlobSize) {
        this.pageBlobSize = pageBlobSize;
    }

    /**
     * Override the default expiration (millis) of URL download link.
     */
    public Long getDownloadLinkExpiration() {
        return downloadLinkExpiration;
    }

    public void setDownloadLinkExpiration(Long downloadLinkExpiration) {
        this.downloadLinkExpiration = downloadLinkExpiration;
    }

    /**
     * When is set to `true`, the staged blocks will not be committed directly.
     */
    public boolean isCommitBlockListLater() {
        return commitBlockListLater;
    }

    public void setCommitBlockListLater(boolean commitBlockListLater) {
        this.commitBlockListLater = commitBlockListLater;
    }

    /**
     * When is set to `true`, the append blocks will be created when committing append blocks.
     */
    public boolean isCreateAppendBlob() {
        return createAppendBlob;
    }

    public void setCreateAppendBlob(boolean createAppendBlob) {
        this.createAppendBlob = createAppendBlob;
    }

    /**
     * When is set to `true`, the page blob will be created when uploading page blob.
     */
    public boolean isCreatePageBlob() {
        return createPageBlob;
    }

    public void setCreatePageBlob(boolean createPageBlob) {
        this.createPageBlob = createPageBlob;
    }

    /**
     * A user-controlled value that you can use to track requests. The value of the sequence number must be between 0
     * and 2^63 - 1.The default value is 0.
     */
    public Long getBlobSequenceNumber() {
        return blobSequenceNumber;
    }

    public void setBlobSequenceNumber(Long blobSequenceNumber) {
        this.blobSequenceNumber = blobSequenceNumber;
    }

    /**
     * Specifies which type of blocks to return.
     */
    public BlockListType getBlockListType() {
        return blockListType;
    }

    public void setBlockListType(BlockListType blockListType) {
        this.blockListType = blockListType;
    }

    /**
     * When using `getChangeFeed` producer operation, this filters the results to return events approximately after the
     * start time. Note: A few events belonging to the previous hour can also be returned. A few events belonging to
     * this hour can be missing; to ensure all events from the hour are returned, round the start time down by an hour.
     */
    public OffsetDateTime getChangeFeedStartTime() {
        return changeFeedStartTime;
    }

    public void setChangeFeedStartTime(OffsetDateTime changeFeedStartTime) {
        this.changeFeedStartTime = changeFeedStartTime;
    }

    /**
     * When using `getChangeFeed` producer operation, this filters the results to return events approximately before the
     * end time. Note: A few events belonging to the next hour can also be returned. A few events belonging to this hour
     * can be missing; to ensure all events from the hour are returned, round the end time up by an hour.
     */
    public OffsetDateTime getChangeFeedEndTime() {
        return changeFeedEndTime;
    }

    public void setChangeFeedEndTime(OffsetDateTime changeFeedEndTime) {
        this.changeFeedEndTime = changeFeedEndTime;
    }

    /**
     * When using `getChangeFeed` producer operation, this gives additional context that is passed through the Http
     * pipeline during the service call.
     */
    public Context getChangeFeedContext() {
        return changeFeedContext;
    }

    public void setChangeFeedContext(Context changeFeedContext) {
        this.changeFeedContext = changeFeedContext;
    }

    /**
     * Filters the results to return only blobs whose names match the specified regular expression. May be null to
     * return all if both prefix and regex are set, regex takes the priority and prefix is ignored.
     */
    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getSourceBlobAccessKey() {
        return sourceBlobAccessKey;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    /**
     * Determines the credential strategy to adopt
     */
    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    /**
     * Source Blob Access Key: for copyblob operation, sadly, we need to have an accessKey for the source blob we want
     * to copy Passing an accessKey as header, it's unsafe so we could set as key.
     */
    public void setSourceBlobAccessKey(String sourceBlobAccessKey) {
        this.sourceBlobAccessKey = sourceBlobAccessKey;
    }

    public String getSasToken() {
        return sasToken;
    }

    /**
     * In case of usage of Shared Access Signature we'll need to set a SAS Token
     */
    public void setSasToken(String sasToken) {
        this.sasToken = sasToken;
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
