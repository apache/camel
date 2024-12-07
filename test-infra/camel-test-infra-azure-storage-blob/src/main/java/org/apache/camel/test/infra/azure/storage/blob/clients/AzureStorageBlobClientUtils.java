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

package org.apache.camel.test.infra.azure.storage.blob.clients;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.test.infra.azure.common.AzureConfigs;

public final class AzureStorageBlobClientUtils {

    private AzureStorageBlobClientUtils() {

    }

    public static BlobServiceClient getClient() {
        String instanceType = System.getProperty("azure.instance.type");

        String accountName = System.getProperty(AzureConfigs.ACCOUNT_NAME);
        String accountKey = System.getProperty(AzureConfigs.ACCOUNT_KEY);
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);

        String host = System.getProperty(AzureConfigs.HOST);
        String port = System.getProperty(AzureConfigs.PORT);

        String endpoint;

        if (instanceType == null || instanceType.equals("local-azure-container")) {
            endpoint = String.format("http://%s:%s/%s", host, port, accountName);
        } else {
            if (host == null || host.isEmpty()) {
                endpoint = String.format("https://%s.blob.core.windows.net/%s", accountName, accountKey);
            } else {
                endpoint = String.format("http://%s:%s/%s", host, port, accountName);
            }
        }

        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS).setPrettyPrintBody(true))
                .buildClient();
    }
}
