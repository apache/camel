package org.apache.camel.component.azure.storage.blob.operations;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobProperties;
import org.apache.camel.component.azure.storage.blob.BlobConstants;

public class BlobOperationResponse {

    private Object body;
    private Map<String, Object> headers = new HashMap<>();

    public BlobOperationResponse() {
    }

    public BlobOperationResponse(final Object body, final BlobProperties properties) {
        setBody(body);
        setHeaders(properties);
    }

    public BlobOperationResponse(final Object body, final HttpHeaders httpHeaders) {
        setBody(body);
        setHttpHeaders(httpHeaders);
    }

    public BlobOperationResponse(final Object body, final BlobDownloadHeaders downloadHeaders, final HttpHeaders httpHeaders) {
        setBody(body);
        setHeaders(downloadHeaders);
        setHttpHeaders(httpHeaders);
    }

    public BlobOperationResponse(final Object body) {
        setBody(body);
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(final BlobProperties properties) {
        headers.putAll(createHeadersFromBlobProperties(properties));
    }

    public void setHeaders(final BlobDownloadHeaders downloadHeaders) {
        headers.putAll(createHeadersFromBlobProperties(buildBlobProperties(downloadHeaders)));
    }

    public void setHttpHeaders(final HttpHeaders httpHeaders) {
        headers.put(BlobConstants.HTTP_HEADERS, httpHeaders.toMap());
    }

    private Map<String, Object> createHeadersFromBlobProperties(final BlobProperties properties) {
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

    private BlobProperties buildBlobProperties(final BlobDownloadHeaders hd) {
        if (hd == null) {
            return null;
        }
        return new BlobProperties(null, hd.getLastModified(), hd.getETag(),
                hd.getContentLength() == null ? 0 : hd.getContentLength(), hd.getContentType(), null,
                hd.getContentEncoding(), hd.getContentDisposition(), hd.getContentLanguage(), hd.getCacheControl(),
                hd.getBlobSequenceNumber(), hd.getBlobType(), hd.getLeaseStatus(), hd.getLeaseState(),
                hd.getLeaseDuration(), hd.getCopyId(), hd.getCopyStatus(), hd.getCopySource(), hd.getCopyProgress(),
                hd.getCopyCompletionTime(), hd.getCopyStatusDescription(), hd.isServerEncrypted(),
                null, null, null, null, null, hd.getEncryptionKeySha256(), null, hd.getMetadata(),
                hd.getBlobCommittedBlockCount());
    }
}
