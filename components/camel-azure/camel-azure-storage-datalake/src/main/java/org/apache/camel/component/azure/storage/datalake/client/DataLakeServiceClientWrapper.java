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

import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.models.FileSystemItem;
import com.azure.storage.file.datalake.models.ListFileSystemsOptions;
import org.apache.camel.util.ObjectHelper;

public class DataLakeServiceClientWrapper {
    private final DataLakeServiceClient client;

    public DataLakeServiceClientWrapper(final DataLakeServiceClient client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public List<FileSystemItem> listFileSystems(final ListFileSystemsOptions options, final Duration timeout) {
        return client.listFileSystems(options, timeout).stream().toList();
    }

    public DataLakeFileSystemClientWrapper getDataLakeFileSystemClientWrapper(final String fileSystemName) {
        if (!ObjectHelper.isEmpty(fileSystemName)) {
            return new DataLakeFileSystemClientWrapper(client.getFileSystemClient(fileSystemName));
        }
        throw new IllegalArgumentException("cannot inititialize a fileSystem since no file system name was provided");
    }
}
