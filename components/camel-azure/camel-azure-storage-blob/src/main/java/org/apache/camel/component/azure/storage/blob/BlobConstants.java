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

public final class BlobConstants {
    // constants
    public static final Long PAGE_BLOB_DEFAULT_SIZE = 512L;
    private static final String HEADER_PREFIX = "CamelAzureStorageBlob";
    // header names
    public static final String BLOB_OPERATION = HEADER_PREFIX + "Operation";
    public static final String BLOB_HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";
    public static final String E_TAG = HEADER_PREFIX + "ETag";
    public static final String CREATION_TIME = HEADER_PREFIX + "CreationTime";
    public static final String LAST_MODIFIED = HEADER_PREFIX + "LastModified";
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    public static final String CONTENT_MD5 = HEADER_PREFIX + "ContentMD5";
    public static final String CONTENT_ENCODING = HEADER_PREFIX + "ContentEncoding";
    public static final String CONTENT_DISPOSITION = HEADER_PREFIX + "ContentDisposition";
    public static final String CONTENT_LANGUAGE = HEADER_PREFIX + "ContentLanguage";
    public static final String CACHE_CONTROL = HEADER_PREFIX + "CacheControl";
    public static final String BLOB_SIZE = HEADER_PREFIX + "BlobSize";
    public static final String BLOB_SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
    public static final String BLOB_TYPE = HEADER_PREFIX + "BlobType";
    public static final String LEASE_STATUS = HEADER_PREFIX + "LeaseStatus";
    public static final String LEASE_STATE = HEADER_PREFIX + "LeaseState";
    public static final String LEASE_DURATION = HEADER_PREFIX + "LeaseDuration";
    public static final String COPY_ID = HEADER_PREFIX + "CopyId";
    public static final String COPY_STATUS = HEADER_PREFIX + "CopyStatus";
    public static final String COPY_SOURCE = HEADER_PREFIX + "CopySource";
    public static final String COPY_PROGRESS = HEADER_PREFIX + "CopyProgress";
    public static final String COPY_COMPILATION_TIME = HEADER_PREFIX + "CopyCompletionTime";
    public static final String COPY_STATUS_DESCRIPTION = HEADER_PREFIX + "CopyStatusDescription";
    public static final String COPY_DESTINATION_SNAPSHOT = HEADER_PREFIX + "CopyDestinationSnapshot";
    public static final String IS_SERVER_ENCRYPTED = HEADER_PREFIX + "IsServerEncrypted";
    public static final String IS_INCREMENTAL_COPY = HEADER_PREFIX + "IsIncrementalCopy";
    public static final String ACCESS_TIER = HEADER_PREFIX + "AccessTier";
    public static final String IS_ACCESS_TIER_INFRRRED = HEADER_PREFIX + "IsAccessTierInferred";
    public static final String ARCHIVE_STATUS = HEADER_PREFIX + "ArchiveStatus";
    public static final String ENCRYPTION_KEY_SHA_256 = HEADER_PREFIX + "EncryptionKeySha256";
    public static final String ENCRYPTION_SCOPE = HEADER_PREFIX + "EncryptionScope";
    public static final String ACCESS_TIER_CHANGE_TIME = HEADER_PREFIX + "accessTierChangeTime";
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    public static final String COMMITTED_BLOCK_COUNT = HEADER_PREFIX + "CommittedBlockCount";
    public static final String APPEND_OFFSET = HEADER_PREFIX + "AppendOffset";
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    public static final String FILE_NAME = HEADER_PREFIX + "FileName";
    public static final String DOWNLOAD_LINK = HEADER_PREFIX + "DownloadLink";
    // headers to be retrieved
    public static final String LIST_BLOB_OPTIONS = HEADER_PREFIX + "ListBlobOptions";
    public static final String BLOB_LIST_DETAILS = HEADER_PREFIX + "ListDetails";
    public static final String PREFIX = HEADER_PREFIX + "Prefix";
    public static final String REGEX = HEADER_PREFIX + "Regex";
    public static final String MAX_RESULTS_PER_PAGE = HEADER_PREFIX + "MaxResultsPerPage";
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    public static final String PUBLIC_ACCESS_TYPE = HEADER_PREFIX + "PublicAccessType";
    public static final String BLOB_REQUEST_CONDITION = HEADER_PREFIX + "RequestCondition";
    public static final String BLOB_CONTAINER_NAME = HEADER_PREFIX + "BlobContainerName";
    public static final String BLOB_NAME = HEADER_PREFIX + "BlobName";
    public static final String FILE_DIR = HEADER_PREFIX + "FileDir";
    public static final String PAGE_BLOB_RANGE = HEADER_PREFIX + "PageBlobRange";
    public static final String PAGE_BLOB_SIZE = HEADER_PREFIX + "PageBlobSize";
    public static final String COMMIT_BLOCK_LIST_LATER = HEADER_PREFIX + "CommitBlobBlockListLater";
    public static final String BLOCK_LIST_TYPE = HEADER_PREFIX + "BlockListType";
    public static final String CREATE_APPEND_BLOB = HEADER_PREFIX + "CreateAppendBlob";
    public static final String CREATE_PAGE_BLOB = HEADER_PREFIX + "CreatePageBlob";
    public static final String DELETE_SNAPSHOT_OPTION_TYPE = HEADER_PREFIX + "DeleteSnapshotsOptionType";
    public static final String LIST_BLOB_CONTAINERS_OPTIONS = HEADER_PREFIX + "ListBlobContainersOptions";
    public static final String PARALLEL_TRANSFER_OPTIONS = HEADER_PREFIX + "ParallelTransferOptions";
    public static final String DOWNLOAD_LINK_EXPIRATION = HEADER_PREFIX + "DownloadLinkExpiration";

    private BlobConstants() {
    }
}
