package org.apache.camel.component.azure.storage.blob;

import java.util.HashMap;
import java.util.Map;

import com.azure.storage.blob.models.BlobProperties;

public class BlobUtils {

    public static Map<String, Object> createHeadersFromBlobProperties(final BlobProperties properties) {
        final Map<String, Object> headers = new HashMap<>();

        headers.put(BlobConstants.ACCESS_TIER, properties.getAccessTier());
        headers.put(BlobConstants.ACCESS_TIER_CHANGE_TIME, properties.getAccessTierChangeTime());
        headers.put(BlobConstants.ARCHIVE_STATUS, properties.getArchiveStatus());
        headers.put(BlobConstants.BLOB_SEQUENCE_NUMBER, properties.getBlobSequenceNumber());
        headers.put(BlobConstants.BLOB_SIZE, properties.getBlobSize());
        headers.put(BlobConstants.BLOB_TYPE, properties.getBlobType());
        headers.put(BlobConstants.CACHE_CONTROL, properties.getCacheControl());
        headers.put(BlobConstants.COMMITTED_BLOCK_COUNT, properties.getCommittedBlockCount());
        headers.put(BlobConstants.CONTENT_DISPOSITION, properties.getContentDisposition());
        headers.put(BlobConstants.CONTENT_ENCODING, properties.getContentEncoding());
        headers.put(BlobConstants.CONTENT_LANGUAGE, properties.getContentLanguage());
        headers.put(BlobConstants.CONTENT_MD5, properties.getContentMd5());
        headers.put(BlobConstants.CONTENT_TYPE, properties.getContentType());
        headers.put(BlobConstants.COPY_COMPILATION_TIME, properties.getCopyCompletionTime());
        headers.put(BlobConstants.COPY_DESTINATION_SNAPSHOT, properties.getCopyDestinationSnapshot());
        headers.put(BlobConstants.COPY_ID, properties.getCopyId());
        headers.put(BlobConstants.COPY_PROGRESS, properties.getCopyProgress());
        headers.put(BlobConstants.COPY_SOURCE, properties.getCopySource());
        headers.put(BlobConstants.COPY_STATUS, properties.getCopyStatus());
        headers.put(BlobConstants.COPY_STATUS_DESCRIPTION, properties.getCopyStatusDescription());
        headers.put(BlobConstants.CREATION_TIME, properties.getCreationTime());
        headers.put(BlobConstants.ENCRYPTION_KEY_SHA_256, properties.getEncryptionKeySha256());
        headers.put(BlobConstants.E_TAG, properties.getETag());
        headers.put(BlobConstants.IS_ACCESS_TIER_INFRRRED, properties.isAccessTierInferred());
        headers.put(BlobConstants.IS_INCREMENTAL_COPY, properties.isIncrementalCopy());
        headers.put(BlobConstants.IS_SERVER_ENCRYPTED, properties.isServerEncrypted());
        headers.put(BlobConstants.LAST_MODIFIED, properties.getLastModified());
        headers.put(BlobConstants.LEASE_DURATION, properties.getLeaseDuration());
        headers.put(BlobConstants.LEASE_STATE, properties.getLeaseState());
        headers.put(BlobConstants.LEASE_STATUS, properties.getLeaseState());
        headers.put(BlobConstants.METADATA, properties.getMetadata());

        return headers;
    }

    private BlobUtils() {
    }
}
