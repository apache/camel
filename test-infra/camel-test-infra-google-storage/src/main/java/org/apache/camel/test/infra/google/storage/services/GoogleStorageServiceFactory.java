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

package org.apache.camel.test.infra.google.storage.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Cloud Storage service of Floci GCP, or with {@code -Dgoogle.instance.type=remote} the one of a real
 * Google Cloud project.
 */
public final class GoogleStorageServiceFactory {

    private static class SingletonGoogleStorageService extends SingletonService<GoogleStorageService>
            implements GoogleStorageService {
        public SingletonGoogleStorageService(GoogleStorageService service, String name) {
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

    private GoogleStorageServiceFactory() {

    }

    public static SimpleTestServiceBuilder<GoogleStorageService> builder() {
        return new SimpleTestServiceBuilder<>("google");
    }

    public static GoogleStorageService createService() {
        return builder()
                .addLocalMapping(GoogleStorageLocalContainerService::new)
                .addRemoteMapping(GoogleStorageRemoteService::new)
                .build();
    }

    public static GoogleStorageService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final GoogleStorageService INSTANCE;
        static {
            SimpleTestServiceBuilder<GoogleStorageService> instance = builder();
            // a name of its own: the singleton of another Google service of the same JVM must not stand for this one
            instance.addLocalMapping(
                    () -> new SingletonGoogleStorageService(new GoogleStorageLocalContainerService(), "google-storage"))
                    .addRemoteMapping(GoogleStorageRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class GoogleStorageLocalContainerService extends GoogleStorageLocalContainerInfraService
            implements GoogleStorageService {
    }

    static class GoogleStorageRemoteService extends GoogleStorageRemoteInfraService implements GoogleStorageService {
    }
}
