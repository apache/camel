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

import org.apache.camel.spi.Metadata;

public final class BlobConstants {
    // constants
    public static final Long PAGE_BLOB_DEFAULT_SIZE = 512L;
    private static final String HEADER_PREFIX = "CamelAzureStorageBlob";
    // header names
    @Metadata(label = "producer",
              description = "(All) Specify the producer operation to execute, please see the doc on this page related to producer operation.",
              javaType = "org.apache.camel.component.azure.storage.blob.BlobOperationsDefinition")
    public static final String BLOB_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(label = "producer",
              description = "(uploadBlockBlob, commitBlobBlockList, createAppendBlob, createPageBlob) Additional parameters for a set of operations.",
              javaType = "BlobHttpHeaders")
    public static final String BLOB_HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";
    @Metadata(label = "consumer", description = "The E Tag of the blob", javaType = "String")
    public static final String E_TAG = HEADER_PREFIX + "ETag";
    @Metadata(label = "consumer", description = "Creation time of the blob.", javaType = "OffsetDateTime")
    public static final String CREATION_TIME = HEADER_PREFIX + "CreationTime";
    @Metadata(label = "consumer", description = "Datetime when the blob was last modified.", javaType = "OffsetDateTime")
    public static final String LAST_MODIFIED = HEADER_PREFIX + "LastModified";
    @Metadata(label = "consumer", description = "Content type specified for the blob.", javaType = "String")
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    @Metadata(description = "(producer) (Most operations related to upload blob) Most operations related to upload blob|An MD5 hash of the block content. "
                            +
                            "This hash is used to verify the integrity of the block during transport. " +
                            "When this header is specified, the storage service compares the hash of the content that has arrived with this header value. "
                            +
                            "Note that this MD5 hash is not stored with the blob. If the two hashes do not match, the operation will fail.\n"
                            +
                            "(consumer) Content MD5 specified for the blob.",
              javaType = "byte[]")
    public static final String CONTENT_MD5 = HEADER_PREFIX + "ContentMD5";
    @Metadata(label = "consumer", description = "Content encoding specified for the blob.", javaType = "String")
    public static final String CONTENT_ENCODING = HEADER_PREFIX + "ContentEncoding";
    @Metadata(label = "consumer", description = "Content disposition specified for the blob.", javaType = "String")
    public static final String CONTENT_DISPOSITION = HEADER_PREFIX + "ContentDisposition";
    @Metadata(label = "consumer", description = "Content language specified for the blob.", javaType = "String")
    public static final String CONTENT_LANGUAGE = HEADER_PREFIX + "ContentLanguage";
    @Metadata(label = "consumer", description = "Cache control specified for the blob.", javaType = "String")
    public static final String CACHE_CONTROL = HEADER_PREFIX + "CacheControl";
    @Metadata(label = "consumer", description = "The size of the blob.",
              javaType = "long")
    public static final String BLOB_SIZE = HEADER_PREFIX + "BlobSize";
    @Metadata(label = "producer",
              description = "When uploading a blob with the uploadBlockBlob-operation this can be used to tell the client what the length of an InputStream is.",
              javaType = "long")
    public static final String BLOB_UPLOAD_SIZE = HEADER_PREFIX + "BlobUploadSize";
    @Metadata(description = "(producer) (createPageBlob) A user-controlled value that you can use to track requests. " +
                            "The value of the sequence number must be between 0 and 2^63 - 1. The default value is 0.\n" +
                            "(consumer) The current sequence number for a page blob.",
              javaType = "Long")
    public static final String BLOB_SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
    @Metadata(label = "consumer", description = "The type of the blob.",
              javaType = "org.apache.camel.component.azure.storage.blob.BlobType")
    public static final String BLOB_TYPE = HEADER_PREFIX + "BlobType";
    @Metadata(label = "consumer", description = "Status of the lease on the blob.",
              javaType = "com.azure.storage.blob.models.LeaseStatusType")
    public static final String LEASE_STATUS = HEADER_PREFIX + "LeaseStatus";
    @Metadata(label = "consumer", description = "State of the lease on the blob.",
              javaType = "com.azure.storage.blob.models.LeaseStateType")
    public static final String LEASE_STATE = HEADER_PREFIX + "LeaseState";
    @Metadata(label = "consumer", description = "Type of lease on the blob.",
              javaType = "com.azure.storage.blob.models.LeaseDurationType")
    public static final String LEASE_DURATION = HEADER_PREFIX + "LeaseDuration";
    @Metadata(label = "consumer", description = "Identifier of the last copy operation performed on the blob.",
              javaType = "String")
    public static final String COPY_ID = HEADER_PREFIX + "CopyId";
    @Metadata(label = "consumer", description = "Status of the last copy operation performed on the blob.",
              javaType = "com.azure.storage.blob.models.CopyStatusType")
    public static final String COPY_STATUS = HEADER_PREFIX + "CopyStatus";
    @Metadata(label = "consumer", description = "Source of the last copy operation performed on the blob.", javaType = "String")
    public static final String COPY_SOURCE = HEADER_PREFIX + "CopySource";
    @Metadata(label = "consumer", description = "Progress of the last copy operation performed on the blob.",
              javaType = "String")
    public static final String COPY_PROGRESS = HEADER_PREFIX + "CopyProgress";
    @Metadata(label = "consumer", description = "Datetime when the last copy operation on the blob completed.",
              javaType = "OffsetDateTime")
    public static final String COPY_COMPILATION_TIME = HEADER_PREFIX + "CopyCompletionTime";
    @Metadata(label = "consumer", description = "Description of the last copy operation on the blob.", javaType = "String")
    public static final String COPY_STATUS_DESCRIPTION = HEADER_PREFIX + "CopyStatusDescription";
    @Metadata(label = "consumer", description = "Snapshot identifier of the last incremental copy snapshot for the blob.",
              javaType = "String")
    public static final String COPY_DESTINATION_SNAPSHOT = HEADER_PREFIX + "CopyDestinationSnapshot";
    @Metadata(label = "consumer", description = "Flag indicating if the blob's content is encrypted on the server.",
              javaType = "boolean")
    public static final String IS_SERVER_ENCRYPTED = HEADER_PREFIX + "IsServerEncrypted";
    @Metadata(label = "consumer", description = "Flag indicating if the blob was incrementally copied.", javaType = "boolean")
    public static final String IS_INCREMENTAL_COPY = HEADER_PREFIX + "IsIncrementalCopy";
    @Metadata(description = "(producer) (uploadBlockBlob, commitBlobBlockList) Defines values for AccessTier.\n"
                            + "(consumer) Access tier of the blob.",
              javaType = "AccessTier")
    public static final String ACCESS_TIER = HEADER_PREFIX + "AccessTier";
    @Metadata(label = "consumer",
              description = "Flag indicating if the access tier of the blob was inferred from properties of the blob.",
              javaType = "boolean")
    public static final String IS_ACCESS_TIER_INFRRRED = HEADER_PREFIX + "IsAccessTierInferred";
    @Metadata(label = "consumer", description = "Archive status of the blob.", javaType = "ArchiveStatus")
    public static final String ARCHIVE_STATUS = HEADER_PREFIX + "ArchiveStatus";
    public static final String ENCRYPTION_KEY_SHA_256 = HEADER_PREFIX + "EncryptionKeySha256";
    public static final String ENCRYPTION_SCOPE = HEADER_PREFIX + "EncryptionScope";
    @Metadata(label = "consumer", description = "Datetime when the access tier of the blob last changed.",
              javaType = "OffsetDateTime")
    public static final String ACCESS_TIER_CHANGE_TIME = HEADER_PREFIX + "accessTierChangeTime";
    @Metadata(description = "(producer) (Operations related to container and blob) Operations related to container and blob| Metadata to associate with the container or blob.\n"
                            + "(consumer) Additional metadata associated with the blob.",
              javaType = "Map<String,String>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(label = "consumer", description = "Number of blocks committed to an append blob", javaType = "Integer")
    public static final String COMMITTED_BLOCK_COUNT = HEADER_PREFIX + "CommittedBlockCount";
    @Metadata(label = "consumer", description = "The offset at which the block was committed to the block blob.",
              javaType = "String")
    public static final String APPEND_OFFSET = HEADER_PREFIX + "AppendOffset";
    @Metadata(label = "consumer", description = "Returns non-parsed httpHeaders that can be used by the user.",
              javaType = "HttpHeaders")
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    @Metadata(label = "consumer", description = "The downloaded filename from the operation `downloadBlobToFile`.",
              javaType = "String")
    public static final String FILE_NAME = HEADER_PREFIX + "FileName";
    @Metadata(label = "consumer", description = "The download link generated by `downloadLink` operation.", javaType = "String")
    public static final String DOWNLOAD_LINK = HEADER_PREFIX + "DownloadLink";
    // headers to be retrieved
    @Metadata(label = "producer",
              description = "(listBlobs) Defines options available to configure the behavior of a call to listBlobsFlatSegment on a `BlobContainerClient` object.",
              javaType = "ListBlobsOptions")
    public static final String LIST_BLOB_OPTIONS = HEADER_PREFIX + "ListBlobOptions";
    @Metadata(label = "producer", description = "(listBlobs) The details for listing specific blobs",
              javaType = "BlobListDetails")
    public static final String BLOB_LIST_DETAILS = HEADER_PREFIX + "ListDetails";
    @Metadata(label = "producer",
              description = "(listBlobs,getBlob) Filters the results to return only blobs whose names begin with the specified prefix. May be null to return all blobs.",
              javaType = "String")
    public static final String PREFIX = HEADER_PREFIX + "Prefix";
    @Metadata(label = "producer",
              description = "(listBlobs,getBlob) Filters the results to return only blobs whose names match the specified regular expression. "
                            +
                            "May be null to return all. If both prefix and regex are set, regex takes the priority and prefix is ignored.",
              javaType = "String")
    public static final String REGEX = HEADER_PREFIX + "Regex";
    @Metadata(label = "producer",
              description = "(listBlobs) Specifies the maximum number of blobs to return, including all BlobPrefix elements. " +
                            "If the request does not specify maxResultsPerPage or specifies a value greater than 5,000, the server will return up to 5,000 items.",
              javaType = "Integer")
    public static final String MAX_RESULTS_PER_PAGE = HEADER_PREFIX + "MaxResultsPerPage";
    @Metadata(label = "producer",
              description = "(All) An optional timeout value beyond which a `RuntimeException` will be raised.",
              javaType = "Duration")
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    @Metadata(label = "producer",
              description = "(createContainer) Specifies how the data in this container is available to the public. Pass `null` for no public access.",
              javaType = "PublicAccessType")
    public static final String PUBLIC_ACCESS_TYPE = HEADER_PREFIX + "PublicAccessType";
    @Metadata(label = "producer",
              description = "(Operations related to container and blob) This contains values which will restrict the successful operation of a variety of requests to the conditions present. "
                            +
                            "These conditions are entirely optional.",
              javaType = "BlobRequestConditions")
    public static final String BLOB_REQUEST_CONDITION = HEADER_PREFIX + "RequestCondition";
    @Metadata(label = "producer",
              description = "(Operations related to container and blob) Override/set the container name on the exchange headers.",
              javaType = "String")
    public static final String BLOB_CONTAINER_NAME = HEADER_PREFIX + "BlobContainerName";
    @Metadata(label = "producer",
              description = "(Operations related to blob) Override/set the blob name on the exchange headers.",
              javaType = "String")
    public static final String BLOB_NAME = HEADER_PREFIX + "BlobName";
    @Metadata(label = "producer",
              description = "(downloadBlobToFile) The file directory where the downloaded blobs will be saved to.",
              javaType = "String")
    public static final String FILE_DIR = HEADER_PREFIX + "FileDir";
    @Metadata(label = "producer",
              description = "(Operations related to page blob) A `PageRange` object. Given that pages must be aligned with 512-byte boundaries, "
                            +
                            "the start offset must be a modulus of 512 and the end offset must be a modulus of 512 - 1." +
                            " Examples of valid byte ranges are 0-511, 512-1023, etc.",
              javaType = "PageRange")
    public static final String PAGE_BLOB_RANGE = HEADER_PREFIX + "PageBlobRange";
    @Metadata(label = "producer",
              description = "(createPageBlob, resizePageBlob) Specifies the maximum size for the page blob, up to 8 TB. The page blob size must be aligned to a 512-byte boundary.",
              javaType = "Long")
    public static final String PAGE_BLOB_SIZE = HEADER_PREFIX + "PageBlobSize";
    @Metadata(label = "producer",
              description = "(stageBlockBlobList) When is set to `true`, the staged blocks will not be committed directly.",
              javaType = "boolean")
    public static final String COMMIT_BLOCK_LIST_LATER = HEADER_PREFIX + "CommitBlobBlockListLater";
    @Metadata(label = "producer", description = "(getBlobBlockList) Specifies which type of blocks to return.",
              javaType = "com.azure.storage.blob.models.BlockListType")
    public static final String BLOCK_LIST_TYPE = HEADER_PREFIX + "BlockListType";
    @Metadata(label = "producer",
              description = "(commitAppendBlob) When is set to `true`, the append blocks will be created when committing append blocks.",
              javaType = "boolean")
    public static final String CREATE_APPEND_BLOB = HEADER_PREFIX + "CreateAppendBlob";
    @Metadata(label = "producer",
              description = "(uploadPageBlob) When is set to `true`, the page blob will be created when uploading page blob.",
              javaType = "boolean")
    public static final String CREATE_PAGE_BLOB = HEADER_PREFIX + "CreatePageBlob";
    @Metadata(label = "producer",
              description = "(deleteBlob) Specifies the behavior for deleting the snapshots on this blob. `Include` will delete the base blob and all snapshots. "
                            +
                            "`Only` will delete only the snapshots. If a snapshot is being deleted, you must pass null.",
              javaType = "com.azure.storage.blob.models.DeleteSnapshotsOptionType")
    public static final String DELETE_SNAPSHOT_OPTION_TYPE = HEADER_PREFIX + "DeleteSnapshotsOptionType";
    @Metadata(label = "producer",
              description = "(listBlobContainers) A `ListBlobContainersOptions` which specifies what data should be returned by the service.",
              javaType = "ListBlobContainersOptions")
    public static final String LIST_BLOB_CONTAINERS_OPTIONS = HEADER_PREFIX + "ListBlobContainersOptions";
    @Metadata(label = "producer",
              description = "(downloadBlobToFile) `ParallelTransferOptions` to use to download to file. Number of parallel transfers parameter is ignored.",
              javaType = "ParallelTransferOptions")
    public static final String PARALLEL_TRANSFER_OPTIONS = HEADER_PREFIX + "ParallelTransferOptions";
    @Metadata(label = "producer", description = "(downloadLink) Override the default expiration (millis) of URL download link.",
              javaType = "Long")
    public static final String DOWNLOAD_LINK_EXPIRATION = HEADER_PREFIX + "DownloadLinkExpiration";
    @Metadata(label = "producer",
              description = "(copyBlob) The source blob account name to be used as source account name in a copy blob operation",
              javaType = "String")
    public static final String SOURCE_BLOB_ACCOUNT_NAME = HEADER_PREFIX + "SourceBlobAccountName";
    @Metadata(label = "producer",
              description = "(copyBlob) The source blob container name to be used as source container name in a copy blob operation",
              javaType = "String")
    public static final String SOURCE_BLOB_CONTAINER_NAME = HEADER_PREFIX + "SourceBlobContainerName";
    public static final String DESTINATION_BLOB_NAME = HEADER_PREFIX + "DestinationBlobContainerName";
    // change feed
    @Metadata(label = "producer",
              description = "(getChangeFeed) It filters the results to return events approximately after the start time. " +
                            "Note: A few events belonging to the previous hour can also be returned. " +
                            "A few events belonging to this hour can be missing; to ensure all events from the hour are returned, round the start time down by an hour.",
              javaType = "OffsetDateTime")
    public static final String CHANGE_FEED_START_TIME = HEADER_PREFIX + "ChangeFeedStartTime";
    @Metadata(label = "producer",
              description = "(getChangeFeed) It filters  the results to return events approximately before the end time. " +
                            "Note: A few events belonging to the next hour can also be returned. " +
                            "A few events belonging to this hour can be missing; to ensure all events from the hour are returned, round the end time up by an hour.",
              javaType = "OffsetDateTime")
    public static final String CHANGE_FEED_END_TIME = HEADER_PREFIX + "ChangeFeedEndTime";
    @Metadata(label = "producer",
              description = "(getChangeFeed) This gives additional context that is passed through the Http pipeline during the service call.",
              javaType = "Context")
    public static final String CHANGE_FEED_CONTEXT = HEADER_PREFIX + "Context";

    private BlobConstants() {
    }
}
