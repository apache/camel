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

package org.apache.camel.test.infra.azure.key.vault.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Key Vault of Floci AZ, or with {@code -Dazure.instance.type=remote} the vault of the
 * {@code AzureKeyVaultConfigs} system properties.
 */
public final class AzureKeyVaultServiceFactory {

    private static class SingletonAzureKeyVaultService extends SingletonService<AzureKeyVaultService>
            implements AzureKeyVaultService {
        public SingletonAzureKeyVaultService(AzureKeyVaultService service, String name) {
            super(service, name);
        }

        @Override
        public String vaultName() {
            return getService().vaultName();
        }

        @Override
        public String vaultUrl() {
            return getService().vaultUrl();
        }
    }

    private AzureKeyVaultServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureKeyVaultService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureKeyVaultService createService() {
        return builder()
                .addLocalMapping(AzureKeyVaultLocalContainerService::new)
                .addRemoteMapping(AzureKeyVaultRemoteService::new)
                .build();
    }

    public static AzureKeyVaultService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureKeyVaultService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureKeyVaultService> instance = builder();
            // a name of its own: the singleton of another Azure service of the same JVM must not stand for this one
            instance.addLocalMapping(
                    () -> new SingletonAzureKeyVaultService(new AzureKeyVaultLocalContainerService(), "azure-key-vault"))
                    .addRemoteMapping(AzureKeyVaultRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureKeyVaultLocalContainerService extends AzureKeyVaultLocalContainerInfraService
            implements AzureKeyVaultService {
    }

    static class AzureKeyVaultRemoteService extends AzureKeyVaultRemoteInfraService implements AzureKeyVaultService {
    }
}
