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
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.file.datalake.DataLakeDirectoryClient;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.PathHttpHeaders;
import org.apache.camel.util.ObjectHelper;

public class DataLakeDirectoryClientWrapper {
    private final DataLakeDirectoryClient client;

    public DataLakeDirectoryClientWrapper(final DataLakeDirectoryClient directoryClient) {
        ObjectHelper.notNull(directoryClient, "directory client cant be null");
        client = directoryClient;
    }

    public Response<DataLakeFileClient> createFileWithResponse(
            final String fileName, final String permission, final String umask, final PathHttpHeaders headers,
            final Map<String, String> metadata, final DataLakeRequestConditions requestConditions, final Duration timeout) {
        return client.createFileWithResponse(fileName, permission, umask, headers, metadata, requestConditions, timeout,
                Context.NONE);
    }

    public Response<Void> deleteWithResponse(
            final Boolean recursive, final DataLakeRequestConditions requestConditions, final Duration timeout) {
        return client.deleteWithResponse(recursive, requestConditions, timeout, Context.NONE);
    }

    public DataLakeFileClientWrapper getDataLakeFileClientWrapper(final String fileName) {
        if (!ObjectHelper.isEmpty(fileName)) {
            return new DataLakeFileClientWrapper(client.getFileClient(fileName));
        }
        throw new IllegalArgumentException("Cannot initialize a file since no file name was provided.");
    }
}
