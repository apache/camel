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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.azure.storage.common.ParallelTransferOptions;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.AccessTier;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.FileQueryError;
import com.azure.storage.file.datalake.models.FileQueryProgress;
import com.azure.storage.file.datalake.models.FileQuerySerialization;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.models.ListFileSystemsOptions;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathHttpHeaders;
import com.azure.storage.file.datalake.models.PublicAccessType;
import com.azure.storage.file.datalake.options.FileQueryOptions;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLakeConfigurationOptionsProxy {
    private static final Logger LOG = LoggerFactory.getLogger(DataLakeConfigurationOptionsProxy.class);

    private final DataLakeConfiguration configuration;

    public DataLakeConfigurationOptionsProxy(final DataLakeConfiguration configuration) {
        this.configuration = configuration;
    }

    public ListFileSystemsOptions getListFileSystemOptions(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getListFileSystemOptionsFromHeaders, () -> null, exchange);
    }

    public Duration getTimeout(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getTimoutFromHeaders, configuration::getTimeout, exchange);
    }

    public DataLakeOperationsDefinition getOperation(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getDataLakeOperationsDefinitionFromHeader, configuration::getOperation,
                exchange);
    }

    public String getFileSystemName(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getFileSystemNameFromHeaders, configuration::getFileSystemName, exchange);
    }

    public String getDirectoryName(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getDirectoryNameFromHeaders, configuration::getDirectoryName, exchange);
    }

    public String getFileName(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getFileNameFromHeaders, configuration::getFileName, exchange);
    }

    public ListPathsOptions getListPathsOptions(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getListPathsOptionsFromHeaders, () -> null, exchange);
    }

    public String getPath(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getPathFromHeaders, configuration::getPath, exchange);
    }

    public Boolean isRecursive(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getRecursiveFromHeaders, configuration::getRecursive, exchange);
    }

    public Integer getMaxResults(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getMaxResultsFromHeaders, configuration::getMaxResults, exchange);
    }

    public Boolean isUserPrincipalNameReturned(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getUserPrincipalNameReturnedFromHeaders,
                configuration::getUserPrincipalNameReturned, exchange);
    }

    public String getRegex(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getRegexFromHeaders, configuration::getRegex, exchange);
    }

    public String getFileDir(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getFileDirFromHeaders, configuration::getFileDir, exchange);
    }

    public Long getDownloadLinkExpiration(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getDownloadLinkExpirationFromHeaders,
                configuration::getDownloadLinkExpiration, exchange);
    }

    public Long getFileOffset(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getFileOffsetFromHeaders, configuration::getFileOffset, exchange);
    }

    public String getLeaseId(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getLeaseIdFromHeaders, () -> null, exchange);
    }

    public Boolean getFlush(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getFlushFromHeaders, () -> Boolean.FALSE, exchange);
    }

    public Boolean retainUnCommitedData(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getRetainUncommittedDataFromHeaders, configuration::getRetainUncommitedData,
                exchange);
    }

    public Boolean getClose(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getCloseFromHeaders, configuration::getClose, exchange);
    }

    public Long getPosition(final Exchange exchange) {
        LOG.info("Position: {}", configuration.getPosition());

        return getOption(DataLakeExchangeHeaders::getPositionFromHeaders, configuration::getPosition, exchange);
    }

    public ListPathsOptions getListPathOptions(final Exchange exchange) {
        ListPathsOptions pathsOptions = getListPathsOptions(exchange);

        if (ObjectHelper.isNotEmpty(pathsOptions)) {
            return pathsOptions;
        }

        pathsOptions = new ListPathsOptions();

        final String path = getPath(exchange);
        final Boolean recursive = isRecursive(exchange);
        final Integer maxResults = getMaxResults(exchange);
        final Boolean userPrincipalNameReturned = isUserPrincipalNameReturned(exchange);

        pathsOptions = pathsOptions.setPath(path).setMaxResults(maxResults).setRecursive(recursive)
                .setUserPrincipalNameReturned(userPrincipalNameReturned);
        return pathsOptions;
    }

    public String getExpression(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getExpressionFromHeaders, configuration::getExpression, exchange);
    }

    public FileQuerySerialization getInputSerialization(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getInputSerializationFromHeaders, () -> null, exchange);
    }

    public FileQuerySerialization getOutputSerialization(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getOutputSerializationFromHeaders, () -> null, exchange);
    }

    public Consumer<FileQueryError> getErrorConsuer(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getErrorConsumerFromHeaders, () -> null, exchange);
    }

    public Consumer<FileQueryProgress> getProgressConsuer(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getProgressConsumerFromHeaders, () -> null, exchange);
    }

    public FileQueryOptions getFileQueryOption(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getQueryOptionsFromHeaders, () -> null, exchange);
    }

    public String getPermission(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getPermissionFromHeaders, configuration::getPermission, exchange);
    }

    public String getUmask(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getUmaskFromHeaders, configuration::getUmask, exchange);
    }

    public Set<OpenOption> getOpenOptions(final Exchange exchange) {
        return getOption(DataLakeExchangeHeaders::getOpenOptionsFromHeaders, configuration::getOpenOptions, exchange);
    }

    public FileQueryOptions getFileQueryOptions(final Exchange exchange) {
        FileQueryOptions queryOptions = getFileQueryOption(exchange);

        if (ObjectHelper.isNotEmpty(queryOptions)) {
            return queryOptions;
        }
        final String expression = getExpression(exchange);
        final FileQuerySerialization inputSerialization = getInputSerialization(exchange);
        final FileQuerySerialization outputSerialization = getOutputSerialization(exchange);
        final Consumer<FileQueryError> errorConsumer = getErrorConsuer(exchange);
        final Consumer<FileQueryProgress> progressConsumer = getProgressConsuer(exchange);
        queryOptions = new FileQueryOptions(expression)
                .setInputSerialization(inputSerialization)
                .setOutputSerialization(outputSerialization)
                .setErrorConsumer(errorConsumer)
                .setProgressConsumer(progressConsumer);

        return queryOptions;

    }

    public Map<String, String> getMetadata(final Exchange exchange) {
        return DataLakeExchangeHeaders.getMedataFromHeaders(exchange);
    }

    public PublicAccessType getPublicAccessType(final Exchange exchange) {
        return DataLakeExchangeHeaders.getPublicAccessTypeFromHeaders(exchange);
    }

    public DataLakeRequestConditions getDataLakeRequestConditions(final Exchange exchange) {
        return DataLakeExchangeHeaders.getDataLakeRequestConditionsFromHeaders(exchange);
    }

    public AccessTier getAccessTier(final Exchange exchange) {
        return DataLakeExchangeHeaders.getAccessTierFromHeaders(exchange);
    }

    public byte[] getContentMd5(final Exchange exchange) {
        return DataLakeExchangeHeaders.getContendMd5FromHeaders(exchange);
    }

    public ParallelTransferOptions getParallelTransferOptions(final Exchange exchange) {
        return DataLakeExchangeHeaders.getParallelTransferOptionsFromHeaders(exchange);
    }

    public PathHttpHeaders getPathHttpHeaders(final Exchange exchange) {
        return DataLakeExchangeHeaders.getPathHttpHeadersFromHeaders(exchange);
    }

    public DataLakeFileClient getFileClient(final Exchange exchange) {
        return DataLakeExchangeHeaders.getFileClientFromHeaders(exchange);
    }

    public int getMaxRetryRequests() {
        return configuration.getMaxRetryRequests();
    }

    public FileRange getFileRange(final Exchange exchange) {
        final FileRange fileRange = DataLakeExchangeHeaders.getFileRangeFromHeaders(exchange);
        final Long fileOffset = configuration.getFileOffset();
        final Long dataCount = configuration.getDataCount();
        if (fileRange != null) {
            return fileRange;
        } else if (fileOffset != null && dataCount != null) {
            return new FileRange(configuration.getFileOffset(), configuration.getDataCount());
        } else {
            return null;
        }
    }

    public DataLakeConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(
            final Function<Exchange, R> exchangeFn, final Supplier<R> fallbackFn, final Exchange exchange) {
        if (ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(exchangeFn.apply(exchange))) {
            return fallbackFn.get();
        } else {
            return exchangeFn.apply(exchange);
        }
    }
}
