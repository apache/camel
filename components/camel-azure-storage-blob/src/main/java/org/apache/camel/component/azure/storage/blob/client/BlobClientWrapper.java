package org.apache.camel.component.azure.storage.blob.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.util.ObjectHelper;

public class BlobClientWrapper {

    private final BlobClient client;

    public BlobClientWrapper(final BlobClient client) {
        ObjectHelper.notNull(client, "client can not be null");

        this.client = client;
    }

    public BlobProperties downloadToFile(final String fileDir, final boolean overwrite) {
        return client.downloadToFile(fileDir, overwrite);
    }

    public Map<String, Object> openInputStream(final BlobRange blobRange, final BlobRequestConditions blobRequestConditions) {
        // we will retain a hashmap instead to ease the mocking of azure related classes which almost all of them marked with final
        final Map<String, Object> resultsHolder = new HashMap<>();
        final BlobInputStream blobInputStream = client.openInputStream(blobRange, blobRequestConditions);

        resultsHolder.put("inputStream", blobInputStream);
        resultsHolder.put("properties", blobInputStream.getProperties());

        return resultsHolder;
    }

    public Map<String, Object> downloadWithResponse(final OutputStream stream, final BlobRange range,
                                                    final DownloadRetryOptions options, final BlobRequestConditions requestConditions, final boolean getRangeContentMd5,
                                                    final Duration timeout) {
        // we will retain a hashmap instead to ease the mocking of azure related classes which almost all of them marked with final
        final Map<String, Object> resultsHolder = new HashMap<>();
        final BlobDownloadResponse downloadResponse = client.downloadWithResponse(stream, range, options, requestConditions, getRangeContentMd5, timeout, Context.NONE);

        resultsHolder.put("deserializedHeaders", downloadResponse.getDeserializedHeaders());
        resultsHolder.put("httpHeaders", downloadResponse.getHeaders());

        return resultsHolder;
    }

    public Map<String, Object> uploadBlockBlob(final InputStream data, final long length, final BlobHttpHeaders headers,
                                               final Map<String, String> metadata, AccessTier tier, final byte[] contentMd5, final BlobRequestConditions requestConditions,
                                               final Duration timeout) {
        // we will retain a hashmap instead to ease the mocking of azure related classes which almost all of them marked with final
        final Map<String, Object> resultsHolder = new HashMap<>();
        final Response<BlockBlobItem> uploadResponse = client.getBlockBlobClient().uploadWithResponse(data, length, headers, metadata, tier, contentMd5, requestConditions, timeout, Context.NONE);

        resultsHolder.put("deserializedHeaders", uploadResponse.getValue());
        resultsHolder.put("httpHeaders", uploadResponse.getHeaders());

        return resultsHolder;
    }

}
