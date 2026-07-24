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
package org.apache.camel.test.infra.keycloak.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.keycloak.admin.client.Keycloak;

public final class KeycloakServiceFactory {

    private static class SingletonKeycloakService extends SingletonService<KeycloakService> implements KeycloakService {
        public SingletonKeycloakService(KeycloakService service, String name) {
            super(service, name);
        }

        @Override
        public String getKeycloakServerUrl() {
            return getService().getKeycloakServerUrl();
        }

        @Override
        public String getKeycloakRealm() {
            return getService().getKeycloakRealm();
        }

        @Override
        public String getKeycloakUsername() {
            return getService().getKeycloakUsername();
        }

        @Override
        public String getKeycloakPassword() {
            return getService().getKeycloakPassword();
        }

        @Override
        public Keycloak getKeycloakAdminClient() {
            return getService().getKeycloakAdminClient();
        }
    }

    private KeycloakServiceFactory() {

    }

    public static SimpleTestServiceBuilder<KeycloakService> builder() {
        return new SimpleTestServiceBuilder<>("keycloak");
    }

    public static KeycloakService createService() {
        return builder()
                .addLocalMapping(KeycloakLocalContainerService::new)
                .build();
    }

    public static KeycloakService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final KeycloakService INSTANCE;
        static {
            SimpleTestServiceBuilder<KeycloakService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonKeycloakService(new KeycloakLocalContainerService(), "keycloak"));
            INSTANCE = instance.build();
        }
    }

    public static class KeycloakLocalContainerService extends KeycloakLocalContainerInfraService
            implements KeycloakService {
    }
}
