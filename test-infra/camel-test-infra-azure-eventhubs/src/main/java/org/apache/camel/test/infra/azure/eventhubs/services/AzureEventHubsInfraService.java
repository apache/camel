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

package org.apache.camel.test.infra.azure.eventhubs.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Test infra service for Azure Event Hubs: an event hub, and the Blob storage account where an event processor keeps
 * its checkpoints.
 */
public interface AzureEventHubsInfraService extends InfrastructureService {

    /**
     * The connection string of the event hub, with the name of the event hub in its {@code EntityPath}, as the
     * camel-azure-eventhubs connectionString option expects it.
     */
    String connectionString();

    /**
     * The endpoint of the Blob service of the storage account that holds the checkpoints, for example
     * {@code https://myaccount.blob.core.windows.net}.
     */
    String blobEndpoint();

    String blobAccountName();

    String blobAccessKey();
}
