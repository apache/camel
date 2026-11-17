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

package org.apache.camel.test.infra.google.secret.manager.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Secret Manager service of Floci GCP, or with {@code -Dgoogle.instance.type=remote} the one of a real
 * Google Cloud project.
 */
public final class GoogleSecretManagerServiceFactory {

    private static class SingletonGoogleSecretManagerService extends SingletonService<GoogleSecretManagerService>
            implements GoogleSecretManagerService {
        public SingletonGoogleSecretManagerService(GoogleSecretManagerService service, String name) {
            super(service, name);
        }

        @Override
        public String projectId() {
            return getService().projectId();
        }

        @Override
        public String endpoint() {
            return getService().endpoint();
        }
    }

    private GoogleSecretManagerServiceFactory() {

    }

    public static SimpleTestServiceBuilder<GoogleSecretManagerService> builder() {
        return new SimpleTestServiceBuilder<>("google");
    }

    public static GoogleSecretManagerService createService() {
        return builder()
                .addLocalMapping(GoogleSecretManagerLocalContainerService::new)
                .addRemoteMapping(GoogleSecretManagerRemoteService::new)
                .build();
    }

    public static GoogleSecretManagerService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final GoogleSecretManagerService INSTANCE;
        static {
            SimpleTestServiceBuilder<GoogleSecretManagerService> instance = builder();
            // a name of its own: the singleton of another Google service of the same JVM must not stand for this one
            instance.addLocalMapping(
                    () -> new SingletonGoogleSecretManagerService(
                            new GoogleSecretManagerLocalContainerService(), "google-secret-manager"))
                    .addRemoteMapping(GoogleSecretManagerRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class GoogleSecretManagerLocalContainerService extends GoogleSecretManagerLocalContainerInfraService
            implements GoogleSecretManagerService {
    }

    static class GoogleSecretManagerRemoteService extends GoogleSecretManagerRemoteInfraService
            implements GoogleSecretManagerService {
    }
}
