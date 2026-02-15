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
package org.apache.camel.component.file.azure;

import com.azure.core.http.policy.AzureSasCredentialPolicy;
import com.azure.core.http.policy.BearerTokenAuthenticationPolicy;
import com.azure.storage.common.policy.StorageSharedKeyCredentialPolicy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FilesOperationsTest extends CamelTestSupport {

    @Test
    void testCreateClientAzureIdentity() {
        var endpoint = context
                .getEndpoint("azure-files://account.file.core.windows.net/share?credentialType=AZURE_IDENTITY",
                        FilesEndpoint.class);

        var filesOperations = new FilesOperations(endpoint);
        var client = filesOperations.getClient();

        var hasAuthPolicy = false;
        for (int i = 0; i < client.getHttpPipeline().getPolicyCount(); i++) {
            if (client.getHttpPipeline().getPolicy(i) instanceof BearerTokenAuthenticationPolicy) {
                hasAuthPolicy = true;
                break;
            }
        }

        Assertions.assertTrue(hasAuthPolicy);
    }

    @Test
    void testCreateClientSharedAccountKey() {
        var endpoint = context
                .getEndpoint(
                        "azure-files://account.file.core.windows.net/share?credentialType=SHARED_ACCOUNT_KEY&sharedKey=sharedKey",
                        FilesEndpoint.class);

        var filesOperations = new FilesOperations(endpoint);
        var client = filesOperations.getClient();

        var hasAuthPolicy = false;
        for (int i = 0; i < client.getHttpPipeline().getPolicyCount(); i++) {
            if (client.getHttpPipeline().getPolicy(i) instanceof StorageSharedKeyCredentialPolicy) {
                hasAuthPolicy = true;
                break;
            }
        }

        Assertions.assertTrue(hasAuthPolicy);
    }

    @Test
    void testCreateClientAzureSAS() {
        var endpoint = context
                .getEndpoint(
                        "azure-files://account.file.core.windows.net/share?credentialType=AZURE_SAS&sv=2022-11-02&ss=f&srt=sco&sp=rwdlc&se=2023-06-18T22:29:13Z&st=2023-06-05T14:29:13Z&spr=https&sig=MPsMh8zci0v3To7IT9SKdaFGZV8ezno63m9C8s9bdVQ%3D",
                        FilesEndpoint.class);

        var filesOperations = new FilesOperations(endpoint);
        var client = filesOperations.getClient();

        var hasAuthPolicy = false;
        for (int i = 0; i < client.getHttpPipeline().getPolicyCount(); i++) {
            if (client.getHttpPipeline().getPolicy(i) instanceof AzureSasCredentialPolicy) {
                hasAuthPolicy = true;
                break;
            }
        }

        Assertions.assertTrue(hasAuthPolicy);
    }
}
