package org.apache.camel.component.azure.storage.blob.client;

import java.io.OutputStream;
import java.time.Duration;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
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

    public BlobInputStream openInputStream(final BlobRange blobRange, final BlobRequestConditions blobRequestConditions) {
        return client.openInputStream(blobRange, blobRequestConditions);
    }

    public BlobDownloadResponse downloadWithResponse(final OutputStream stream, final BlobRange range,
                                                     final DownloadRetryOptions options, final BlobRequestConditions requestConditions, final boolean getRangeContentMd5,
                                                     final Duration timeout) {
        return client.downloadWithResponse(stream, range, options, requestConditions, getRangeContentMd5, timeout, Context.NONE);
    }

}
