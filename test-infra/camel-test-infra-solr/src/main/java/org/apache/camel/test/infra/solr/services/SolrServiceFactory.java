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
package org.apache.camel.test.infra.solr.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class SolrServiceFactory {

    private static class SingletonSolrService extends SingletonService<SolrService> implements SolrService {
        public SingletonSolrService(SolrService service, String name) {
            super(service, name);
        }

        @Override
        public int getPort() {
            return getService().getPort();
        }

        @Override
        public String getSolrHost() {
            return getService().getSolrHost();
        }
    }

    private SolrServiceFactory() {

    }

    public static SimpleTestServiceBuilder<SolrService> builder() {
        return new SimpleTestServiceBuilder<>(SolrContainer.CONTAINER_NAME);
    }

    public static SolrService createService() {
        return builder()
                .addLocalMapping(SolrLocalContainerService::new)
                .addRemoteMapping(SolrRemoteService::new)
                .build();
    }

    public static SolrService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final SolrService INSTANCE;
        static {
            SimpleTestServiceBuilder<SolrService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonSolrService(new SolrLocalContainerService(), SolrContainer.CONTAINER_NAME))
                    .addRemoteMapping(SolrRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class SolrRemoteService extends SolrRemoteInfraService implements SolrService {
    }

}
