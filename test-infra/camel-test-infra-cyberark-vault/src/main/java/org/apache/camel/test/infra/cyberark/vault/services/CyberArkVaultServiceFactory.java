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
package org.apache.camel.test.infra.cyberark.vault.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class CyberArkVaultServiceFactory {

    private static class SingletonCyberArkVaultService extends SingletonService<CyberArkVaultService>
            implements CyberArkVaultService {
        public SingletonCyberArkVaultService(CyberArkVaultService service, String name) {
            super(service, name);
        }

        @Override
        public String account() {
            return getService().account();
        }

        @Override
        public String username() {
            return getService().username();
        }

        @Override
        public String apiKey() {
            return getService().apiKey();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String host() {
            return getService().host();
        }
    }

    private CyberArkVaultServiceFactory() {

    }

    public static SimpleTestServiceBuilder<CyberArkVaultService> builder() {
        return new SimpleTestServiceBuilder<>("cyberark-vault");
    }

    public static CyberArkVaultService createService() {
        return builder()
                .addLocalMapping(CyberArkVaultLocalContainerService::new)
                .build();
    }

    public static CyberArkVaultService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final CyberArkVaultService INSTANCE;
        static {
            SimpleTestServiceBuilder<CyberArkVaultService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonCyberArkVaultService(new CyberArkVaultLocalContainerService(), "cyberark-vault"));
            INSTANCE = instance.build();
        }
    }

    public static class CyberArkVaultLocalContainerService extends CyberArkVaultLocalContainerInfraService
            implements CyberArkVaultService {
    }
}
