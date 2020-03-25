package org.apache.camel.component.azure.storage.blob;

public final class BlobConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageBlob";

    // header names
    public static final String BLOB_OPERATION = HEADER_PREFIX + "Operation";
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
    public static final String BLOB_SEQUENCE_NUMBER = HEADER_PREFIX + "BlobSequenceNumber";
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
    public static final String ACCESS_TIER_CHANGE_TIME = HEADER_PREFIX + "accessTierChangeTime";
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    public static final String COMMITTED_BLOCK_COUNT = HEADER_PREFIX + "CommittedBlockCount";
    public static final String HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";

    private BlobConstants() {
    }
}
