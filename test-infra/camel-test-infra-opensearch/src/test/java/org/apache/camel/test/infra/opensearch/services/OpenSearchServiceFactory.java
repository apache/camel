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

package org.apache.camel.test.infra.opensearch.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class OpenSearchServiceFactory {

    static class SingletonOpenSearchService extends SingletonService<OpenSearchService> implements OpenSearchService {
        public SingletonOpenSearchService(OpenSearchService service, String name) {
            super(service, name);
        }

        @Override
        public int getPort() {
            return getService().getPort();
        }

        public String getOpenSearchHost() {
            return getService().getOpenSearchHost();
        }

        @Override
        public String getHttpHostAddress() {
            return getService().getHttpHostAddress();
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

    private OpenSearchServiceFactory() {

    }

    public static SimpleTestServiceBuilder<OpenSearchService> builder() {
        return new SimpleTestServiceBuilder<>("opensearch");
    }

    public static OpenSearchService createService() {
        return builder()
                .addLocalMapping(OpenSearchLocalContainerService::new)
                .addRemoteMapping(RemoteOpenSearchService::new)
                .build();
    }

    public static OpenSearchService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final OpenSearchService INSTANCE;
        static {
            SimpleTestServiceBuilder<OpenSearchService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonOpenSearchService(new OpenSearchLocalContainerService(), "opensearch"))
                    .addRemoteMapping(RemoteOpenSearchService::new);
            INSTANCE = instance.build();
        }
    }
}
