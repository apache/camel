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

package org.apache.camel.test.infra.azure.eventhubs.common;

/**
 * The system properties of the Event Hubs service: the local service sets them, the remote service reads them.
 */
public final class AzureEventHubsProperties {
    /**
     * The connection string of the event hub, with the name of the event hub in its {@code EntityPath}
     */
    public static final String CONNECTION_STRING = "azure.eventhubs.connection.string";
    /**
     * The endpoint of the Blob service that holds the checkpoints. A remote service defaults to
     * {@code https://<blob account name>.blob.core.windows.net}.
     */
    public static final String BLOB_ENDPOINT = "azure.eventhubs.blob.endpoint";
    public static final String BLOB_ACCOUNT_NAME = "azure.eventhubs.blob.account.name";
    public static final String BLOB_ACCESS_KEY = "azure.eventhubs.blob.access.key";

    private AzureEventHubsProperties() {

    }
}
