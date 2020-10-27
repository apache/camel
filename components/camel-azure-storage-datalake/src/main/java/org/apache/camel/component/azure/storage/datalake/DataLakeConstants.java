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
package org.apache.camel.component.azure.storage.datalake;

public final class DataLakeConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageDataLake";

    public static final String LIST_FILESYSTEMS_OPTIONS = HEADER_PREFIX + "ListFileSystemsOptions";
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    public static final String DATALAKE_OPERATION = HEADER_PREFIX + "Operation";
    public static final String FILESYSTEM_NAME = HEADER_PREFIX + "FileSystemName";
    public static final String DIRECTORY_NAME = HEADER_PREFIX + "DirectoryName";
    public static final String FILE_NAME = HEADER_PREFIX + "FileName";
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    public static final String PUBLIC_ACCESS_TYPE = HEADER_PREFIX + "PublicAccessType";
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    public static final String DATALAKE_REQUEST_CONDITION = HEADER_PREFIX + "RequestCondition";
    public static final String LIST_PATH_OPTIONS = HEADER_PREFIX + "ListPathOptions";
    public static final String PATH = HEADER_PREFIX + "Path";
    public static final String RECURSIVE = HEADER_PREFIX + "Recursive";
    public static final String MAX_RESULTS = HEADER_PREFIX + "MaxResults";
    public static final String USER_PRINCIPAL_NAME_RETURNED = HEADER_PREFIX + "UserPrincipalNameReturned";
    public static final String REGEX = HEADER_PREFIX + "Regex";
    public static final String FILE_DIR = HEADER_PREFIX + "FileDir";
    public static final String ACCESS_TIER = HEADER_PREFIX + "AccessTier";
    public static final String CONTENT_MD5 = HEADER_PREFIX + "ContentMD5";
    public static final String FILE_RANGE = HEADER_PREFIX + "FileRange";
    public static final String PARALLEL_TRANSFER_OPTIONS = HEADER_PREFIX + "ParallelTransferOptions";
    public static final String OPEN_OPTIONS = HEADER_PREFIX + "OpenOptions";
    public static final String ACCESS_TIER_CHANGE_TIME = HEADER_PREFIX + "AccessTierChangeTime";
    public static final String ARCHIVE_STATUS = HEADER_PREFIX + "ArchiveStatus";
    public static final String CACHE_CONTROL = HEADER_PREFIX + "CacheControl";
    public static final String CONTENT_DISPOSITION = HEADER_PREFIX + "ContentDisposition";
    public static final String CONTENT_ENCODING = HEADER_PREFIX + "ContentEncoding";
    public static final String CONTENT_LANGUAGE = HEADER_PREFIX + "ContentLanguage";
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    public static final String COPY_COMPLETION_TIME = HEADER_PREFIX + "CopyCompletionTime";
    public static final String COPY_ID = HEADER_PREFIX + "CopyId";
    public static final String COPY_PROGRESS = HEADER_PREFIX + "CopyProgress";
    public static final String COPY_SOURCE = HEADER_PREFIX + "CopySource";
    public static final String COPY_STATUS = HEADER_PREFIX + "CopyStatus";
    public static final String COPY_STATUS_DESCRIPTION = HEADER_PREFIX + "CopyStatusDescription";
    public static final String CREATION_TIME = HEADER_PREFIX + "CreationTime";
    public static final String ENCRYPTION_KEY_SHA_256 = HEADER_PREFIX + "EncryptionKeySha256";
    public static final String E_TAG = HEADER_PREFIX + "ETag";
    public static final String FILE_SIZE = HEADER_PREFIX + "FileSize";
    public static final String LAST_MODIFIED = HEADER_PREFIX + "LastModified";
    public static final String LEASE_DURATION = HEADER_PREFIX + "LeaseDuration";
    public static final String LEASE_STATE = HEADER_PREFIX + "LeaseState";
    public static final String LEASE_STATUS = HEADER_PREFIX + "LeaseStatus";
    public static final String INCREMENTAL_COPY = HEADER_PREFIX + "IncrementalCopy";
    public static final String SERVER_ENCRYPTED = HEADER_PREFIX + "ServerEncrypted";
    public static final String DOWNLOAD_LINK_EXPIRATION = HEADER_PREFIX + "DownloadLinkExpiration";
    public static final String DOWNLOAD_LINK = HEADER_PREFIX + "DownloadLink";
    public static final String FILE_OFFSET = HEADER_PREFIX + "FileOffset";
    public static final String LEASE_ID = HEADER_PREFIX + "LeaseId";
    public static final String PATH_HTTP_HEADERS = HEADER_PREFIX + "PathHttpHeaders";
    public static final String RETAIN_UNCOMMITED_DATA = HEADER_PREFIX + "RetainCommitedData";
    public static final String CLOSE = HEADER_PREFIX + "Close";
    public static final String POSITION = HEADER_PREFIX + "Position";
    public static final String EXPRESSION = HEADER_PREFIX + "Expression";
    public static final String INPUT_SERIALIZATION = HEADER_PREFIX + "InputSerialization";
    public static final String OUTPUT_SERIALIZATION = HEADER_PREFIX + "OutputSerialization";
    public static final String ERROR_CONSUMER = HEADER_PREFIX + "ErrorConsumer";
    public static final String PROGRESS_CONSUMER = HEADER_PREFIX + "ProgressConsumer";
    public static final String QUERY_OPTIONS = HEADER_PREFIX + "QueryOptions";
    public static final String PERMISSION = HEADER_PREFIX + "Permission";
    public static final String UMASK = HEADER_PREFIX + "Umask";

    private DataLakeConstants() {
    }
}
