package org.apache.camel.component.azure.storage.blob;

import java.time.Duration;
import java.util.Map;

import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;

public class BlobCommonRequestOptions {

    private final BlobHttpHeaders blobHttpHeaders;
    private final Map<String, String> metadata;
    private final AccessTier accessTier;
    private final BlobRequestConditions blobRequestConditions;
    private final byte[] contentMD5;
    private final Duration timeout;

    public BlobCommonRequestOptions(BlobHttpHeaders blobHttpHeaders, Map<String, String> metadata, AccessTier accessTier, BlobRequestConditions blobRequestConditions, byte[] contentMD5, Duration timeout) {
        this.blobHttpHeaders = blobHttpHeaders;
        this.metadata = metadata;
        this.accessTier = accessTier;
        this.blobRequestConditions = blobRequestConditions;
        this.contentMD5 = contentMD5;
        this.timeout = timeout;
    }

    public BlobHttpHeaders getBlobHttpHeaders() {
        return blobHttpHeaders;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public AccessTier getAccessTier() {
        return accessTier;
    }

    @SuppressWarnings("unchecked")
    public <T extends BlobRequestConditions> T getBlobRequestConditions() {
        return blobRequestConditions == null ? null : (T) blobRequestConditions;
    }

    public byte[] getContentMD5() {
        return contentMD5;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String leaseId() {
        return blobRequestConditions != null ? blobRequestConditions.getLeaseId() : null;
    }
}
