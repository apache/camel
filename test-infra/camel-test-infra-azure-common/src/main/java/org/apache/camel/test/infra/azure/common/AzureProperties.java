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

package org.apache.camel.test.infra.azure.common;

public final class AzureProperties {
    /**
     * The Floci AZ image of the local Azure services
     */
    public static final String AZURE_CONTAINER = "azure.container";
    /**
     * The Azurite image of the local Blob service. Blob and Queue keep a key each, so the version of the test-infra
     * metadata is matched by their implementation alias and not by the shared {@code azure} alias of Floci AZ.
     */
    public static final String AZURE_STORAGE_BLOB_CONTAINER = "azure.storage.blob.container";
    /**
     * The Azurite image of the local Queue service
     */
    public static final String AZURE_STORAGE_QUEUE_CONTAINER = "azure.storage.queue.container";

    private AzureProperties() {

    }
}
