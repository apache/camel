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

import java.time.Duration;

import org.apache.camel.test.infra.azure.common.AzureCredentialsHolder;
import org.apache.camel.test.infra.azure.common.AzureProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A container running Floci AZ (https://github.com/floci-io/floci-az), a local Azure emulator. It serves every Azure
 * service on one port, and tells them apart by the first segment of the path: {@code /devstoreaccount1} for Blob and
 * Data Lake, {@code /devstoreaccount1-queue} for Queue, {@code /devstoreaccount1-keyvault} for Key Vault, and so on.
 */
public class FlociAzContainer extends GenericContainer<FlociAzContainer> {
    public static final int SERVICE_PORT = 4577;
    public static final String DEFAULT_ACCOUNT_NAME = "devstoreaccount1";
    // the well-known development storage key, which Floci AZ accepts as Azurite does
    public static final String DEFAULT_ACCOUNT_KEY
            = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    public FlociAzContainer() {
        this(LocalPropertyResolver.getProperty(FlociAzContainer.class, AzureProperties.AZURE_CONTAINER), false);
    }

    public FlociAzContainer(String imageName, boolean fixedPort) {
        super(imageName);

        ContainerEnvironmentUtil.configurePort(this, fixedPort, SERVICE_PORT);

        waitingFor(Wait.forHttp("/health")
                .forPort(SERVICE_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public AzureCredentialsHolder azureCredentials() {
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

    /**
     * The address of the emulator, for example {@code http://localhost:32768}
     */
    public String serviceAddress() {
        return String.format("http://%s:%d", getHost(), getMappedPort(SERVICE_PORT));
    }

    /**
     * The endpoint of the storage account, used by the Blob and Data Lake clients, for example
     * {@code http://localhost:32768/devstoreaccount1}
     */
    public String accountEndpoint() {
        return serviceAddress() + "/" + DEFAULT_ACCOUNT_NAME;
    }
}
