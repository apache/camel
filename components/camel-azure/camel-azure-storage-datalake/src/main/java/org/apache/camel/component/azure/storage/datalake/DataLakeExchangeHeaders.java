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
package org.apache.camel.component.azure.storage.datalake;

import java.nio.file.OpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.common.ParallelTransferOptions;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.AccessTier;
import com.azure.storage.file.datalake.models.ArchiveStatus;
import com.azure.storage.file.datalake.models.CopyStatusType;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.FileQueryError;
import com.azure.storage.file.datalake.models.FileQueryProgress;
import com.azure.storage.file.datalake.models.FileQuerySerialization;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.models.FileReadHeaders;
import com.azure.storage.file.datalake.models.LeaseDurationType;
import com.azure.storage.file.datalake.models.LeaseStateType;
import com.azure.storage.file.datalake.models.LeaseStatusType;
import com.azure.storage.file.datalake.models.ListFileSystemsOptions;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathHttpHeaders;
import com.azure.storage.file.datalake.models.PathInfo;
import com.azure.storage.file.datalake.models.PathProperties;
import com.azure.storage.file.datalake.models.PublicAccessType;
import com.azure.storage.file.datalake.options.FileQueryOptions;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

public class DataLakeExchangeHeaders {
    private final Map<String, Object> headers = new HashMap<>();

    public static DataLakeExchangeHeaders createDataLakeExchangeHeadersFromPathProperties(final PathProperties properties) {
        return new DataLakeExchangeHeaders()
                .accessTier(properties.getAccessTier())
                .accessTierChangrTime(properties.getAccessTierChangeTime())
                .archiveStatus(properties.getArchiveStatus())
                .cacheControl(properties.getCacheControl())
                .contentDisposition(properties.getContentDisposition())
                .contentEncoding(properties.getContentEncoding())
                .contentLanguage(properties.getContentLanguage())
                .contentMd5(properties.getContentMd5())
                .contentType(properties.getContentType())
                .copyCompletionTime(properties.getCopyCompletionTime())
                .copyId(properties.getCopyId())
                .copyProgress(properties.getCopyProgress())
                .copySource(properties.getCopySource())
                .copyStatus(properties.getCopyStatus())
                .copyStatusDescription(properties.getCopyStatusDescription())
                .creationTime(properties.getCreationTime())
                .encryptionKeySha256(properties.getEncryptionKeySha256())
                .eTag(properties.getETag())
                .fileSize(properties.getFileSize())
                .lastModified(properties.getLastModified())
                .leaseDuration(properties.getLeaseDuration())
                .leaseState(properties.getLeaseState())
                .leaseStatus(properties.getLeaseStatus())
                .metadata(properties.getMetadata())
                .incrementalCopy(properties.isIncrementalCopy())
                .serverEncrypted(properties.isServerEncrypted());
    }

    public static DataLakeExchangeHeaders createDataLakeExchangeHeadersFromPathInfo(final PathInfo pathInfo) {
        return new DataLakeExchangeHeaders()
                .eTag(pathInfo.getETag())
                .lastModified(pathInfo.getLastModified());
    }

    public static DataLakeExchangeHeaders createDataLakeExchangeHeadersFromFileReadHeaders(
            final FileReadHeaders fileReadHeaders) {
        return createDataLakeExchangeHeadersFromPathProperties(buildPathProperties(fileReadHeaders));
    }

    private static PathProperties buildPathProperties(final FileReadHeaders rh) {
        long contentLength;
        if (rh.getContentLength() == null) {
            contentLength = 0L;
        } else {
            contentLength = rh.getContentLength();
        }
        return new PathProperties(
                null, rh.getLastModified(), rh.getETag(), contentLength, rh.getContentType(),
                null, rh.getContentEncoding(), rh.getContentDisposition(), rh.getContentLanguage(),
                rh.getCacheControl(), rh.getLeaseStatus(), rh.getLeaseState(), rh.getLeaseDuration(), rh.getCopyId(),
                rh.getCopyStatus(), rh.getCopySource(), rh.getCopyProgress(), rh.getCopyCompletionTime(),
                rh.getCopyStatusDescription(),
                rh.isServerEncrypted(), null, null, null, rh.getEncryptionKeySha256(),
                null, rh.getMetadata());
    }

    public static ListFileSystemsOptions getListFileSystemOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.LIST_FILESYSTEMS_OPTIONS, ListFileSystemsOptions.class);
    }

    public static DataLakeExchangeHeaders create() {
        return new DataLakeExchangeHeaders();
    }

    public static Duration getTimoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.TIMEOUT, Duration.class);
    }

    public static DataLakeOperationsDefinition getDataLakeOperationsDefinitionFromHeader(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.DATALAKE_OPERATION, DataLakeOperationsDefinition.class);
    }

    public static String getFileSystemNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILESYSTEM_NAME, String.class);
    }

    public static String getDirectoryNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.DIRECTORY_NAME, String.class);
    }

    public static String getFileNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILE_NAME, String.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getMedataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.METADATA, Map.class);
    }

    public static PublicAccessType getPublicAccessTypeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PUBLIC_ACCESS_TYPE, PublicAccessType.class);
    }

    public static DataLakeRequestConditions getDataLakeRequestConditionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.DATALAKE_REQUEST_CONDITION, DataLakeRequestConditions.class);
    }

    public static ListPathsOptions getListPathsOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.LIST_PATH_OPTIONS, ListPathsOptions.class);
    }

    public static String getPathFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PATH, String.class);
    }

    public static Boolean getRecursiveFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.RECURSIVE, Boolean.class);
    }

    public static Integer getMaxResultsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.MAX_RESULTS, Integer.class);
    }

    public static Boolean getUserPrincipalNameReturnedFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.USER_PRINCIPAL_NAME_RETURNED, Boolean.class);
    }

    public static String getRegexFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.REGEX, String.class);
    }

    public static String getFileDirFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILE_DIR, String.class);
    }

    public static AccessTier getAccessTierFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.ACCESS_TIER, AccessTier.class);
    }

    public static byte[] getContendMd5FromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.CONTENT_MD5, byte[].class);
    }

    public static FileRange getFileRangeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILE_RANGE, FileRange.class);
    }

    public static ParallelTransferOptions getParallelTransferOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PARALLEL_TRANSFER_OPTIONS, ParallelTransferOptions.class);
    }

    public static Set<OpenOption> getOpenOptionsFromHeaders(final Exchange exchange) {
        return (Set<OpenOption>) getObjectFromHeaders(exchange, DataLakeConstants.OPEN_OPTIONS, Set.class);
    }

    public static Long getDownloadLinkExpirationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.DOWNLOAD_LINK_EXPIRATION, Long.class);
    }

    public static Long getFileOffsetFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILE_OFFSET, Long.class);
    }

    public static String getLeaseIdFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.LEASE_ID, String.class);
    }

    public static PathHttpHeaders getPathHttpHeadersFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PATH_HTTP_HEADERS, PathHttpHeaders.class);
    }

    public static Boolean getRetainUncommittedDataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.RETAIN_UNCOMMITED_DATA, Boolean.class);
    }

    public static Boolean getCloseFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.CLOSE, Boolean.class);
    }

    public static Long getPositionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.POSITION, Long.class);
    }

    public static String getExpressionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.EXPRESSION, String.class);
    }

    public static FileQuerySerialization getInputSerializationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.INPUT_SERIALIZATION, FileQuerySerialization.class);
    }

    public static FileQuerySerialization getOutputSerializationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.OUTPUT_SERIALIZATION, FileQuerySerialization.class);
    }

    public static Consumer<FileQueryError> getErrorConsumerFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.ERROR_CONSUMER, Consumer.class);
    }

    public static Consumer<FileQueryProgress> getProgressConsumerFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PROGRESS_CONSUMER, Consumer.class);
    }

    public static FileQueryOptions getQueryOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.QUERY_OPTIONS, FileQueryOptions.class);
    }

    public static String getPermissionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.PERMISSION, String.class);
    }

    public static String getUmaskFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.UMASK, String.class);
    }

    public static DataLakeFileClient getFileClientFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FILE_CLIENT, DataLakeFileClient.class);
    }

    public static Boolean getFlushFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DataLakeConstants.FLUSH, Boolean.class);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return ObjectHelper.isEmpty(exchange) ? null : exchange.getIn().getHeader(headerName, classType);
    }

    public Map<String, Object> toMap() {
        return headers;
    }

    public DataLakeExchangeHeaders httpHeaders(final HttpHeaders httpHeaders) {
        headers.put(DataLakeConstants.RAW_HTTP_HEADERS, httpHeaders);
        return this;
    }

    public DataLakeExchangeHeaders accessTier(final AccessTier accessTier) {
        headers.put(DataLakeConstants.ACCESS_TIER, accessTier);
        return this;
    }

    public DataLakeExchangeHeaders accessTierChangrTime(final OffsetDateTime accessTierChangeTime) {
        headers.put(DataLakeConstants.ACCESS_TIER_CHANGE_TIME, accessTierChangeTime);
        return this;
    }

    public DataLakeExchangeHeaders archiveStatus(final ArchiveStatus archiveStatus) {
        headers.put(DataLakeConstants.ARCHIVE_STATUS, archiveStatus);
        return this;
    }

    public DataLakeExchangeHeaders cacheControl(final String cacheControl) {
        headers.put(DataLakeConstants.CACHE_CONTROL, cacheControl);
        return this;
    }

    public DataLakeExchangeHeaders contentDisposition(final String contentDisposition) {
        headers.put(DataLakeConstants.CONTENT_DISPOSITION, contentDisposition);
        return this;
    }

    public DataLakeExchangeHeaders contentEncoding(final String contentEncoding) {
        headers.put(DataLakeConstants.CONTENT_ENCODING, contentEncoding);
        return this;
    }

    public DataLakeExchangeHeaders contentLanguage(final String contentLanguage) {
        headers.put(DataLakeConstants.CONTENT_LANGUAGE, contentLanguage);
        return this;
    }

    public DataLakeExchangeHeaders contentMd5(final byte[] contentMd5) {
        headers.put(DataLakeConstants.CONTENT_MD5, contentMd5);
        return this;
    }

    public DataLakeExchangeHeaders contentType(final String contentType) {
        headers.put(DataLakeConstants.CONTENT_TYPE, contentType);
        return this;
    }

    public DataLakeExchangeHeaders copyCompletionTime(final OffsetDateTime copyCompletionTime) {
        headers.put(DataLakeConstants.COPY_COMPLETION_TIME, copyCompletionTime);
        return this;
    }

    public DataLakeExchangeHeaders copyId(final String copyId) {
        headers.put(DataLakeConstants.COPY_ID, copyId);
        return this;
    }

    public DataLakeExchangeHeaders copyProgress(final String copyProgress) {
        headers.put(DataLakeConstants.COPY_PROGRESS, copyProgress);
        return this;
    }

    public DataLakeExchangeHeaders copySource(final String copySource) {
        headers.put(DataLakeConstants.COPY_SOURCE, copySource);
        return this;
    }

    public DataLakeExchangeHeaders copyStatus(final CopyStatusType copyStatus) {
        headers.put(DataLakeConstants.COPY_STATUS, copyStatus);
        return this;
    }

    public DataLakeExchangeHeaders copyStatusDescription(final String copyStatusDescription) {
        headers.put(DataLakeConstants.COPY_STATUS_DESCRIPTION, copyStatusDescription);
        return this;
    }

    public DataLakeExchangeHeaders creationTime(final OffsetDateTime creationTime) {
        headers.put(DataLakeConstants.CREATION_TIME, creationTime);
        return this;
    }

    public DataLakeExchangeHeaders encryptionKeySha256(final String key) {
        headers.put(DataLakeConstants.ENCRYPTION_KEY_SHA_256, key);
        return this;
    }

    public DataLakeExchangeHeaders eTag(final String eTag) {
        headers.put(DataLakeConstants.E_TAG, eTag);
        return this;
    }

    public DataLakeExchangeHeaders fileSize(final Long fileSize) {
        headers.put(DataLakeConstants.FILE_SIZE, fileSize);
        return this;
    }

    public DataLakeExchangeHeaders lastModified(final OffsetDateTime lastModified) {
        headers.put(DataLakeConstants.LAST_MODIFIED, lastModified);
        if (lastModified != null) {
            long ts = lastModified.toEpochSecond() * 1000;
            headers.put(Exchange.MESSAGE_TIMESTAMP, ts);
        }
        return this;
    }

    public DataLakeExchangeHeaders leaseDuration(final LeaseDurationType leaseDuration) {
        headers.put(DataLakeConstants.LEASE_DURATION, leaseDuration);
        return this;
    }

    public DataLakeExchangeHeaders leaseState(final LeaseStateType leaseState) {
        headers.put(DataLakeConstants.LEASE_STATE, leaseState);
        return this;
    }

    public DataLakeExchangeHeaders leaseStatus(final LeaseStatusType leaseStatus) {
        headers.put(DataLakeConstants.LEASE_STATUS, leaseStatus);
        return this;
    }

    public DataLakeExchangeHeaders metadata(final Map<String, String> metadata) {
        headers.put(DataLakeConstants.METADATA, metadata);
        return this;
    }

    public DataLakeExchangeHeaders incrementalCopy(final Boolean incrementalCopy) {
        headers.put(DataLakeConstants.INCREMENTAL_COPY, incrementalCopy);
        return this;
    }

    public DataLakeExchangeHeaders serverEncrypted(final Boolean serverEncrypted) {
        headers.put(DataLakeConstants.SERVER_ENCRYPTED, serverEncrypted);
        return this;
    }

    public DataLakeExchangeHeaders fileName(final String fileName) {
        headers.put(DataLakeConstants.FILE_NAME, fileName);
        return this;
    }

    public DataLakeExchangeHeaders downloadLink(final String downloadLink) {
        headers.put(DataLakeConstants.DOWNLOAD_LINK, downloadLink);
        return this;
    }

    public DataLakeExchangeHeaders fileClient(final DataLakeFileClient fileClient) {
        headers.put(DataLakeConstants.FILE_CLIENT, fileClient);
        return this;
    }
}
