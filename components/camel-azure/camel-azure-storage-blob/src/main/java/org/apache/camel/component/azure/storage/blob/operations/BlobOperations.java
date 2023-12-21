/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.storage.blob.operations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.Block;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.BlockList;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.PageBlobItem;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.PageRangeItem;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobCommonRequestOptions;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.BlobStreamAndLength;
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
    public static final String MISSING_EXCHANGE = "exchange cannot be null";

    private final BlobClientWrapper client;
    private final BlobConfigurationOptionsProxy configurationProxy;

    public BlobOperations(final BlobConfiguration configuration, final BlobClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.client = client;
        this.configurationProxy = new BlobConfigurationOptionsProxy(configuration);
    }

    public BlobOperationResponse getBlob(final Exchange exchange) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting a blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        final Message message = BlobUtils.getInMessage(exchange);
        final OutputStream outputStream = ObjectHelper.isEmpty(message) ? null : message.getBody(OutputStream.class);
        final BlobRange blobRange = configurationProxy.getBlobRange(exchange);
        final BlobCommonRequestOptions blobCommonRequestOptions = getCommonRequestOptions(exchange);

        if (outputStream == null) {
            // Then we create an input stream
            final Map<String, Object> blobInputStream
                    = client.openInputStream(blobRange, blobCommonRequestOptions.getBlobRequestConditions());
            final BlobExchangeHeaders blobExchangeHeaders = BlobExchangeHeaders
                    .createBlobExchangeHeadersFromBlobProperties((BlobProperties) blobInputStream.get("properties"));

            return BlobOperationResponse.create(blobInputStream.get("inputStream"), blobExchangeHeaders.toMap());
        }
        // we have an outputStream set, so we use it
        final DownloadRetryOptions downloadRetryOptions = getDownloadRetryOptions(configurationProxy);

        try {
            final ResponseBase<BlobDownloadHeaders, Void> response = client.downloadWithResponse(outputStream, blobRange,
                    downloadRetryOptions, blobCommonRequestOptions.getBlobRequestConditions(),
                    blobCommonRequestOptions.getContentMD5() != null, blobCommonRequestOptions.getTimeout());

            final BlobExchangeHeaders blobExchangeHeaders
                    = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobDownloadHeaders(response.getDeserializedHeaders())
                            .httpHeaders(response.getHeaders());

            return BlobOperationResponse.create(outputStream, blobExchangeHeaders.toMap());
        } finally {
            if (configurationProxy.getConfiguration().isCloseStreamAfterRead()) {
                outputStream.close();
            }
        }
    }

    public BlobOperationResponse downloadBlobToFile(final Exchange exchange) {
        // check for fileDir
        final String fileDir = configurationProxy.getFileDir(exchange);
        if (ObjectHelper.isEmpty(fileDir)) {
            throw new IllegalArgumentException("In order to download a blob, you will need to specify the fileDir in the URI");
        }

        final File fileToDownload = new File(fileDir, client.getBlobName());
        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final BlobRange blobRange = configurationProxy.getBlobRange(exchange);
        final ParallelTransferOptions parallelTransferOptions = configurationProxy.getParallelTransferOptions(exchange);
        final DownloadRetryOptions downloadRetryOptions = getDownloadRetryOptions(configurationProxy);

        final Response<BlobProperties> response = client.downloadToFileWithResponse(fileToDownload.toString(), blobRange,
                parallelTransferOptions, downloadRetryOptions,
                commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getContentMD5() != null,
                commonRequestOptions.getTimeout());

        final BlobExchangeHeaders exchangeHeaders
                = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobProperties(response.getValue())
                        .httpHeaders(response.getHeaders())
                        .fileName(fileToDownload.toString());

        return BlobOperationResponse.create(fileToDownload, exchangeHeaders.toMap());
    }

    public BlobOperationResponse deleteBlob(final Exchange exchange) {
        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final DeleteSnapshotsOptionType deleteSnapshotsOptionType = configurationProxy.getDeleteSnapshotsOptionType(exchange);

        return BlobOperationResponse
                .createWithEmptyBody(client.delete(deleteSnapshotsOptionType, commonRequestOptions.getBlobRequestConditions(),
                        commonRequestOptions.getTimeout()));
    }

    public BlobOperationResponse downloadLink(final Exchange exchange) {
        final OffsetDateTime offsetDateTime = OffsetDateTime.now();
        final long defaultExpirationTime = 60L * 60L; // 1 hour
        final BlobSasPermission sasPermission = new BlobSasPermission().setReadPermission(true); // only read access
        final Long expirationMillis = configurationProxy.getDownloadLinkExpiration(exchange);

        OffsetDateTime offsetDateTimeToSet;
        if (expirationMillis != null) {
            offsetDateTimeToSet = offsetDateTime.plusSeconds(expirationMillis / 1000);
        } else {
            offsetDateTimeToSet = offsetDateTime.plusSeconds(defaultExpirationTime);
        }

        final BlobServiceSasSignatureValues serviceSasSignatureValues
                = new BlobServiceSasSignatureValues(offsetDateTimeToSet, sasPermission);
        final String url = client.getBlobUrl() + "?" + client.generateSas(serviceSasSignatureValues);

        final BlobExchangeHeaders headers = BlobExchangeHeaders.create().downloadLink(url);

        return BlobOperationResponse.createWithEmptyBody(headers.toMap());
    }

    public BlobOperationResponse uploadBlockBlob(final Exchange exchange) throws IOException {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final BlobStreamAndLength blobStreamAndLength = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);
        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Putting a block blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        try {
            final Response<BlockBlobItem> response = client.uploadBlockBlob(blobStreamAndLength.getInputStream(),
                    blobStreamAndLength.getStreamLength(), commonRequestOptions.getBlobHttpHeaders(),
                    commonRequestOptions.getMetadata(), commonRequestOptions.getAccessTier(),
                    commonRequestOptions.getContentMD5(), commonRequestOptions.getBlobRequestConditions(),
                    commonRequestOptions.getTimeout());

            return BlobOperationResponse.createWithEmptyBody(response);
        } finally {
            closeInputStreamIfNeeded(blobStreamAndLength.getInputStream());
        }
    }

    public BlobOperationResponse stageBlockBlobList(final Exchange exchange) throws Exception {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final Object object = exchange.getIn().getMandatoryBody();

        List<BlobBlock> blobBlocks = null;
        if (object instanceof List) {
            // noinspection unchecked
            blobBlocks = (List<BlobBlock>) object;
        } else if (object instanceof BlobBlock) {
            blobBlocks = Collections.singletonList((BlobBlock) object);
        }
        if (blobBlocks == null || blobBlocks.isEmpty()) {
            throw new IllegalArgumentException("Illegal storageBlocks payload");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Putting a blob [{}] from blocks from exchange [{}]...", configurationProxy.getBlobName(exchange),
                    exchange);
        }

        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        final List<Block> blockEntries = new LinkedList<>();

        blobBlocks.forEach(blobBlock -> {
            blockEntries.add(blobBlock.getBlockEntry());
            client.stageBlockBlob(blobBlock.getBlockEntry().getName(),
                    blobBlock.getBlockStream(),
                    blobBlock.getBlockEntry().getSizeLong(),
                    commonRequestOptions.getContentMD5(),
                    commonRequestOptions.leaseId(),
                    commonRequestOptions.getTimeout());
        });

        final boolean commitBlockListLater = configurationProxy.isCommitBlockListLater(exchange);

        if (!commitBlockListLater) {
            // let us commit now
            exchange.getIn().setBody(blockEntries);
            return commitBlobBlockList(exchange);
        }

        return BlobOperationResponse.createWithEmptyBody();
    }

    @SuppressWarnings("unchecked")
    public BlobOperationResponse commitBlobBlockList(final Exchange exchange) throws Exception {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final Object object = exchange.getIn().getMandatoryBody();

        List<Block> blockEntries = null;
        if (object instanceof List) {
            blockEntries = (List<Block>) object;
        } else if (object instanceof Block) {
            blockEntries = Collections.singletonList((Block) object);
        }
        if (blockEntries == null || blockEntries.isEmpty()) {
            throw new IllegalArgumentException("Illegal commit block list payload");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Putting a blob [{}] block list from exchange [{}]...", configurationProxy.getBlobName(exchange),
                    exchange);
        }

        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        final List<String> blockIds = blockEntries.stream()
                .map(Block::getName)
                .toList();

        final Response<BlockBlobItem> response = client.commitBlockBlob(blockIds, commonRequestOptions.getBlobHttpHeaders(),
                commonRequestOptions.getMetadata(),
                commonRequestOptions.getAccessTier(), commonRequestOptions.getBlobRequestConditions(),
                commonRequestOptions.getTimeout());

        return BlobOperationResponse.createWithEmptyBody(response);
    }

    public BlobOperationResponse getBlobBlockList(final Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting the blob block list [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange),
                    exchange);
        }

        final BlockListType blockListType = configurationProxy.getBlockListType(exchange);
        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        final Response<BlockList> response
                = client.listBlobBlocks(blockListType, commonRequestOptions.leaseId(), commonRequestOptions.getTimeout());

        return BlobOperationResponse.create(response);
    }

    public BlobOperationResponse createAppendBlob(final Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating an append blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        final Response<AppendBlobItem> response
                = client.createAppendBlob(commonRequestOptions.getBlobHttpHeaders(), commonRequestOptions.getMetadata(),
                        commonRequestOptions.getBlobRequestConditions(), commonRequestOptions.getTimeout());

        return BlobOperationResponse.createWithEmptyBody(response);
    }

    public BlobOperationResponse copyBlob(final Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating an append blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        String sourceBlobName = configurationProxy.getBlobName(exchange);
        String sourceAccountName = exchange.getMessage().getHeader(BlobConstants.SOURCE_BLOB_ACCOUNT_NAME, String.class);
        if (ObjectHelper.isEmpty(sourceAccountName)) {
            throw new IllegalArgumentException("Source Account Name must be specified for copyBlob Operation");
        }
        String sourceContainerName = exchange.getMessage().getHeader(BlobConstants.SOURCE_BLOB_CONTAINER_NAME, String.class);
        if (ObjectHelper.isEmpty(sourceAccountName)) {
            throw new IllegalArgumentException("Source Container Name must be specified for copyBlob Operation");
        }
        final String response
                = client.copyBlob(sourceBlobName, sourceAccountName, sourceContainerName,
                        configurationProxy.getConfiguration().getSourceBlobAccessKey());

        return BlobOperationResponse.create(response);
    }

    public BlobOperationResponse commitAppendBlob(final Exchange exchange) throws IOException {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final boolean createAppendBlob = configurationProxy.isCreateAppendBlob(exchange);

        // only if header is true and we don't have one exists
        if (createAppendBlob && !client.appendBlobExists()) {
            createAppendBlob(exchange);
        }

        final BlobStreamAndLength streamAndLength = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);

        try {
            final Response<AppendBlobItem> response
                    = client.appendBlobBlock(streamAndLength.getInputStream(), streamAndLength.getStreamLength(),
                            commonRequestOptions.getContentMD5(), commonRequestOptions.getBlobRequestConditions(),
                            commonRequestOptions.getTimeout());

            return BlobOperationResponse.createWithEmptyBody(response);
        } finally {
            closeInputStreamIfNeeded(streamAndLength.getInputStream());
        }
    }

    public BlobOperationResponse createPageBlob(final Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating a page blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        final Long pageSize = getPageBlobSize(exchange);
        final BlobCommonRequestOptions requestOptions = getCommonRequestOptions(exchange);
        final Long sequenceNumber = configurationProxy.getBlobSequenceNumber(exchange);

        final Response<PageBlobItem> response
                = client.createPageBlob(pageSize, sequenceNumber, requestOptions.getBlobHttpHeaders(),
                        requestOptions.getMetadata(), requestOptions.getBlobRequestConditions(), requestOptions.getTimeout());

        return BlobOperationResponse.createWithEmptyBody(response);
    }

    public BlobOperationResponse uploadPageBlob(final Exchange exchange) throws IOException {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final boolean createPageBlob = configurationProxy.isCreatePageBlob(exchange);

        // only if header is true and we don't have one exists
        if (createPageBlob && !client.pageBlobExists()) {
            createPageBlob(exchange);
        }

        final BlobStreamAndLength streamAndLength = BlobStreamAndLength.createBlobStreamAndLengthFromExchangeBody(exchange);
        final BlobCommonRequestOptions requestOptions = getCommonRequestOptions(exchange);
        final PageRange pageRange = configurationProxy.getPageRange(exchange);

        if (pageRange == null) {
            throw new IllegalArgumentException("You need to set page range in the exchange headers.");
        }

        try {
            final Response<PageBlobItem> response
                    = client.uploadPageBlob(pageRange, streamAndLength.getInputStream(), requestOptions.getContentMD5(),
                            requestOptions.getBlobRequestConditions(), requestOptions.getTimeout());

            return BlobOperationResponse.createWithEmptyBody(response);
        } finally {
            closeInputStreamIfNeeded(streamAndLength.getInputStream());
        }
    }

    public BlobOperationResponse resizePageBlob(final Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Resizing a page blob [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange), exchange);
        }

        final Long pageSize = getPageBlobSize(exchange);
        final BlobCommonRequestOptions requestOptions = getCommonRequestOptions(exchange);

        final Response<PageBlobItem> response
                = client.resizePageBlob(pageSize, requestOptions.getBlobRequestConditions(), requestOptions.getTimeout());

        return BlobOperationResponse.createWithEmptyBody(response);
    }

    public BlobOperationResponse clearPageBlob(final Exchange exchange) {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final PageRange pageRange = configurationProxy.getPageRange(exchange);
        final BlobCommonRequestOptions requestOptions = getCommonRequestOptions(exchange);

        if (pageRange == null) {
            throw new IllegalArgumentException("You need to set page range in the exchange headers.");
        }

        final Response<PageBlobItem> response
                = client.clearPagesBlob(pageRange, requestOptions.getBlobRequestConditions(), requestOptions.getTimeout());

        return BlobOperationResponse.createWithEmptyBody(response);
    }

    public BlobOperationResponse getPageBlobRanges(final Exchange exchange) {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final BlobRange blobRange = configurationProxy.getBlobRange(exchange);
        final BlobCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting the page blob ranges [{}] from exchange [{}]...", configurationProxy.getBlobName(exchange),
                    exchange);
        }

        final PagedIterable<PageRangeItem> response
                = client.getPageBlobRanges(blobRange, commonRequestOptions.getBlobRequestConditions(),
                        commonRequestOptions.getTimeout());

        return BlobOperationResponse.create(response);
    }

    private DownloadRetryOptions getDownloadRetryOptions(final BlobConfigurationOptionsProxy configurationProxy) {
        return new DownloadRetryOptions().setMaxRetryRequests(configurationProxy.getMaxRetryRequests());
    }

    private BlobCommonRequestOptions getCommonRequestOptions(final Exchange exchange) {
        final BlobHttpHeaders blobHttpHeaders = configurationProxy.getBlobHttpHeaders(exchange);
        final Map<String, String> metadata = configurationProxy.getMetadata(exchange);
        final AccessTier accessTier = configurationProxy.getAccessTier(exchange);
        final BlobRequestConditions blobRequestConditions = configurationProxy.getBlobRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final byte[] contentMD5 = configurationProxy.getContentMd5(exchange);

        return new BlobCommonRequestOptions(blobHttpHeaders, metadata, accessTier, blobRequestConditions, contentMD5, timeout);
    }

    private Long getPageBlobSize(final Exchange exchange) {
        // we try to get the size from the page range if exists
        final PageRange pageRange = configurationProxy.getPageRange(exchange);
        if (pageRange != null) {
            return pageRange.getEnd() - pageRange.getStart() + 1; //e.g: 1023-0+1 = 1024 size
        }
        // now we try the page size
        final Long pageSize = configurationProxy.getPageBlobSize(exchange);
        if (pageSize != null) {
            return pageSize;
        }
        return BlobConstants.PAGE_BLOB_DEFAULT_SIZE;
    }

    private void closeInputStreamIfNeeded(InputStream inputStream) throws IOException {
        if (configurationProxy.getConfiguration().isCloseStreamAfterWrite()) {
            inputStream.close();
        }
    }
}
