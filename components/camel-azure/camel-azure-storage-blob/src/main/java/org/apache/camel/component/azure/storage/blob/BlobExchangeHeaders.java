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
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.ArchiveStatus;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobType;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.CopyStatusType;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.models.LeaseDurationType;
import com.azure.storage.blob.models.LeaseStateType;
import com.azure.storage.blob.models.LeaseStatusType;
import com.azure.storage.blob.models.ListBlobContainersOptions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PageBlobItem;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

public class BlobExchangeHeaders {

    private final Map<String, Object> headers = new HashMap<>();

    public static BlobExchangeHeaders createBlobExchangeHeadersFromBlobProperties(final BlobProperties properties) {
        return new BlobExchangeHeaders()
                .accessTierHeader(properties.getAccessTier())
                .accessTierChangeTime(properties.getAccessTierChangeTime())
                .archiveStatus(properties.getArchiveStatus())
                .blobSequenceNumber(properties.getBlobSequenceNumber())
                .blobSize(properties.getBlobSize())
                .blobType(properties.getBlobType())
                .cacheControl(properties.getCacheControl())
                .committedBlockCount(properties.getCommittedBlockCount())
                .contentDisposition(properties.getContentDisposition())
                .contentEncoding(properties.getContentEncoding())
                .contentLanguage(properties.getContentLanguage())
                .contentMd5(properties.getContentMd5())
                .contentType(properties.getContentType())
                .copyCompletionTime(properties.getCopyCompletionTime())
                .copyDestinationSnapshot(properties.getCopyDestinationSnapshot())
                .copyId(properties.getCopyId())
                .copyProgress(properties.getCopyProgress())
                .copySource(properties.getCopySource())
                .copyStatus(properties.getCopyStatus())
                .copyStatusDescription(properties.getCopyStatusDescription())
                .creationTime(properties.getCreationTime())
                .encryptionKeySha256(properties.getEncryptionKeySha256())
                .eTag(properties.getETag())
                .isAccessTierInferred(properties.isAccessTierInferred())
                .isIncrementalCopy(properties.isIncrementalCopy())
                .isServerEncrypted(properties.isServerEncrypted())
                .lastModified(properties.getLastModified())
                .leaseDuration(properties.getLeaseDuration())
                .leaseState(properties.getLeaseState())
                .leaseStatus(properties.getLeaseStatus())
                .metadata(properties.getMetadata());
    }

    public static BlobExchangeHeaders createBlobExchangeHeadersFromBlobDownloadHeaders(
            final BlobDownloadHeaders blobDownloadHeaders) {
        return createBlobExchangeHeadersFromBlobProperties(buildBlobProperties(blobDownloadHeaders));
    }

    public static BlobExchangeHeaders createBlobExchangeHeadersFromBlockBlobItem(final BlockBlobItem blockBlobItem) {
        return new BlobExchangeHeaders()
                .eTag(blockBlobItem.getETag())
                .lastModified(blockBlobItem.getLastModified())
                .contentMd5(blockBlobItem.getContentMd5())
                .isServerEncrypted(blockBlobItem.isServerEncrypted())
                .encryptionKeySha256(blockBlobItem.getEncryptionKeySha256())
                .encryptionScope(blockBlobItem.getEncryptionScope());
    }

    public static BlobExchangeHeaders createBlobExchangeHeadersFromAppendBlobItem(final AppendBlobItem appendBlobItem) {
        return new BlobExchangeHeaders()
                .eTag(appendBlobItem.getETag())
                .lastModified(appendBlobItem.getLastModified())
                .contentMd5(appendBlobItem.getContentMd5())
                .isServerEncrypted(appendBlobItem.isServerEncrypted())
                .encryptionKeySha256(appendBlobItem.getEncryptionKeySha256())
                .encryptionScope(appendBlobItem.getEncryptionScope())
                .appendOffset(appendBlobItem.getBlobAppendOffset())
                .committedBlockCount(appendBlobItem.getBlobCommittedBlockCount());
    }

    public static BlobExchangeHeaders createBlobExchangeHeadersFromPageBlobItem(final PageBlobItem pageBlobItem) {
        return new BlobExchangeHeaders()
                .eTag(pageBlobItem.getETag())
                .lastModified(pageBlobItem.getLastModified())
                .contentMd5(pageBlobItem.getContentMd5())
                .isServerEncrypted(pageBlobItem.isServerEncrypted())
                .encryptionScope(pageBlobItem.getEncryptionScope())
                .blobSequenceNumber(pageBlobItem.getBlobSequenceNumber());
    }

    public static BlobExchangeHeaders create() {
        return new BlobExchangeHeaders();
    }

    private static BlobProperties buildBlobProperties(final BlobDownloadHeaders hd) {
        return new BlobProperties(
                null, hd.getLastModified(), hd.getETag(),
                hd.getContentLength() == null ? 0 : hd.getContentLength(), hd.getContentType(), null,
                hd.getContentEncoding(), hd.getContentDisposition(), hd.getContentLanguage(), hd.getCacheControl(),
                hd.getBlobSequenceNumber(), hd.getBlobType(), hd.getLeaseStatus(), hd.getLeaseState(),
                hd.getLeaseDuration(), hd.getCopyId(), hd.getCopyStatus(), hd.getCopySource(), hd.getCopyProgress(),
                hd.getCopyCompletionTime(), hd.getCopyStatusDescription(), hd.isServerEncrypted(),
                null, null, null, null, null, hd.getEncryptionKeySha256(), null, hd.getMetadata(),
                hd.getBlobCommittedBlockCount());
    }

    public static Duration getTimeoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.TIMEOUT, Duration.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getMetadataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.METADATA, Map.class);
    }

    public static PublicAccessType getPublicAccessTypeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.PUBLIC_ACCESS_TYPE, PublicAccessType.class);
    }

    public static BlobRequestConditions getBlobRequestConditionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_REQUEST_CONDITION, BlobRequestConditions.class);
    }

    public static BlobListDetails getBlobListDetailsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_LIST_DETAILS, BlobListDetails.class);
    }

    public static ListBlobsOptions getListBlobsOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.LIST_BLOB_OPTIONS, ListBlobsOptions.class);
    }

    public static String getPrefixFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.PREFIX, String.class);
    }

    public static String getRegexFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.REGEX, String.class);
    }

    public static Integer getMaxResultsPerPageFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.MAX_RESULTS_PER_PAGE, Integer.class);
    }

    public static BlobHttpHeaders getBlobHttpHeadersFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_HTTP_HEADERS, BlobHttpHeaders.class);
    }

    public static AccessTier getAccessTierFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.ACCESS_TIER, AccessTier.class);
    }

    public static byte[] getContentMd5FromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CONTENT_MD5, byte[].class);
    }

    public static PageRange getPageRangeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.PAGE_BLOB_RANGE, PageRange.class);
    }

    public static Boolean getCommitBlockListFlagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.COMMIT_BLOCK_LIST_LATER, Boolean.class);
    }

    public static Boolean getCreateAppendBlobFlagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CREATE_APPEND_BLOB, Boolean.class);
    }

    public static Boolean getCreatePageBlobFlagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CREATE_PAGE_BLOB, Boolean.class);
    }

    public static BlockListType getBlockListTypeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOCK_LIST_TYPE, BlockListType.class);
    }

    public static Long getPageBlobSize(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.PAGE_BLOB_SIZE, Long.class);
    }

    public static Long getBlobSequenceNumberFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_SEQUENCE_NUMBER, Long.class);
    }

    public static DeleteSnapshotsOptionType getDeleteSnapshotsOptionTypeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.DELETE_SNAPSHOT_OPTION_TYPE, DeleteSnapshotsOptionType.class);
    }

    public static ListBlobContainersOptions getListBlobContainersOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.LIST_BLOB_CONTAINERS_OPTIONS, ListBlobContainersOptions.class);
    }

    public static ParallelTransferOptions getParallelTransferOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.PARALLEL_TRANSFER_OPTIONS, ParallelTransferOptions.class);
    }

    public static String getFileDirFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.FILE_DIR, String.class);
    }

    public static Long getDownloadLinkExpirationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.DOWNLOAD_LINK_EXPIRATION, Long.class);
    }

    public static String getBlobNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_NAME, String.class);
    }

    public static String getBlobContainerNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_CONTAINER_NAME, String.class);
    }

    public static BlobOperationsDefinition getBlobOperationsDefinitionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.BLOB_OPERATION, BlobOperationsDefinition.class);
    }

    public static OffsetDateTime getChangeFeedStartTimeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CHANGE_FEED_START_TIME, OffsetDateTime.class);
    }

    public static OffsetDateTime getChangeFeedEndTimeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CHANGE_FEED_END_TIME, OffsetDateTime.class);
    }

    public static Context getChangeFeedContextFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, BlobConstants.CHANGE_FEED_CONTEXT, Context.class);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return ObjectHelper.isEmpty(exchange) ? null : exchange.getIn().getHeader(headerName, classType);
    }

    public Map<String, Object> toMap() {
        return headers;
    }

    public BlobExchangeHeaders accessTierHeader(final AccessTier accessTier) {
        headers.put(BlobConstants.ACCESS_TIER, accessTier);
        return this;
    }

    public BlobExchangeHeaders accessTierChangeTime(final OffsetDateTime accessTierChangeTime) {
        headers.put(BlobConstants.ACCESS_TIER_CHANGE_TIME, accessTierChangeTime);
        return this;
    }

    public BlobExchangeHeaders archiveStatus(final ArchiveStatus archiveStatus) {
        headers.put(BlobConstants.ARCHIVE_STATUS, archiveStatus);
        return this;
    }

    public BlobExchangeHeaders creationTime(final OffsetDateTime creationTime) {
        headers.put(BlobConstants.CREATION_TIME, creationTime);
        return this;
    }

    public BlobExchangeHeaders blobSequenceNumber(final Long sequence) {
        headers.put(BlobConstants.BLOB_SEQUENCE_NUMBER, sequence);
        return this;
    }

    public BlobExchangeHeaders blobSize(final long size) {
        headers.put(BlobConstants.BLOB_SIZE, size);
        return this;
    }

    public BlobExchangeHeaders blobType(final BlobType blobType) {
        headers.put(BlobConstants.BLOB_TYPE, blobType);
        return this;
    }

    public BlobExchangeHeaders cacheControl(final String cache) {
        headers.put(BlobConstants.CACHE_CONTROL, cache);
        return this;
    }

    public BlobExchangeHeaders committedBlockCount(final Integer count) {
        headers.put(BlobConstants.COMMITTED_BLOCK_COUNT, count);
        return this;
    }

    public BlobExchangeHeaders contentDisposition(final String content) {
        headers.put(BlobConstants.CONTENT_DISPOSITION, content);
        return this;
    }

    public BlobExchangeHeaders contentEncoding(final String contentEncoding) {
        headers.put(BlobConstants.CONTENT_ENCODING, contentEncoding);
        return this;
    }

    public BlobExchangeHeaders contentLanguage(final String contentLanguageHeader) {
        headers.put(BlobConstants.CONTENT_LANGUAGE, contentLanguageHeader);
        return this;
    }

    public BlobExchangeHeaders contentMd5(final byte[] md5) {
        headers.put(BlobConstants.CONTENT_MD5, md5);
        return this;
    }

    public BlobExchangeHeaders contentType(final String type) {
        headers.put(BlobConstants.CONTENT_TYPE, type);
        return this;
    }

    public BlobExchangeHeaders copyCompletionTime(final OffsetDateTime offsetDateTime) {
        headers.put(BlobConstants.COPY_COMPILATION_TIME, offsetDateTime);
        return this;
    }

    public BlobExchangeHeaders copyDestinationSnapshot(final String copyDest) {
        headers.put(BlobConstants.COPY_DESTINATION_SNAPSHOT, copyDest);
        return this;
    }

    public BlobExchangeHeaders copyId(final String copyId) {
        headers.put(BlobConstants.COPY_ID, copyId);
        return this;
    }

    public BlobExchangeHeaders copyProgress(final String copyProg) {
        headers.put(BlobConstants.COPY_PROGRESS, copyProg);
        return this;
    }

    public BlobExchangeHeaders copySource(final String copySource) {
        headers.put(BlobConstants.COPY_SOURCE, copySource);
        return this;
    }

    public BlobExchangeHeaders copyStatus(final CopyStatusType copyStatusType) {
        headers.put(BlobConstants.COPY_STATUS, copyStatusType);
        return this;
    }

    public BlobExchangeHeaders copyStatusDescription(final String copyStatusDes) {
        headers.put(BlobConstants.COPY_STATUS_DESCRIPTION, copyStatusDes);
        return this;
    }

    public BlobExchangeHeaders encryptionKeySha256(final String encryptionKeySha256) {
        headers.put(BlobConstants.ENCRYPTION_KEY_SHA_256, encryptionKeySha256);
        return this;
    }

    public BlobExchangeHeaders encryptionScope(final String scope) {
        headers.put(BlobConstants.ENCRYPTION_SCOPE, scope);
        return this;
    }

    public BlobExchangeHeaders eTag(final String eTag) {
        headers.put(BlobConstants.E_TAG, eTag);
        return this;
    }

    public BlobExchangeHeaders isAccessTierInferred(final Boolean isAccess) {
        headers.put(BlobConstants.IS_ACCESS_TIER_INFRRRED, isAccess);
        return this;
    }

    public BlobExchangeHeaders isIncrementalCopy(final Boolean isIncr) {
        headers.put(BlobConstants.IS_INCREMENTAL_COPY, isIncr);
        return this;
    }

    public BlobExchangeHeaders isServerEncrypted(final Boolean isServerEncrypted) {
        headers.put(BlobConstants.IS_SERVER_ENCRYPTED, isServerEncrypted);
        return this;
    }

    public BlobExchangeHeaders lastModified(final OffsetDateTime offsetDateTime) {
        headers.put(BlobConstants.LAST_MODIFIED, offsetDateTime);
        if (offsetDateTime != null) {
            long ts = offsetDateTime.toEpochSecond() * 1000;
            headers.put(Exchange.MESSAGE_TIMESTAMP, ts);
        }
        return this;
    }

    public BlobExchangeHeaders leaseDuration(final LeaseDurationType leaseDurationType) {
        headers.put(BlobConstants.LEASE_DURATION, leaseDurationType);
        return this;
    }

    public BlobExchangeHeaders leaseState(final LeaseStateType leaseStateType) {
        headers.put(BlobConstants.LEASE_STATE, leaseStateType);
        return this;
    }

    public BlobExchangeHeaders leaseStatus(final LeaseStatusType leaseStatusType) {
        headers.put(BlobConstants.LEASE_STATUS, leaseStatusType);
        return this;
    }

    public BlobExchangeHeaders metadata(final Map<String, String> metadata) {
        headers.put(BlobConstants.METADATA, metadata);
        return this;
    }

    public BlobExchangeHeaders appendOffset(final String offset) {
        headers.put(BlobConstants.APPEND_OFFSET, offset);
        return this;
    }

    public BlobExchangeHeaders fileName(final String fileName) {
        headers.put(BlobConstants.FILE_NAME, fileName);
        return this;
    }

    public BlobExchangeHeaders downloadLink(final String downloadLink) {
        headers.put(BlobConstants.DOWNLOAD_LINK, downloadLink);
        return this;
    }

    public BlobExchangeHeaders httpHeaders(final HttpHeaders httpHeaders) {
        headers.put(BlobConstants.RAW_HTTP_HEADERS, httpHeaders);
        return this;
    }
}
