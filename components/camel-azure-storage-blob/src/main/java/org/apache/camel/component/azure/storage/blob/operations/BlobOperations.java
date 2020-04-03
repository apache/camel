package org.apache.camel.component.azure.storage.blob.operations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.AppendBlobRequestConditions;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.Block;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.BlockList;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.PageRange;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobCommonRequestOptions;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobStreamAndLength;
import org.apache.camel.component.azure.storage.blob.BlobType;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
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
            final BlobClientWrapper.ResponseEnvelope<InputStream, BlobProperties> blobInputStream = client.openInputStream(new BlobRange(0), null);
            final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobProperties(blobInputStream.getSecondObject());

            return new BlobOperationResponse(blobInputStream.getFirstObject(), blobExchangeHeaders.toMap());
        }

        LOG.trace("Getting a blob [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        final OutputStream outputStream = BlobUtils.getInMessage(exchange).getBody(OutputStream.class);
        final BlobRange blobRange = getBlobRangeFromHeadersOrConfig(exchange, configuration);
        final BlobCommonRequestOptions blobCommonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        if (outputStream == null) {
            // Then we create an input stream
            final BlobClientWrapper.ResponseEnvelope<InputStream, BlobProperties> blobInputStream = client.openInputStream(blobRange, blobCommonRequestOptions.getBlobRequestConditions());
            final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobProperties(blobInputStream.getSecondObject());

            return new BlobOperationResponse(blobInputStream.getFirstObject(), blobExchangeHeaders.toMap());
        }
        // we have an outputStream set, so we use it
        final DownloadRetryOptions downloadRetryOptions = new DownloadRetryOptions();
        downloadRetryOptions.setMaxRetryRequests(configuration.getMaxRetryRequests());

        final BlobClientWrapper.ResponseEnvelope<BlobDownloadHeaders, HttpHeaders> blobDownloadResponse = client.downloadWithResponse(outputStream, blobRange, downloadRetryOptions, blobCommonRequestOptions.getBlobRequestConditions(),
                configuration.isGetRangeContentMd5(), blobCommonRequestOptions.getTimeout());

        final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobDownloadHeaders(blobDownloadResponse.getFirstObject())
                .httpHeaders(blobDownloadResponse.getSecondObject());

        return new BlobOperationResponse(outputStream, blobExchangeHeaders.toMap());
    }

    public BlobOperationResponse uploadBlockBlob(final Exchange exchange) throws IOException {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final BlobStreamAndLength blobStreamAndLength = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);
        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        LOG.trace("Putting a block blob [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        try {
            final BlobClientWrapper.ResponseEnvelope<BlockBlobItem, HttpHeaders> uploadedResults = client.uploadBlockBlob(blobStreamAndLength.getInputStream(), blobStreamAndLength.getStreamLength(), commonRequestOptions.getBlobHttpHeaders(), commonRequestOptions.getMetadata(), commonRequestOptions.getAccessTier(),
                    commonRequestOptions.getContentMD5(), commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getTimeout());
            final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromBlockBlobItem(uploadedResults.getFirstObject())
                    .httpHeaders(uploadedResults.getSecondObject());

            return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
        } finally {
            closeInputStreamIfNeeded(blobStreamAndLength.getInputStream());
        }
    }

    public BlobOperationResponse stageBlockBlobList(final Exchange exchange) throws Exception {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final Object object = exchange.getIn().getMandatoryBody();

        List<BlobBlock> blobBlocks = null;
        if (object instanceof List) {
            blobBlocks = (List<BlobBlock>) object;
        } else if (object instanceof BlobBlock) {
            blobBlocks = Collections.singletonList((BlobBlock)object);
        }
        if (blobBlocks == null || blobBlocks.isEmpty()) {
            throw new IllegalArgumentException("Illegal storageBlocks payload");
        }

        LOG.trace("Putting a blob [{}] from blocks from exchange [{}]...", configuration.getBlobName(), exchange);

        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        final List<Block> blockEntries = new LinkedList<>();

        blobBlocks.forEach(blobBlock -> {
            blockEntries.add(blobBlock.getBlockEntry());
            client.stageBlockBlob(blobBlock.getBlockEntry().getName(), blobBlock.getBlockStream(), blobBlock.getBlockEntry().getSize(),
                    commonRequestOptions.getContentMD5(), commonRequestOptions.leaseId(), commonRequestOptions.getTimeout());
        });

        final boolean commitBlockListLater = BlobExchangeHeaders.getCommitBlockListFlagFromHeaders(exchange);

        if (!commitBlockListLater) {
            // let us commit now
            exchange.getIn().setBody(blockEntries);
            return commitBlobBlockList(exchange);
        }

        return new BlobOperationResponse(true);
    }

    public BlobOperationResponse commitBlobBlockList(final Exchange exchange) throws Exception {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final Object object = exchange.getIn().getMandatoryBody();

        List<Block> blockEntries = null;
        if (object instanceof List) {
            blockEntries = (List<Block>) object;
        } else if (object instanceof Block) {
            blockEntries = Collections.singletonList((Block)object);
        }
        if (blockEntries == null || blockEntries.isEmpty()) {
            throw new IllegalArgumentException("Illegal commit block list payload");
        }

        LOG.trace("Putting a blob [{}] block list from exchange [{}]...", configuration.getBlobName(), exchange);

        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        final List<String> blockIds = blockEntries.stream()
                .map(Block::getName)
                .collect(Collectors.toList());

        final BlobClientWrapper.ResponseEnvelope<BlockBlobItem, HttpHeaders> responseEnvelope = client.commitBlockBlob(blockIds, commonRequestOptions.getBlobHttpHeaders(), commonRequestOptions.getMetadata(),
                commonRequestOptions.getAccessTier(), commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getTimeout());
        final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromBlockBlobItem(responseEnvelope.getFirstObject())
                .httpHeaders(responseEnvelope.getSecondObject());

        return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
    }

    public BlobOperationResponse getBlobBlockList(final Exchange exchange) {
        if (exchange == null) {
            final BlobClientWrapper.ResponseEnvelope<BlockList, HttpHeaders> responseEnvelop = client.listBlobBlocks(BlockListType.COMMITTED, null, null);
            final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.create().httpHeaders(responseEnvelop.getSecondObject());

            return new BlobOperationResponse(responseEnvelop.getFirstObject(), blobExchangeHeaders.toMap());
        }

        LOG.trace("Getting the blob block list [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        final BlockListType blockListType = BlobExchangeHeaders.getBlockListTypeFromHeaders(exchange) == null ? BlockListType.COMMITTED : BlobExchangeHeaders.getBlockListTypeFromHeaders(exchange);
        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        final BlobClientWrapper.ResponseEnvelope<BlockList, HttpHeaders> responseEnvelop = client.listBlobBlocks(blockListType, commonRequestOptions.leaseId(), commonRequestOptions.getTimeout());
        final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders.create().httpHeaders(responseEnvelop.getSecondObject());

        return new BlobOperationResponse(responseEnvelop.getFirstObject(), blobExchangeHeaders.toMap());
    }

    public BlobOperationResponse createAppendBlob(final Exchange exchange) {
        if (exchange == null) {
            final BlobClientWrapper.ResponseEnvelope<AppendBlobItem, HttpHeaders> responseEnvelope = client.createAppendBlob(null, null, null, null);
            final BlobExchangeHeaders exchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromAppendBlobItem(responseEnvelope.getFirstObject()).httpHeaders(responseEnvelope.getSecondObject());

            return new BlobOperationResponse(true, exchangeHeaders.toMap());
        }

        LOG.trace("Creating an append blob [{}] from exchange [{}]...", configuration.getBlobName(), exchange);

        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);

        final BlobClientWrapper.ResponseEnvelope<AppendBlobItem, HttpHeaders> responseEnvelope = client.createAppendBlob(commonRequestOptions.getBlobHttpHeaders(), commonRequestOptions.getMetadata(),
                commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getTimeout());
        final BlobExchangeHeaders exchangeHeaders = BlobExchangeHeaders.create().httpHeaders(responseEnvelope.getSecondObject());

        return new BlobOperationResponse(true, exchangeHeaders.toMap());
    }

    public BlobOperationResponse updateAppendBlob(final Exchange exchange) throws IOException {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final BlobCommonRequestOptions commonRequestOptions = BlobUtils.getCommonRequestOptions(exchange);
        final boolean appendBlockCreated = BlobExchangeHeaders.getAppendBlockCreatedFlagFromHeaders(exchange);

        if (!appendBlockCreated) {
            createAppendBlob(exchange);
        }

        final BlobStreamAndLength streamAndLength = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);

        try {
            final BlobClientWrapper.ResponseEnvelope<AppendBlobItem, HttpHeaders> responseEnvelope = client.appendBlobBlock(streamAndLength.getInputStream(), streamAndLength.getStreamLength(),
                    commonRequestOptions.getContentMD5(), (AppendBlobRequestConditions) commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getTimeout());
            final BlobExchangeHeaders exchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromAppendBlobItem(responseEnvelope.getFirstObject())
                    .httpHeaders(responseEnvelope.getSecondObject());

            return new BlobOperationResponse(true, exchangeHeaders.toMap());
        } finally {
            closeInputStreamIfNeeded(streamAndLength.getInputStream());
        }
    }

    private BlobRange getBlobRangeFromHeadersOrConfig(final Exchange exchange, final BlobConfiguration configuration) {
        if (configuration.getBlobType() == BlobType.pageblob) {
            final PageRange pageRange = BlobExchangeHeaders.getPageRangeFromHeaders(exchange);
            if (pageRange != null) {
                final long blobOffset = pageRange.getStart();
                final long dataCount = pageRange.getEnd() - pageRange.getStart();

                return new BlobRange(blobOffset, dataCount);
            }
        }
        return new BlobRange(configuration.getBlobOffset(), configuration.getDataCount());
    }

    private void closeInputStreamIfNeeded(InputStream inputStream) throws IOException {
        if (configuration.isCloseStreamAfterWrite()) {
            inputStream.close();
        }
    }
}
