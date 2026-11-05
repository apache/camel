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

import java.net.URL;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import org.apache.camel.test.infra.azure.common.AzureConfigs;
import reactor.core.publisher.Mono;

public final class AzureStorageDataLakeClientUtils {

    private AzureStorageDataLakeClientUtils() {

    }

    public static DataLakeServiceClient getClient() {
        String instanceType = System.getProperty("azure.instance.type");

        String accountName = System.getProperty(AzureConfigs.ACCOUNT_NAME);
        String accountKey = System.getProperty(AzureConfigs.ACCOUNT_KEY);
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);

        DataLakeServiceClientBuilder builder = new DataLakeServiceClientBuilder()
                .credential(credential);

        if (instanceType == null || instanceType.equals("local-azure-container")) {
            builder.endpoint(String.format("http://%s:%s/%s", System.getProperty(AzureConfigs.HOST),
                    System.getProperty(AzureConfigs.PORT), accountName))
                    .addPolicy(new DfsHostPolicy(accountName));
        } else {
            builder.endpoint(String.format("https://%s.dfs.core.windows.net", accountName));
        }

        return builder.buildClient();
    }

    /**
     * Floci AZ serves the Data Lake Storage Gen2 (DFS) API of an account only to the requests for the host
     * {@code <account>.dfs.core.windows.net}, with no account in the path. The client is built for the address of the
     * container, and this policy sends its requests to that host.
     */
    private static final class DfsHostPolicy implements HttpPipelinePolicy {
        private final String accountName;

        private DfsHostPolicy(String accountName) {
            this.accountName = accountName;
        }

        @Override
        public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
            HttpRequest request = context.getHttpRequest();

            URL url = request.getUrl();
            String accountPath = "/" + accountName;
            String path = url.getPath();
            if (path.equals(accountPath) || path.startsWith(accountPath + "/")) {
                String rest = path.substring(accountPath.length());
                String query = url.getQuery();
                request.setUrl(url.getProtocol() + "://" + url.getAuthority() + (rest.isEmpty() ? "/" : rest)
                               + (query != null ? "?" + query : ""));
            }
            request.setHeader(HttpHeaderName.HOST, accountName + ".dfs.core.windows.net");

            return next.process();
        }
    }
}
