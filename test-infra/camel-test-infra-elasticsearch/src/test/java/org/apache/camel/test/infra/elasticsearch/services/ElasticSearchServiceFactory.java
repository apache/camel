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

package org.apache.camel.test.infra.elasticsearch.services;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ElasticSearchServiceFactory {

    static class SingletonElasticSearchService extends SingletonService<ElasticSearchService> implements ElasticSearchService {
        public SingletonElasticSearchService(ElasticSearchService service, String name) {
            super(service, name);
        }

        @Override
        public int getPort() {
            return getService().getPort();
        }

        @Override
        public String getElasticSearchHost() {
            return getService().getElasticSearchHost();
        }

        @Override
        public String getHttpHostAddress() {
            return getService().getHttpHostAddress();
        }

        @Override
        public Optional<String> getCertificatePath() {
            return getService().getCertificatePath();
        }

        @Override
        public Optional<SSLContext> getSslContext() {
            return getService().getSslContext();
        }

        @Override
        public String getUsername() {
            return getService().getUsername();
        }

        @Override
        public String getPassword() {
            return getService().getPassword();
        }
    }

    private ElasticSearchServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ElasticSearchService> builder() {
        return new SimpleTestServiceBuilder<>("elasticsearch");
    }

    public static ElasticSearchService createService() {
        return builder()
                .addLocalMapping(ElasticSearchLocalContainerService::new)
                .addRemoteMapping(RemoteElasticSearchService::new)
                .build();
    }

    public static ElasticSearchService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final ElasticSearchService INSTANCE;
        static {
            SimpleTestServiceBuilder<ElasticSearchService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonElasticSearchService(new ElasticSearchLocalContainerService(), "elastic"))
                    .addRemoteMapping(RemoteElasticSearchService::new);
            INSTANCE = instance.build();
        }
    }
}
