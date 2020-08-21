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
package org.apache.camel.component.azure.storage.blob.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.ResponseBase;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.AppendBlobRequestConditions;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.BlockList;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.models.PageBlobItem;
import com.azure.storage.blob.models.PageBlobRequestConditions;
import com.azure.storage.blob.models.PageList;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
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

    public String getBlobName() {
        return client.getBlobName();
    }

    public String getBlobUrl() {
        return client.getBlobUrl();
    }

    public Response<Void> delete(
            final DeleteSnapshotsOptionType deleteBlobSnapshotOptions,
            final BlobRequestConditions requestConditions, final Duration timeout) {
        return client.deleteWithResponse(deleteBlobSnapshotOptions, requestConditions, timeout, Context.NONE);
    }

    public Map<String, Object> openInputStream(final BlobRange blobRange, final BlobRequestConditions blobRequestConditions) {

        final BlobInputStream blobInputStream = client.openInputStream(blobRange, blobRequestConditions);

        // Hack to fold the response in order to ease the mocking in the unit tests
        final Map<String, Object> results = new HashMap<>();
        results.put("inputStream", blobInputStream);
        results.put("properties", blobInputStream.getProperties());

        return results;
    }

    public ResponseBase<BlobDownloadHeaders, Void> downloadWithResponse(
            final OutputStream stream, final BlobRange range,
            final DownloadRetryOptions options, final BlobRequestConditions requestConditions, final boolean getRangeContentMd5,
            final Duration timeout) {
        return client.downloadWithResponse(stream, range, options, requestConditions, getRangeContentMd5, timeout,
                Context.NONE);
    }

    public Response<BlobProperties> downloadToFileWithResponse(
            final String filePath, final BlobRange range,
            final ParallelTransferOptions parallelTransferOptions, final DownloadRetryOptions downloadRetryOptions,
            final BlobRequestConditions requestConditions,
            final boolean rangeGetContentMd5, final Duration timeout) {
        return client.downloadToFileWithResponse(filePath, range, parallelTransferOptions, downloadRetryOptions,
                requestConditions, rangeGetContentMd5, timeout, Context.NONE);
    }

    public Response<BlockBlobItem> uploadBlockBlob(
            final InputStream data, final long length, final BlobHttpHeaders headers,
            final Map<String, String> metadata, AccessTier tier, final byte[] contentMd5,
            final BlobRequestConditions requestConditions,
            final Duration timeout) {
        return getBlockBlobClient().uploadWithResponse(data, length, headers, metadata, tier, contentMd5, requestConditions,
                timeout, Context.NONE);
    }

    public HttpHeaders stageBlockBlob(
            final String base64BlockId, final InputStream data, final long length, final byte[] contentMd5,
            final String leaseId, final Duration timeout) {
        return client.getBlockBlobClient()
                .stageBlockWithResponse(base64BlockId, data, length, contentMd5, leaseId, timeout, Context.NONE).getHeaders();
    }

    public Response<BlockBlobItem> commitBlockBlob(
            final List<String> base64BlockIds, final BlobHttpHeaders headers,
            final Map<String, String> metadata, final AccessTier tier, final BlobRequestConditions requestConditions,
            final Duration timeout) {
        return getBlockBlobClient().commitBlockListWithResponse(base64BlockIds, headers, metadata, tier, requestConditions,
                timeout, Context.NONE);
    }

    public Response<BlockList> listBlobBlocks(final BlockListType listType, final String leaseId, final Duration timeout) {
        return getBlockBlobClient().listBlocksWithResponse(listType, leaseId, timeout, Context.NONE);
    }

    public Response<AppendBlobItem> createAppendBlob(
            final BlobHttpHeaders headers, final Map<String, String> metadata,
            final BlobRequestConditions requestConditions, final Duration timeout) {

        return getAppendBlobClient().createWithResponse(headers, metadata, requestConditions, timeout, Context.NONE);
    }

    public Response<AppendBlobItem> appendBlobBlock(
            final InputStream data, final long length, final byte[] contentMd5,
            final AppendBlobRequestConditions appendBlobRequestConditions, final Duration timeout) {
        return getAppendBlobClient().appendBlockWithResponse(data, length, contentMd5, appendBlobRequestConditions, timeout,
                Context.NONE);
    }

    public boolean appendBlobExists() {
        return getAppendBlobClient().exists();
    }

    public Response<PageBlobItem> createPageBlob(
            final long size, final Long sequenceNumber, final BlobHttpHeaders headers,
            final Map<String, String> metadata, final BlobRequestConditions requestConditions, final Duration timeout) {
        return getPageBlobClient().createWithResponse(size, sequenceNumber, headers, metadata, requestConditions, timeout,
                Context.NONE);
    }

    public Response<PageBlobItem> uploadPageBlob(
            final PageRange pageRange, final InputStream body, final byte[] contentMd5,
            final PageBlobRequestConditions pageBlobRequestConditions, final Duration timeout) {
        return getPageBlobClient().uploadPagesWithResponse(pageRange, body, contentMd5, pageBlobRequestConditions, timeout,
                Context.NONE);
    }

    public Response<PageBlobItem> resizePageBlob(
            final long size, final BlobRequestConditions requestConditions, final Duration timeout) {
        return getPageBlobClient().resizeWithResponse(size, requestConditions, timeout, Context.NONE);
    }

    public Response<PageBlobItem> clearPagesBlob(
            final PageRange pageRange, final PageBlobRequestConditions pageBlobRequestConditions, final Duration timeout) {
        return getPageBlobClient().clearPagesWithResponse(pageRange, pageBlobRequestConditions, timeout, Context.NONE);
    }

    public Response<PageList> getPageBlobRanges(
            final BlobRange blobRange, final BlobRequestConditions requestConditions,
            final Duration timeout) {
        return getPageBlobClient().getPageRangesWithResponse(blobRange, requestConditions, timeout, Context.NONE);
    }

    public boolean pageBlobExists() {
        return getPageBlobClient().exists();
    }

    public String generateSas(final BlobServiceSasSignatureValues blobServiceSasSignatureValues) {
        return client.generateSas(blobServiceSasSignatureValues);
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
}
