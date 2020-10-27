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

package org.apache.camel.test.infra.azure.storage.datalake.clients;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import org.apache.camel.test.infra.azure.common.AzureConfigs;

public final class AzureStorageDataLakeClientUtils {

    private AzureStorageDataLakeClientUtils() {

    }

    public static DataLakeServiceClient getClient() {
        String instanceType = System.getProperty("azure.instance.type");

        String accountName = System.getProperty(AzureConfigs.ACCOUNT_NAME);
        String accountKey = System.getProperty(AzureConfigs.ACCOUNT_KEY);
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);

        String endpoint = String.format("https://%s.dfs.core.windows.net/%s", accountName, accountKey);

        return new DataLakeServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient();
    }
}
