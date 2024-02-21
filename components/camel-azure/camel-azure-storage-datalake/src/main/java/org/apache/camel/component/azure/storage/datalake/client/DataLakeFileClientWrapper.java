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
package org.apache.camel.component.azure.storage.datalake.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.common.ParallelTransferOptions;
import com.azure.storage.file.datalake.DataLakeFileClient;
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
import org.apache.camel.util.SkipLastByteInputStream;

public class DataLakeFileClientWrapper {
    private final DataLakeFileClient client;

    public DataLakeFileClientWrapper(DataLakeFileClient client) {
        this.client = client;
    }

    public String getFileName() {
        return client.getFileName();
    }

    public String getFileUrl() {
        return client.getFileUrl();
    }

    public Long getFileSize() {
        return client.getProperties().getFileSize();
    }

    public Response<Void> delete(DataLakeRequestConditions accessConditions, Duration timeout) {
        return client.deleteWithResponse(accessConditions, timeout, Context.NONE);
    }

    public InputStream openInputStream() {
        String query = "SELECT * from BlobStorage";
        final InputStream sourceInputStream = client.openQueryInputStream(query);
        /* Workaround for https://github.com/Azure/azure-sdk-for-java/issues/19612 */
        return new SkipLastByteInputStream(sourceInputStream, (byte) '\n');
    }

    public Response<InputStream> openQueryInputStreamWithResponse(final FileQueryOptions queryOptions) {
        return client.openQueryInputStreamWithResponse(queryOptions);
    }

    public FileReadResponse downloadWithResponse(
            final OutputStream outputStream, final FileRange fileRange, final DownloadRetryOptions downloadRetryOptions,
            final DataLakeRequestConditions requestConditions, final boolean rangeGetContentMd5, final Duration timeout) {
        return client.readWithResponse(outputStream, fileRange, downloadRetryOptions, requestConditions, rangeGetContentMd5,
                timeout, Context.NONE);
    }

    public Response<PathProperties> downloadToFileWithResponse(
            final String filePath, final FileRange fileRange, final ParallelTransferOptions parallelTransferOptions,
            final DownloadRetryOptions downloadRetryOptions, final DataLakeRequestConditions requestConditions,
            final boolean rangeGetContentMd5, final Set<OpenOption> openOptions, final Duration timeout) {
        return client.readToFileWithResponse(filePath, fileRange, parallelTransferOptions, downloadRetryOptions,
                requestConditions,
                rangeGetContentMd5, openOptions, timeout, Context.NONE);
    }

    public Response<PathInfo> createWithResponse(
            final String permissions, final String umask, final PathHttpHeaders headers, final Map<String, String> metadata,
            final DataLakeRequestConditions requestConditions, final Duration timeout) {
        return client.createWithResponse(permissions, umask, headers, metadata, requestConditions, timeout, Context.NONE);
    }

    public Response<PathInfo> flushWithResponse(
            final long position, final Boolean retainUncommitedData, final Boolean close, final PathHttpHeaders headers,
            final DataLakeRequestConditions requestConditions, final Duration timeout) {
        return client.flushWithResponse(position, retainUncommitedData, close, headers, requestConditions, timeout,
                Context.NONE);
    }

    public Response<Void> appendWithResponse(
            final InputStream stream, final Long fileOffset, final Long length,
            final Duration timeout, final DataLakeFileAppendOptions options) {
        return client.appendWithResponse(stream, fileOffset, length, options, timeout, Context.NONE);
    }

    public Response<PathInfo> uploadWithResponse(final FileParallelUploadOptions uploadOptions, final Duration timeout) {
        return client.uploadWithResponse(uploadOptions, timeout, Context.NONE);
    }

    public Response<DataLakeFileClient> renameWithResponse(
            final String destFileSystem, final String destPath, final DataLakeRequestConditions sourceAccessConditions,
            final DataLakeRequestConditions destAccessConditions, final Duration timeout) {
        return client.renameWithResponse(destFileSystem, destPath, sourceAccessConditions, destAccessConditions, timeout,
                Context.NONE);
    }

    public void uploadFromFile(
            final String filePath, final ParallelTransferOptions parallelTransferOptions, final PathHttpHeaders headers,
            final Map<String, String> metadata, final DataLakeRequestConditions requestConditions,
            final Duration timeout) {
        client.uploadFromFile(filePath, parallelTransferOptions, headers, metadata, requestConditions, timeout);
    }

    public String generateSas(final DataLakeServiceSasSignatureValues dataLakeServiceSasSignatureValues) {
        return client.generateSas(dataLakeServiceSasSignatureValues);
    }

}
