package org.apache.camel.component.azure.storage.blob.operations;

import java.io.OutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobType;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related {@link BlobClient}. This is at the blob level.
 */
public class BlobOperations {

    private final BlobConfiguration configuration;
    private final BlobClientWrapper client;

    public BlobOperations(final BlobConfiguration configuration, final BlobClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse getBlob(final Exchange exchange) {
        if (exchange == null) {
            final BlobInputStream blobInputStream = client.openInputStream(new BlobRange(0), null);
            return new BlobOperationResponse(blobInputStream, blobInputStream.getProperties());
        }

        final OutputStream outputStream = BlobUtils.getInMessage(exchange).getBody(OutputStream.class);
        final BlobRange blobRange = getBlobRangeFromHeadersOrConfig(exchange, configuration);
        final BlobRequestConditions blobRequestConditions = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.BLOB_REQUEST_CONDITION, BlobRequestConditions.class);

        if (outputStream == null) {
            // Then we create an input stream
            final BlobInputStream blobInputStream = client.openInputStream(blobRange, blobRequestConditions);
            return new BlobOperationResponse(blobInputStream, blobInputStream.getProperties());
        }
        // we have an outputStream set, so we use it
        final DownloadRetryOptions downloadRetryOptions = new DownloadRetryOptions();
        downloadRetryOptions.setMaxRetryRequests(configuration.getMaxRetryRequests());
        final Duration timeout = getTimeoutFromHeaders(exchange);

        final BlobDownloadResponse blobDownloadResponse = client.downloadWithResponse(outputStream, blobRange, downloadRetryOptions, blobRequestConditions,
                configuration.isGetRangeContentMd5(), timeout);

        return new BlobOperationResponse(outputStream, blobDownloadResponse.getDeserializedHeaders(), blobDownloadResponse.getHeaders());
    }

    private boolean isFileDownloadPathIsSet(final BlobConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getFileDir());
    }

    public BlobOperationResponse deleteBlob() {
        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        //final Response<Void> azureResponse = oldClient.deleteWithResponse(null, null, null, Context.NONE);


        return blobOperationResponse;
    }

    private BlobRange getBlobRangeFromHeadersOrConfig(final Exchange exchange, final BlobConfiguration configuration) {
        if (configuration.getBlobType() == BlobType.pageblob) {
            final PageRange pageRange = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.PAGE_BLOB_RANGE, PageRange.class);
            if (pageRange != null) {
                final long blobOffset = pageRange.getStart();
                final long dataCount = pageRange.getEnd() - pageRange.getStart();

                return new BlobRange(blobOffset, dataCount);
            }
        }
        return new BlobRange(configuration.getBlobOffset(), configuration.getDataCount());
    }

    private Duration getTimeoutFromHeaders(final Exchange exchange) {
        return BlobUtils.getInMessage(exchange).getHeader(BlobConstants.TIMEOUT, Duration.class);
    }
}
