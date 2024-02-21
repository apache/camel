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
package org.apache.camel.test.infra.couchdb.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class CouchDbServiceFactory {
    static class SingletonCouchDbService extends SingletonService<CouchDbService> implements CouchDbService {
        public SingletonCouchDbService(CouchDbService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String getServiceAddress() {
            return getService().getServiceAddress();
        }
    }

    private CouchDbServiceFactory() {

    }

    public static SimpleTestServiceBuilder<CouchDbService> builder() {
        return new SimpleTestServiceBuilder<>("consul");
    }

    public static CouchDbService createService() {
        return builder()
                .addLocalMapping(CouchDbLocalContainerService::new)
                .addRemoteMapping(CouchDbRemoteService::new)
                .build();
    }

    public static CouchDbService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final CouchDbService INSTANCE;
        static {
            SimpleTestServiceBuilder<CouchDbService> instance = builder();

            instance.addLocalMapping(() -> new SingletonCouchDbService(new CouchDbLocalContainerService(), "couchdb"))
                    .addRemoteMapping(CouchDbRemoteService::new);

            INSTANCE = instance.build();
        }
    }
}
