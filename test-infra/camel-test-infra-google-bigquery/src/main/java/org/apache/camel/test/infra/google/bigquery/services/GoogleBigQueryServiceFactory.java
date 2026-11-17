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

package org.apache.camel.test.infra.google.bigquery.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the BigQuery service of Floci GCP, or with {@code -Dgoogle.instance.type=remote} the one of a real Google
 * Cloud project.
 */
public final class GoogleBigQueryServiceFactory {

    private static class SingletonGoogleBigQueryService extends SingletonService<GoogleBigQueryService>
            implements GoogleBigQueryService {
        public SingletonGoogleBigQueryService(GoogleBigQueryService service, String name) {
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

    private GoogleBigQueryServiceFactory() {

    }

    public static SimpleTestServiceBuilder<GoogleBigQueryService> builder() {
        return new SimpleTestServiceBuilder<>("google");
    }

    public static GoogleBigQueryService createService() {
        return builder()
                .addLocalMapping(GoogleBigQueryLocalContainerService::new)
                .addRemoteMapping(GoogleBigQueryRemoteService::new)
                .build();
    }

    public static GoogleBigQueryService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final GoogleBigQueryService INSTANCE;
        static {
            SimpleTestServiceBuilder<GoogleBigQueryService> instance = builder();
            // a name of its own: the singleton of another Google service of the same JVM must not stand for this one
            instance.addLocalMapping(
                    () -> new SingletonGoogleBigQueryService(new GoogleBigQueryLocalContainerService(), "google-bigquery"))
                    .addRemoteMapping(GoogleBigQueryRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class GoogleBigQueryLocalContainerService extends GoogleBigQueryLocalContainerInfraService
            implements GoogleBigQueryService {
    }

    static class GoogleBigQueryRemoteService extends GoogleBigQueryRemoteInfraService implements GoogleBigQueryService {
    }
}
