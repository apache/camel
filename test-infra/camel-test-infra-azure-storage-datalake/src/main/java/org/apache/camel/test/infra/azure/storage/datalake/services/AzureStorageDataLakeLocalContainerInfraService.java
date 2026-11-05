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

package org.apache.camel.test.infra.azure.storage.datalake.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.AzureInfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;

/**
 * A local Data Lake Storage Gen2 service: Floci AZ serves the DFS API of the storage account on its Blob backend. It
 * answers the DFS API only to the requests for the host {@code <account>.dfs.core.windows.net}, with no account in the
 * path: {@code AzureStorageDataLakeClientUtils} builds a client that sends its requests that way.
 */
@InfraService(service = AzureInfraService.class,
              description = "Local Azure Data Lake Storage Gen2 service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "storage-datalake")
public class AzureStorageDataLakeLocalContainerInfraService extends FlociAzInfraService {
}
