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

import org.apache.camel.spi.Metadata;

public final class DataLakeConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageDataLake";

    @Metadata(label = "producer",
              description = "Defines options available to configure the behavior of a call to listFileSystemsSegment on a DataLakeServiceAsyncClient object. Null may be passed.",
              javaType = "ListFileSystemsOptions")
    public static final String LIST_FILESYSTEMS_OPTIONS = HEADER_PREFIX + "ListFileSystemsOptions";
    @Metadata(label = "producer", description = "An optional timeout value beyond which a RuntimeException will be raised.",
              javaType = "Duration")
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    @Metadata(label = "producer",
              description = "Specify the producer operation to execute. Different operations allowed are shown below.",
              javaType = "org.apache.camel.component.azure.storage.datalake.DataLakeOperationsDefinition")
    public static final String DATALAKE_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(label = "producer",
              description = "Name of the file system in azure data lake on which operation is to be performed. Please make sure that filesystem name is all lowercase.",
              javaType = "String")
    public static final String FILESYSTEM_NAME = HEADER_PREFIX + "FileSystemName";
    @Metadata(label = "producer",
              description = "Name of the directory in azure data lake on which operation is to be performed.",
              javaType = "String")
    public static final String DIRECTORY_NAME = HEADER_PREFIX + "DirectoryName";
    @Metadata(label = "producer", description = "Name of the file in azure data lake on which operation is to be performed.",
              javaType = "String")
    public static final String FILE_NAME = HEADER_PREFIX + "FileName";
    @Metadata(label = "from both", description = "The metadata to associate with the file.",
              javaType = "Map<String, String>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(label = "producer",
              description = "Defines options available to configure the behavior of a call to listFileSystemsSegment on a DataLakeServiceAsyncClient object.",
              javaType = "PublicAccessType")
    public static final String PUBLIC_ACCESS_TYPE = HEADER_PREFIX + "PublicAccessType";
    @Metadata(label = "consumer", description = "Non parsed http headers that can be used by the user.",
              javaType = "HttpHeaders")
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    @Metadata(label = "producer",
              description = "This contains values which will restrict the successful operation of a variety of requests to the conditions present. These conditions are entirely optional.",
              javaType = "DataLakeRequestConditions")
    public static final String DATALAKE_REQUEST_CONDITION = HEADER_PREFIX + "RequestCondition";
    @Metadata(label = "producer",
              description = "Defines options available to configure the behavior of a call to listContainersSegment on a DataLakeFileSystemClient object. Null may be passed.",
              javaType = "ListPathOptions")
    public static final String LIST_PATH_OPTIONS = HEADER_PREFIX + "ListPathOptions";
    @Metadata(label = "producer", description = "Path of the file to be used for upload operations.", javaType = "String")
    public static final String PATH = HEADER_PREFIX + "Path";
    @Metadata(label = "producer",
              description = "Specifies if the call to listContainersSegment should recursively include all paths.",
              javaType = "Boolean")
    public static final String RECURSIVE = HEADER_PREFIX + "Recursive";
    @Metadata(label = "producer",
              description = "Specifies the maximum number of blobs to return, including all BlobPrefix elements.",
              javaType = "Integer")
    public static final String MAX_RESULTS = HEADER_PREFIX + "MaxResults";
    @Metadata(label = "producer", description = "Specifies if the name of the user principal should be returned.",
              javaType = "Boolean")
    public static final String USER_PRINCIPAL_NAME_RETURNED = HEADER_PREFIX + "UserPrincipalNameReturned";
    @Metadata(label = "producer",
              description = "Filter the results to return only those files with match the specified regular expression.",
              javaType = "String")
    public static final String REGEX = HEADER_PREFIX + "Regex";
    @Metadata(label = "producer", description = "Directory in which the file is to be downloaded.", javaType = "String")
    public static final String FILE_DIR = HEADER_PREFIX + "FileDir";
    @Metadata(label = "consumer", description = "Access tier of file.", javaType = "AccessTier")
    public static final String ACCESS_TIER = HEADER_PREFIX + "AccessTier";
    @Metadata(label = "producer",
              description = "An MD5 hash of the content. The hash is used to verify the integrity of the file during transport.",
              javaType = "byte[]")
    public static final String CONTENT_MD5 = HEADER_PREFIX + "ContentMD5";
    @Metadata(label = "producer",
              description = "This is a representation of a range of bytes on a file, typically used during a download operation. "
                            +
                            "Passing null as a FileRange value will default to the entire range of the file.",
              javaType = "FileRange")
    public static final String FILE_RANGE = HEADER_PREFIX + "FileRange";
    @Metadata(label = "producer", description = "The configuration used to parallelize data transfer operations.",
              javaType = "ParallelTransferOptions")
    public static final String PARALLEL_TRANSFER_OPTIONS = HEADER_PREFIX + "ParallelTransferOptions";
    @Metadata(label = "producer", description = "Set of OpenOption used to configure how to open or create a file.",
              javaType = "Set<OpenOption>")
    public static final String OPEN_OPTIONS = HEADER_PREFIX + "OpenOptions";
    @Metadata(label = "consumer", description = "Datetime when the access tier of the blob last changed.",
              javaType = "OffsetDateTime")
    public static final String ACCESS_TIER_CHANGE_TIME = HEADER_PREFIX + "AccessTierChangeTime";
    @Metadata(label = "consumer", description = "Archive status of file.", javaType = "ArchiveStatus")
    public static final String ARCHIVE_STATUS = HEADER_PREFIX + "ArchiveStatus";
    @Metadata(label = "consumer", description = "Cache control specified for the file.", javaType = "String")
    public static final String CACHE_CONTROL = HEADER_PREFIX + "CacheControl";
    @Metadata(label = "consumer", description = "Content disposition specified for the file.", javaType = "String")
    public static final String CONTENT_DISPOSITION = HEADER_PREFIX + "ContentDisposition";
    @Metadata(label = "consumer", description = "Content encoding specified for the file.", javaType = "String")
    public static final String CONTENT_ENCODING = HEADER_PREFIX + "ContentEncoding";
    @Metadata(label = "consumer", description = "Content language specified for the file.", javaType = "String")
    public static final String CONTENT_LANGUAGE = HEADER_PREFIX + "ContentLanguage";
    @Metadata(label = "consumer", description = "Content type specified for the file.", javaType = "String")
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    @Metadata(label = "consumer",
              description = "Conclusion time of the last attempted Copy Blob operation where this file was the destination file.",
              javaType = "OffsetDateTime")
    public static final String COPY_COMPLETION_TIME = HEADER_PREFIX + "CopyCompletionTime";
    @Metadata(label = "consumer", description = "String identifier for this copy operation.", javaType = "String")
    public static final String COPY_ID = HEADER_PREFIX + "CopyId";
    @Metadata(label = "consumer",
              description = "Contains the number of bytes copied and the total bytes in the source in the last attempted Copy Blob operation where this file was the destination file.",
              javaType = "String")
    public static final String COPY_PROGRESS = HEADER_PREFIX + "CopyProgress";
    @Metadata(label = "consumer",
              description = "URL up to 2 KB in length that specifies the source file or file used in the last attempted Copy Blob operation where this file was the destination file.",
              javaType = "String")
    public static final String COPY_SOURCE = HEADER_PREFIX + "CopySource";
    @Metadata(label = "consumer", description = "Status of the last copy operation performed on the file.",
              javaType = "com.azure.storage.file.datalake.models.CopyStatusType")
    public static final String COPY_STATUS = HEADER_PREFIX + "CopyStatus";
    @Metadata(label = "consumer", description = "The description of the copy's status", javaType = "String")
    public static final String COPY_STATUS_DESCRIPTION = HEADER_PREFIX + "CopyStatusDescription";
    @Metadata(label = "consumer", description = "Creation time of the file.", javaType = "OffsetDateTime")
    public static final String CREATION_TIME = HEADER_PREFIX + "CreationTime";
    @Metadata(label = "consumer", description = "The SHA-256 hash of the encryption key used to encrypt the file.",
              javaType = "String")
    public static final String ENCRYPTION_KEY_SHA_256 = HEADER_PREFIX + "EncryptionKeySha256";
    @Metadata(label = "consumer", description = "The E Tag of the file.", javaType = "String")
    public static final String E_TAG = HEADER_PREFIX + "ETag";
    @Metadata(label = "consumer", description = "Size of the file.", javaType = "Long")
    public static final String FILE_SIZE = HEADER_PREFIX + "FileSize";
    @Metadata(label = "consumer", description = "Datetime when the file was last modified.",
              javaType = "OffsetDateTime")
    public static final String LAST_MODIFIED = HEADER_PREFIX + "LastModified";
    @Metadata(label = "consumer", description = "Type of lease on the file.",
              javaType = "com.azure.storage.file.datalake.models.LeaseDurationType")
    public static final String LEASE_DURATION = HEADER_PREFIX + "LeaseDuration";
    @Metadata(label = "consumer", description = "State of the lease on the file.",
              javaType = "com.azure.storage.file.datalake.models.LeaseStateType")
    public static final String LEASE_STATE = HEADER_PREFIX + "LeaseState";
    @Metadata(label = "consumer", description = "Status of the lease on the file.",
              javaType = "com.azure.storage.file.datalake.models.LeaseStatusType")
    public static final String LEASE_STATUS = HEADER_PREFIX + "LeaseStatus";
    @Metadata(label = "producer", description = "Flag indicating if the file was incrementally copied.",
              javaType = "Boolean")
    public static final String INCREMENTAL_COPY = HEADER_PREFIX + "IncrementalCopy";
    @Metadata(label = "consumer", description = "Flag indicating if the file's content is encrypted on the server.",
              javaType = "Boolean")
    public static final String SERVER_ENCRYPTED = HEADER_PREFIX + "ServerEncrypted";
    @Metadata(label = "producer", description = "Set the Expiration time of the download link.", javaType = "Long")
    public static final String DOWNLOAD_LINK_EXPIRATION = HEADER_PREFIX + "DownloadLinkExpiration";
    @Metadata(label = "consumer", description = "The link that can be used to download the file from data lake.",
              javaType = "String")
    public static final String DOWNLOAD_LINK = HEADER_PREFIX + "DownloadLink";
    @Metadata(label = "producer", description = "The position where the data is to be appended.", javaType = "Long")
    public static final String FILE_OFFSET = HEADER_PREFIX + "FileOffset";
    @Metadata(label = "producer",
              description = "By setting lease id, requests will fail if the provided lease does not match the active lease on the file.",
              javaType = "String")
    public static final String LEASE_ID = HEADER_PREFIX + "LeaseId";
    @Metadata(label = "producer", description = "Additional parameters for a set of operations.",
              javaType = "PathHttpHeaders")
    public static final String PATH_HTTP_HEADERS = HEADER_PREFIX + "PathHttpHeaders";
    @Metadata(label = "producer",
              description = "Determines Whether or not uncommitted data is to be retained after the operation.",
              javaType = "Boolean")
    public static final String RETAIN_UNCOMMITED_DATA = HEADER_PREFIX + "RetainCommitedData";
    @Metadata(label = "producer",
              description = "Whether or not a file changed event raised indicates completion (true) or modification (false).",
              javaType = "Boolean")
    public static final String CLOSE = HEADER_PREFIX + "Close";
    @Metadata(label = "producer", description = "The length of the file after all data has been written.", javaType = "Long")
    public static final String POSITION = HEADER_PREFIX + "Position";
    @Metadata(label = "producer", description = "The query expression on the file.", javaType = "String")
    public static final String EXPRESSION = HEADER_PREFIX + "Expression";
    @Metadata(label = "producer",
              description = "Defines the input serialization for a file query request. either FileQueryJsonSerialization or FileQueryDelimitedSerialization",
              javaType = "FileQuerySerialization")
    public static final String INPUT_SERIALIZATION = HEADER_PREFIX + "InputSerialization";
    @Metadata(label = "producer",
              description = "Defines the output serialization for a file query request. either FileQueryJsonSerialization or FileQueryDelimitedSerialization",
              javaType = "FileQuerySerialization")
    public static final String OUTPUT_SERIALIZATION = HEADER_PREFIX + "OutputSerialization";
    @Metadata(label = "producer", description = "Sets error consumer for file query", javaType = "Consumer<FileQueryError>")
    public static final String ERROR_CONSUMER = HEADER_PREFIX + "ErrorConsumer";
    @Metadata(label = "producer", description = "Sets progress consumer for file query",
              javaType = "Consumer<FileQueryProgress>")
    public static final String PROGRESS_CONSUMER = HEADER_PREFIX + "ProgressConsumer";
    @Metadata(label = "producer", description = "Optional parameters for File Query.", javaType = "FileQueryOptions")
    public static final String QUERY_OPTIONS = HEADER_PREFIX + "QueryOptions";
    @Metadata(label = "producer", description = "Sets the permission for file.", javaType = "String")
    public static final String PERMISSION = HEADER_PREFIX + "Permission";
    @Metadata(label = "producer", description = "Sets the umask for file.", javaType = "String")
    public static final String UMASK = HEADER_PREFIX + "Umask";
    @Metadata(label = "producer", description = "Sets the file client to use", javaType = "DataLakeFileClient")
    public static final String FILE_CLIENT = HEADER_PREFIX + "FileClient";
    @Metadata(label = "producer", description = "Sets whether to flush on append", javaType = "Boolean")
    public static final String FLUSH = HEADER_PREFIX + "Flush";

    private DataLakeConstants() {
    }
}
