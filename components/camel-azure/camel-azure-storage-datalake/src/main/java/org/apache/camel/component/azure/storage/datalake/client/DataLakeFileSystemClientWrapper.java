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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.storage.file.datalake.models.PublicAccessType;
import org.apache.camel.util.ObjectHelper;

public class DataLakeFileSystemClientWrapper {

    private final DataLakeFileSystemClient client;

    public DataLakeFileSystemClientWrapper(final DataLakeFileSystemClient client) {
        this.client = client;
    }

    public HttpHeaders createFileSystem(
            final Map<String, String> metadata, final PublicAccessType publicAccessType, final Duration timeout) {
        return client.createWithResponse(metadata, publicAccessType, timeout, Context.NONE).getHeaders();
    }

    public HttpHeaders deleteFileSystem(final DataLakeRequestConditions dataLakeRequestConditions, final Duration timeout) {
        return client.deleteWithResponse(dataLakeRequestConditions, timeout, Context.NONE).getHeaders();
    }

    public List<PathItem> listPaths(final ListPathsOptions listPathsOptions, final Duration timeout) {
        return client.listPaths(listPathsOptions, timeout).stream().toList();
    }

    public DataLakeDirectoryClientWrapper getDataLakeDirectoryClientWrapper(final String directoryName) {
        if (!ObjectHelper.isEmpty(directoryName)) {
            return new DataLakeDirectoryClientWrapper(client.getDirectoryClient(directoryName));
        }
        throw new IllegalArgumentException("Cannot initialize a directory since no directory name was provided.");
    }

    public DataLakeFileClientWrapper getDataLakeFileClientWrapper(final String fileName) {
        if (!ObjectHelper.isEmpty(fileName)) {
            return new DataLakeFileClientWrapper(client.getFileClient(fileName));
        }
        throw new IllegalArgumentException("Cannot initialize a directory since no directory name was provided.");
    }
}
