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
package org.apache.camel.component.azure.storage.datalake.operations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import com.azure.core.http.rest.Response;
import com.azure.storage.common.ParallelTransferOptions;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.AccessTier;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.DownloadRetryOptions;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.models.FileReadResponse;
import com.azure.storage.file.datalake.models.PathHttpHeaders;
import com.azure.storage.file.datalake.models.PathInfo;
import com.azure.storage.file.datalake.models.PathProperties;
import com.azure.storage.file.datalake.options.DataLakeFileAppendOptions;
import com.azure.storage.file.datalake.options.FileParallelUploadOptions;
import com.azure.storage.file.datalake.options.FileQueryOptions;
import com.azure.storage.file.datalake.sas.DataLakeServiceSasSignatureValues;
import com.azure.storage.file.datalake.sas.PathSasPermission;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.datalake.DataLakeExchangeHeaders;
import org.apache.camel.component.azure.storage.datalake.DataLakeUtils;
import org.apache.camel.component.azure.storage.datalake.FileCommonRequestOptions;
import org.apache.camel.component.azure.storage.datalake.FileStreamAndLength;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
import org.apache.camel.util.ObjectHelper;

public class DataLakeFileOperations {
    private final DataLakeFileClientWrapper client;
    private final DataLakeConfigurationOptionsProxy configurationProxy;

    public DataLakeFileOperations(final DataLakeConfiguration configuration, final DataLakeFileClientWrapper client) {
        this.client = client;
        configurationProxy = new DataLakeConfigurationOptionsProxy(configuration);
    }

    public DataLakeOperationResponse getFile(final Exchange exchange) throws IOException {
        final Message message = DataLakeUtils.getInMessage(exchange);
        final OutputStream outputStream;
        if (ObjectHelper.isEmpty(message)) {
            outputStream = null;
        } else {
            outputStream = message.getBody(OutputStream.class);
        }

        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        if (outputStream == null) {
            InputStream fileInputStream = fileClientWrapper.openInputStream();
            return new DataLakeOperationResponse(fileInputStream);
        }

        final FileRange fileRange = configurationProxy.getFileRange(exchange);
        final FileCommonRequestOptions fileCommonRequestOptions = getCommonRequestOptions(exchange);

        final DownloadRetryOptions downloadRetryOptions = getDownloadRetryOptions(configurationProxy);

        try {
            final FileReadResponse readResponse
                    = fileClientWrapper.downloadWithResponse(outputStream, fileRange, downloadRetryOptions,
                            fileCommonRequestOptions.getRequestConditions(), fileCommonRequestOptions.getContentMD5() != null,
                            fileCommonRequestOptions.getTimeout());

            final DataLakeExchangeHeaders dataLakeExchangeHeaders = DataLakeExchangeHeaders
                    .createDataLakeExchangeHeadersFromFileReadHeaders(readResponse.getDeserializedHeaders())
                    .httpHeaders(readResponse.getHeaders());

            return new DataLakeOperationResponse(outputStream, dataLakeExchangeHeaders.toMap());
        } finally {
            if (Boolean.TRUE.equals(configurationProxy.getConfiguration().getCloseStreamAfterRead())) {
                outputStream.close();
            }
        }
    }

    public DataLakeOperationResponse downloadToFile(final Exchange exchange) {
        final String fileDir = configurationProxy.getFileDir(exchange);
        if (ObjectHelper.isEmpty(fileDir)) {
            throw new IllegalArgumentException("to download a file, you need to specify the fileDir in the URI");
        }
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final File recieverFile = new File(fileDir, fileClientWrapper.getFileName());
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final FileRange fileRange = configurationProxy.getFileRange(exchange);
        final ParallelTransferOptions parallelTransferOptions = configurationProxy.getParallelTransferOptions(exchange);
        final DownloadRetryOptions downloadRetryOptions = getDownloadRetryOptions(configurationProxy);
        final Set<OpenOption> openOptions = configurationProxy.getOpenOptions(exchange);
        final Response<PathProperties> response
                = fileClientWrapper.downloadToFileWithResponse(recieverFile.toString(), fileRange,
                        parallelTransferOptions, downloadRetryOptions, commonRequestOptions.getRequestConditions(),
                        commonRequestOptions.getContentMD5() != null, openOptions, commonRequestOptions.getTimeout());
        final DataLakeExchangeHeaders exchangeHeaders
                = DataLakeExchangeHeaders.createDataLakeExchangeHeadersFromPathProperties(response.getValue())
                        .httpHeaders(response.getHeaders())
                        .fileName(recieverFile.toString());

        return new DataLakeOperationResponse(recieverFile, exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse downloadLink(final Exchange exchange) {
        final OffsetDateTime offsetDateTime = OffsetDateTime.now();
        final PathSasPermission sasPermission = new PathSasPermission().setReadPermission(true);
        final Long expirationMillis = configurationProxy.getDownloadLinkExpiration(exchange);

        OffsetDateTime offsetDateTimeToSet;
        if (expirationMillis != null) {
            offsetDateTimeToSet = offsetDateTime.plusSeconds(expirationMillis / 1000);
        } else {
            final long defaultExpirationTime = 60L * 60L;
            offsetDateTimeToSet = offsetDateTime.plusSeconds(defaultExpirationTime);
        }

        final DataLakeServiceSasSignatureValues serviceSasSignatureValues
                = new DataLakeServiceSasSignatureValues(offsetDateTimeToSet, sasPermission);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final String url = fileClientWrapper.getFileUrl() + "?" + fileClientWrapper.generateSas(serviceSasSignatureValues);
        final DataLakeExchangeHeaders headers = DataLakeExchangeHeaders.create().downloadLink(url);
        return new DataLakeOperationResponse(url, headers.toMap());
    }

    public DataLakeOperationResponse deleteFile(final Exchange exchange) {
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        Response<Void> response
                = fileClientWrapper.delete(commonRequestOptions.getRequestConditions(), commonRequestOptions.getTimeout());
        DataLakeExchangeHeaders exchangeHeaders = DataLakeExchangeHeaders.create();
        exchangeHeaders.httpHeaders(response.getHeaders());
        return new DataLakeOperationResponse(true, exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse appendToFile(final Exchange exchange) throws IOException {
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final FileStreamAndLength fileStreamAndLength = FileStreamAndLength.createFileStreamAndLengthFromExchangeBody(exchange);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final Long fileOffset;
        if (configurationProxy.getFileOffset(exchange) == null) {
            fileOffset = fileClientWrapper.getFileSize();
        } else {
            fileOffset = configurationProxy.getFileOffset(exchange);
        }
        final DataLakeFileAppendOptions options = new DataLakeFileAppendOptions();
        options.setContentHash(commonRequestOptions.getContentMD5());
        options.setLeaseId(commonRequestOptions.getLeaseId());
        options.setFlush(configurationProxy.getFlush(exchange));
        final Response<Void> response = fileClientWrapper.appendWithResponse(fileStreamAndLength.getInputStream(), fileOffset,
                fileStreamAndLength.getStreamLength(), commonRequestOptions.getTimeout(), options);
        DataLakeExchangeHeaders exchangeHeaders = DataLakeExchangeHeaders.create();
        exchangeHeaders.httpHeaders(response.getHeaders());
        return new DataLakeOperationResponse(true, exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse flushToFile(final Exchange exchange) {
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final Long position = configurationProxy.getPosition(exchange);
        final Boolean retainUncommitedData = configurationProxy.retainUnCommitedData(exchange);
        final Boolean close = configurationProxy.getClose(exchange);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final Response<PathInfo> response
                = fileClientWrapper.flushWithResponse(position + fileClientWrapper.getFileSize(), retainUncommitedData, close,
                        commonRequestOptions.getPathHttpHeaders(), commonRequestOptions.getRequestConditions(),
                        commonRequestOptions.getTimeout());
        DataLakeExchangeHeaders exchangeHeaders
                = DataLakeExchangeHeaders.createDataLakeExchangeHeadersFromPathInfo(response.getValue())
                        .httpHeaders(response.getHeaders());
        return new DataLakeOperationResponse(response.getValue(), exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse uploadFromFile(final Exchange exchange) {
        final String path = configurationProxy.getPath(exchange);
        final ParallelTransferOptions transferOptions = configurationProxy.getParallelTransferOptions(exchange);
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        fileClientWrapper.uploadFromFile(path, transferOptions, commonRequestOptions.getPathHttpHeaders(),
                commonRequestOptions.getMetadata(), commonRequestOptions.getRequestConditions(),
                commonRequestOptions.getTimeout());
        return new DataLakeOperationResponse(true);
    }

    public DataLakeOperationResponse upload(final Exchange exchange) throws IOException {
        final FileCommonRequestOptions commonRequestOptions = getCommonRequestOptions(exchange);
        final ParallelTransferOptions transferOptions = configurationProxy.getParallelTransferOptions(exchange);
        final FileStreamAndLength fileStreamAndLength = FileStreamAndLength.createFileStreamAndLengthFromExchangeBody(exchange);
        final String permission = configurationProxy.getPermission(exchange);
        final String umask = configurationProxy.getUmask(exchange);

        final FileParallelUploadOptions uploadOptions
                = new FileParallelUploadOptions(fileStreamAndLength.getInputStream())
                        .setHeaders(commonRequestOptions.getPathHttpHeaders()).setParallelTransferOptions(transferOptions)
                        .setMetadata(commonRequestOptions.getMetadata()).setPermissions(permission)
                        .setRequestConditions(commonRequestOptions.getRequestConditions())
                        .setRequestConditions(commonRequestOptions.getRequestConditions()).setUmask(umask);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final Response<PathInfo> response
                = fileClientWrapper.uploadWithResponse(uploadOptions, commonRequestOptions.getTimeout());
        DataLakeExchangeHeaders exchangeHeaders
                = DataLakeExchangeHeaders.createDataLakeExchangeHeadersFromPathInfo(response.getValue())
                        .httpHeaders(response.getHeaders());
        return new DataLakeOperationResponse(true, exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse openQueryInputStream(final Exchange exchange) {
        FileQueryOptions queryOptions = configurationProxy.getFileQueryOptions(exchange);
        final DataLakeFileClientWrapper fileClientWrapper = getFileClientWrapper(exchange);
        final Response<InputStream> response = fileClientWrapper.openQueryInputStreamWithResponse(queryOptions);
        DataLakeExchangeHeaders exchangeHeaders = DataLakeExchangeHeaders.create();
        exchangeHeaders.httpHeaders(response.getHeaders());
        return new DataLakeOperationResponse(response.getValue(), exchangeHeaders.toMap());
    }

    private FileCommonRequestOptions getCommonRequestOptions(final Exchange exchange) {
        final PathHttpHeaders httpHeaders = configurationProxy.getPathHttpHeaders(exchange);
        final Map<String, String> metadata = configurationProxy.getMetadata(exchange);
        final AccessTier accessTier = configurationProxy.getAccessTier(exchange);
        final DataLakeRequestConditions dataLakeRequestConditions = configurationProxy.getDataLakeRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final byte[] contentMD5 = configurationProxy.getContentMd5(exchange);

        return new FileCommonRequestOptions(httpHeaders, metadata, accessTier, dataLakeRequestConditions, contentMD5, timeout);
    }

    private DownloadRetryOptions getDownloadRetryOptions(final DataLakeConfigurationOptionsProxy proxy) {
        return new DownloadRetryOptions().setMaxRetryRequests(proxy.getMaxRetryRequests());
    }

    private DataLakeFileClientWrapper getFileClientWrapper(final Exchange exchange) {
        final DataLakeFileClient fileClient = configurationProxy.getFileClient(exchange);
        return null == fileClient ? client : new DataLakeFileClientWrapper(fileClient);
    }
}
