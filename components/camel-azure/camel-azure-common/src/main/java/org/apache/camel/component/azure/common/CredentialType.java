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
package org.apache.camel.component.azure.common;

/**
 * Unified credential type enum shared across all Azure components.
 * <p>
 * Not all credential types are supported by every Azure component. The following table shows which credential types are
 * supported by each component:
 * <table>
 * <caption>Credential type support matrix</caption>
 * <tr>
 * <th>Credential Type</th>
 * <th>Components</th>
 * </tr>
 * <tr>
 * <td>{@link #AZURE_IDENTITY}</td>
 * <td>All Azure components</td>
 * </tr>
 * <tr>
 * <td>{@link #SHARED_ACCOUNT_KEY}</td>
 * <td>storage-blob, storage-queue, cosmosdb, files</td>
 * </tr>
 * <tr>
 * <td>{@link #SHARED_KEY_CREDENTIAL}</td>
 * <td>storage-blob, storage-datalake, storage-queue</td>
 * </tr>
 * <tr>
 * <td>{@link #AZURE_SAS}</td>
 * <td>storage-blob, storage-datalake, files</td>
 * </tr>
 * <tr>
 * <td>{@link #CONNECTION_STRING}</td>
 * <td>eventhubs, servicebus</td>
 * </tr>
 * <tr>
 * <td>{@link #CLIENT_SECRET}</td>
 * <td>storage-datalake, key-vault</td>
 * </tr>
 * <tr>
 * <td>{@link #TOKEN_CREDENTIAL}</td>
 * <td>eventhubs, servicebus, eventgrid, functions</td>
 * </tr>
 * <tr>
 * <td>{@link #ACCESS_KEY}</td>
 * <td>eventgrid</td>
 * </tr>
 * <tr>
 * <td>{@link #FUNCTION_KEY}</td>
 * <td>functions</td>
 * </tr>
 * <tr>
 * <td>{@link #SERVICE_CLIENT_INSTANCE}</td>
 * <td>storage-datalake</td>
 * </tr>
 * </table>
 *
 * @since 4.19.0
 */
public enum CredentialType {
    /**
     * Azure Storage shared key credential.
     *
     * @see com.azure.storage.common.StorageSharedKeyCredential
     */
    SHARED_KEY_CREDENTIAL,
    /**
     * Azure Storage shared account key (account name + account key pair).
     */
    SHARED_ACCOUNT_KEY,
    /**
     * Azure Identity (DefaultAzureCredential). Supports service principal with secret/certificate, managed identity,
     * environment variables, and other credential sources.
     *
     * @see com.azure.identity.DefaultAzureCredentialBuilder
     */
    AZURE_IDENTITY,
    /**
     * Azure Shared Access Signature (SAS) token.
     */
    AZURE_SAS,
    /**
     * Connection string-based authentication (e.g., for Service Bus, Event Hubs).
     */
    CONNECTION_STRING,
    /**
     * Client secret credential (client ID + tenant ID + client secret).
     */
    CLIENT_SECRET,
    /**
     * Pre-built token credential instance provided by the user.
     */
    TOKEN_CREDENTIAL,
    /**
     * Pre-built service client instance provided by the user.
     */
    SERVICE_CLIENT_INSTANCE,
    /**
     * Access key authentication (e.g., for Event Grid).
     */
    ACCESS_KEY,
    /**
     * Azure Function key authentication.
     */
    FUNCTION_KEY
}
