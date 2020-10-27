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

import java.time.Duration;

import com.azure.storage.file.datalake.models.ListFileSystemsOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeServiceClientWrapper;

public class DataLakeServiceOperations {
    private final DataLakeServiceClientWrapper client;
    private final DataLakeConfigurationOptionsProxy configurationProxy;

    public DataLakeServiceOperations(final DataLakeConfiguration configuration, final DataLakeServiceClientWrapper client) {
        this.client = client;
        configurationProxy = new DataLakeConfigurationOptionsProxy(configuration);
    }

    public DataLakeOperationResponse listFileSystems(final Exchange exchange) {
        final ListFileSystemsOptions listFileSystemsOptions = configurationProxy.getListFileSystemOptions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);

        return new DataLakeOperationResponse(client.listFileSystems(listFileSystemsOptions, timeout));
    }
}
