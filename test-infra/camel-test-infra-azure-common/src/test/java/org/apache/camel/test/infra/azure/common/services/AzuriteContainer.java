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

package org.apache.camel.test.infra.azure.common.services;

import org.apache.camel.test.infra.azure.common.AzureCredentialsHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class AzuriteContainer extends GenericContainer<AzuriteContainer> {
    public static final String DEFAULT_ACCOUNT_NAME = "devstoreaccount1";
    public static final String DEFAULT_ACCOUNT_KEY
            = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    public static final String IMAGE_NAME = "mcr.microsoft.com/azure-storage/azurite:3.25.0";

    public AzuriteContainer() {
        this(IMAGE_NAME);
    }

    public AzuriteContainer(String containerName) {
        super(containerName);

        withExposedPorts(AzureServices.BLOB_SERVICE, AzureServices.QUEUE_SERVICE);

        waitingFor(Wait.forListeningPort());
    }

    public AzureCredentialsHolder azureCredentials() {
        // Default credentials for Azurite
        return new AzureCredentialsHolder() {
            @Override
            public String accountName() {
                return DEFAULT_ACCOUNT_NAME;
            }

            @Override
            public String accountKey() {
                return DEFAULT_ACCOUNT_KEY;
            }
        };
    }
}
