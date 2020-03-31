package org.apache.camel.component.azure.storage.blob.operations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.PageRange;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobType;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All operations related {@link BlobClient}. This is at the blob level.
 */
public class BlobOperations {

    private static final Logger LOG = LoggerFactory.getLogger(BlobOperations.class);

    private final BlobConfiguration configuration;
    private final BlobClientWrapper client;

    public BlobOperations(final BlobConfiguration configuration, final BlobClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse getBlob(final Exchange exchange) {
        if (exchange == null) {
            final Map<String, Object> blobInputStream = client.openInputStream(new BlobRange(0), null);
            return new BlobOperationResponse(blobInputStream.get("inputStream"), (BlobProperties) blobInputStream.get("properties"));
        }

        LOG.trace("Getting a blob [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        final OutputStream outputStream = BlobUtils.getInMessage(exchange).getBody(OutputStream.class);
        final BlobRange blobRange = getBlobRangeFromHeadersOrConfig(exchange, configuration);
        final BlobRequestConditions blobRequestConditions = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.BLOB_REQUEST_CONDITION, BlobRequestConditions.class);

        if (outputStream == null) {
            // Then we create an input stream
            final Map<String, Object> blobInputStream = client.openInputStream(blobRange, blobRequestConditions);
            return new BlobOperationResponse(blobInputStream.get("inputStream"), (BlobProperties) blobInputStream.get("properties"));
        }
        // we have an outputStream set, so we use it
        final DownloadRetryOptions downloadRetryOptions = new DownloadRetryOptions();
        downloadRetryOptions.setMaxRetryRequests(configuration.getMaxRetryRequests());
        final Duration timeout = getTimeoutFromHeaders(exchange);

        final Map<String, Object> blobDownloadResponse = client.downloadWithResponse(outputStream, blobRange, downloadRetryOptions, blobRequestConditions,
                configuration.isGetRangeContentMd5(), timeout);

        return new BlobOperationResponse(outputStream, (BlobDownloadHeaders) blobDownloadResponse.get("deserializedHeaders"), (HttpHeaders) blobDownloadResponse.get("httpHeaders"));
    }

    public BlobOperationResponse deleteBlob() {
        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        //final Response<Void> azureResponse = oldClient.deleteWithResponse(null, null, null, Context.NONE);


        return blobOperationResponse;
    }

    public BlobOperationResponse uploadBlockBlob(final Exchange exchange) throws Exception {
        final InputStream inputStream = getInputStreamFromExchange(exchange);
        final BlobHttpHeaders blobHttpHeaders = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.HTTP_HEADERS, BlobHttpHeaders.class);
        @SuppressWarnings("unchecked") final Map<String, String> metadata = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.METADATA, Map.class);
        final AccessTier accessTier = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.ACCESS_TIER, AccessTier.class);
        final byte[] contentMD5 = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.CONTENT_MD5, byte[].class);
        final BlobRequestConditions blobRequestConditions = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.BLOB_REQUEST_CONDITION, BlobRequestConditions.class);
        final Duration timeout = getTimeoutFromHeaders(exchange);

        LOG.trace("Putting a block blob [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        try {
            final Map<String, Object> uploadedResults = client.uploadBlockBlob(inputStream, -1, blobHttpHeaders, metadata, accessTier, contentMD5, blobRequestConditions, timeout);

            return new BlobOperationResponse(true, (HttpHeaders) uploadedResults.get("httpHeaders"));
        } finally {
            closeInputStreamIfNeeded(inputStream);
        }
    }

    private InputStream getInputStreamFromExchange(final Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();

        if (body instanceof WrappedFile) {
            // unwrap file
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof InputStream) {
            return (InputStream) body;
        }
        if (body instanceof File) {
            return new FileInputStream((File) body);
        }
        if (body instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) body);
        }

        // try as input stream
        final InputStream inputStream = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);

        if (inputStream == null) {
            // fallback to string based
            throw new IllegalArgumentException("Unsupported blob type:" + body.getClass().getName());
        }

        return inputStream;
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

    private void closeInputStreamIfNeeded(InputStream inputStream) throws IOException {
        // TODO: add to config isCloseStreamAfterWrite
        if (true) {
            inputStream.close();
        }
    }
}
