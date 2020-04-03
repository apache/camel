package org.apache.camel.component.azure.storage.blob.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.AppendBlobRequestConditions;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.BlockList;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.specialized.PageBlobClient;
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

    public HttpHeaders delete(final DeleteSnapshotsOptionType deleteBlobSnapshotOptions,
                              final BlobRequestConditions requestConditions, final Duration timeout) {
        return client.deleteWithResponse(deleteBlobSnapshotOptions, requestConditions, timeout, Context.NONE).getHeaders();
    }

    public ResponseEnvelope<InputStream, BlobProperties> openInputStream(final BlobRange blobRange, final BlobRequestConditions blobRequestConditions) {
        final BlobInputStream blobInputStream = client.openInputStream(blobRange, blobRequestConditions);

        return new ResponseEnvelope<>(blobInputStream, blobInputStream.getProperties());
    }

    public ResponseEnvelope<BlobDownloadHeaders, HttpHeaders> downloadWithResponse(final OutputStream stream, final BlobRange range,
                                                                                   final DownloadRetryOptions options, final BlobRequestConditions requestConditions, final boolean getRangeContentMd5,
                                                                                   final Duration timeout) {
        final BlobDownloadResponse downloadResponse = client.downloadWithResponse(stream, range, options, requestConditions, getRangeContentMd5, timeout, Context.NONE);

        return new ResponseEnvelope<>(downloadResponse.getDeserializedHeaders(), downloadResponse.getHeaders());
    }

    public ResponseEnvelope<BlockBlobItem, HttpHeaders> uploadBlockBlob(final InputStream data, final long length, final BlobHttpHeaders headers,
                                                                        final Map<String, String> metadata, AccessTier tier, final byte[] contentMd5, final BlobRequestConditions requestConditions,
                                                                        final Duration timeout) {
        final Response<BlockBlobItem> uploadResponse = getBlockBlobClient().uploadWithResponse(data, length, headers, metadata, tier, contentMd5, requestConditions, timeout, Context.NONE);

        return new ResponseEnvelope<>(uploadResponse.getValue(), uploadResponse.getHeaders());
    }

    public HttpHeaders stageBlockBlob(final String base64BlockId, final InputStream data, final long length, final byte[] contentMd5,
                                      final String leaseId, final Duration timeout) {
        return client.getBlockBlobClient().stageBlockWithResponse(base64BlockId, data, length, contentMd5, leaseId, timeout, Context.NONE).getHeaders();
    }

    public ResponseEnvelope<BlockBlobItem, HttpHeaders> commitBlockBlob(final List<String> base64BlockIds, final BlobHttpHeaders headers,
                                                                        final Map<String, String> metadata, final AccessTier tier, final BlobRequestConditions requestConditions, final Duration timeout) {
        final Response<BlockBlobItem> response = getBlockBlobClient().commitBlockListWithResponse(base64BlockIds, headers, metadata, tier, requestConditions, timeout, Context.NONE);

        return new ResponseEnvelope<>(response.getValue(), response.getHeaders());
    }

    public ResponseEnvelope<BlockList, HttpHeaders> listBlobBlocks(final BlockListType listType, final String leaseId, final Duration timeout) {
        final Response<BlockList> response = getBlockBlobClient().listBlocksWithResponse(listType, leaseId, timeout, Context.NONE);

        return new ResponseEnvelope<>(response.getValue(), response.getHeaders());
    }

    public ResponseEnvelope<AppendBlobItem, HttpHeaders> createAppendBlob(final BlobHttpHeaders headers, final Map<String, String> metadata,
                                                                                 final BlobRequestConditions requestConditions, final Duration timeout) {
        final Response<AppendBlobItem> response = getAppendBlobClient().createWithResponse(headers, metadata, requestConditions, timeout, Context.NONE);

        return new ResponseEnvelope<>(response.getValue(), response.getHeaders());
    }

    public ResponseEnvelope<AppendBlobItem, HttpHeaders> appendBlobBlock(final InputStream data, final long length, final byte[] contentMd5,
                                                                    final AppendBlobRequestConditions appendBlobRequestConditions, final Duration timeout) {
        final Response<AppendBlobItem> response = getAppendBlobClient().appendBlockWithResponse(data, length, contentMd5, appendBlobRequestConditions, timeout, Context.NONE);

        return new ResponseEnvelope<>(response.getValue(), response.getHeaders());
    }


    private BlockBlobClient getBlockBlobClient() {
        return client.getBlockBlobClient();
    }

    private AppendBlobClient getAppendBlobClient() {
        return client.getAppendBlobClient();
    }

    private PageBlobClient getPageBlobClient() {
        return client.getPageBlobClient();
    }

    /**
     * Envelope class intends to contain the responses from the client wrapper in order to
     * ease the unit testing in terms of mocking
     */
    public final static class ResponseEnvelope<FO, SO> {
        private final FO firstObject;
        private final SO secondObject;

        public ResponseEnvelope(FO firstObject, SO secondObject) {
            this.firstObject = firstObject;
            this.secondObject = secondObject;
        }

        public FO getFirstObject() {
            return firstObject;
        }

        public SO getSecondObject() {
            return secondObject;
        }
    }

}
