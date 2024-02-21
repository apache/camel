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
package org.apache.camel.component.azure.storage.blob;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.azure.core.util.Context;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.ListBlobContainersOptions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link BlobConfigurationOptionsProxy} and {@link BlobExchangeHeaders}. Ideally this is responsible
 * to obtain the correct configurations options either from configs or exchange headers
 */
public class BlobConfigurationOptionsProxy {

    private final BlobConfiguration configuration;

    public BlobConfigurationOptionsProxy(final BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    public ListBlobContainersOptions getListBlobContainersOptions(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getListBlobContainersOptionsFromHeaders, () -> null, exchange);
    }

    public Duration getTimeout(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getTimeoutFromHeaders, configuration::getTimeout, exchange);
    }

    public ListBlobsOptions getListBlobsOptions(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getListBlobsOptionsFromHeaders, () -> null, exchange);
    }

    public BlobListDetails getBlobListDetails(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlobListDetailsFromHeaders, BlobListDetails::new, exchange);
    }

    public String getPrefix(final Exchange exchange) {
        //if regex is set, prefix will not take effect
        if (ObjectHelper.isNotEmpty(getRegex(exchange))) {
            return null;
        }
        return getOption(BlobExchangeHeaders::getPrefixFromHeaders, configuration::getPrefix, exchange);
    }

    public String getRegex(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getRegexFromHeaders, configuration::getRegex, exchange);
    }

    public Integer getMaxResultsPerPage(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getMaxResultsPerPageFromHeaders, configuration::getMaxResultsPerPage, exchange);
    }

    public ListBlobsOptions getListBlobOptions(final Exchange exchange) {
        ListBlobsOptions blobsOptions = getListBlobsOptions(exchange);

        if (blobsOptions == null) {
            blobsOptions = new ListBlobsOptions();
        }

        final BlobListDetails blobListDetails = getBlobListDetails(exchange);
        final String prefix = getPrefix(exchange);
        final Integer maxResultsPerPage = getMaxResultsPerPage(exchange);

        blobsOptions.setDetails(blobListDetails);
        blobsOptions.setMaxResultsPerPage(maxResultsPerPage);
        blobsOptions.setPrefix(prefix);

        return blobsOptions;
    }

    public Map<String, String> getMetadata(final Exchange exchange) {
        return BlobExchangeHeaders.getMetadataFromHeaders(exchange);
    }

    public PublicAccessType getPublicAccessType(final Exchange exchange) {
        return BlobExchangeHeaders.getPublicAccessTypeFromHeaders(exchange);
    }

    public BlobRequestConditions getBlobRequestConditions(final Exchange exchange) {
        return BlobExchangeHeaders.getBlobRequestConditionsFromHeaders(exchange);
    }

    public PageRange getPageRange(final Exchange exchange) {
        return BlobExchangeHeaders.getPageRangeFromHeaders(exchange);
    }

    public BlobRange getBlobRange(final Exchange exchange) {
        if (configuration.getBlobType() == BlobType.pageblob) {
            final PageRange pageRange = getPageRange(exchange);
            if (pageRange != null) {
                final long blobOffset = pageRange.getStart();
                final long dataCount = pageRange.getEnd() - pageRange.getStart();

                return new BlobRange(blobOffset, dataCount);
            }
        }
        return new BlobRange(configuration.getBlobOffset(), configuration.getDataCount());
    }

    public BlobHttpHeaders getBlobHttpHeaders(final Exchange exchange) {
        return BlobExchangeHeaders.getBlobHttpHeadersFromHeaders(exchange);
    }

    public AccessTier getAccessTier(final Exchange exchange) {
        return BlobExchangeHeaders.getAccessTierFromHeaders(exchange);
    }

    public byte[] getContentMd5(final Exchange exchange) {
        return BlobExchangeHeaders.getContentMd5FromHeaders(exchange);
    }

    public String getFileDir(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getFileDirFromHeaders, configuration::getFileDir, exchange);
    }

    public ParallelTransferOptions getParallelTransferOptions(final Exchange exchange) {
        return BlobExchangeHeaders.getParallelTransferOptionsFromHeaders(exchange);
    }

    public DeleteSnapshotsOptionType getDeleteSnapshotsOptionType(final Exchange exchange) {
        return BlobExchangeHeaders.getDeleteSnapshotsOptionTypeFromHeaders(exchange);
    }

    public Long getDownloadLinkExpiration(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getDownloadLinkExpirationFromHeaders, configuration::getDownloadLinkExpiration,
                exchange);
    }

    public boolean isCommitBlockListLater(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getCommitBlockListFlagFromHeaders, configuration::isCommitBlockListLater,
                exchange);
    }

    public BlockListType getBlockListType(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlockListTypeFromHeaders, configuration::getBlockListType, exchange);
    }

    public boolean isCreateAppendBlob(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getCreateAppendBlobFlagFromHeaders, configuration::isCreateAppendBlob, exchange);
    }

    public Long getPageBlobSize(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getPageBlobSize, configuration::getPageBlobSize, exchange);
    }

    public Long getBlobSequenceNumber(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlobSequenceNumberFromHeaders, configuration::getBlobSequenceNumber, exchange);
    }

    public boolean isCreatePageBlob(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getCreatePageBlobFlagFromHeaders, configuration::isCreatePageBlob, exchange);
    }

    public String getBlobName(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlobNameFromHeaders, configuration::getBlobName, exchange);
    }

    public String getContainerName(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlobContainerNameFromHeaders, configuration::getContainerName, exchange);
    }

    public BlobOperationsDefinition getOperation(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getBlobOperationsDefinitionFromHeaders, configuration::getOperation, exchange);
    }

    public int getMaxRetryRequests() {
        return configuration.getMaxRetryRequests();
    }

    public OffsetDateTime getChangeFeedStartTime(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getChangeFeedStartTimeFromHeaders, configuration::getChangeFeedStartTime,
                exchange);
    }

    public OffsetDateTime getChangeFeedEndTime(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getChangeFeedEndTimeFromHeaders, configuration::getChangeFeedEndTime, exchange);
    }

    public Context getChangeFeedContext(final Exchange exchange) {
        return getOption(BlobExchangeHeaders::getChangeFeedContextFromHeaders, configuration::getChangeFeedContext, exchange);
    }

    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(final Function<Exchange, R> exchangeFn, final Supplier<R> fallbackFn, final Exchange exchange) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(exchangeFn.apply(exchange))
                ? fallbackFn.get()
                : exchangeFn.apply(exchange);
    }
}
