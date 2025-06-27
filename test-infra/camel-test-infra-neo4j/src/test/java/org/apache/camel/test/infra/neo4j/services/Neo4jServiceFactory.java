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
package org.apache.camel.test.infra.neo4j.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class Neo4jServiceFactory {
    private Neo4jServiceFactory() {

    }

    public static class SingletonNeo4jService extends SingletonService<Neo4jService> implements Neo4jService {

        public SingletonNeo4jService(Neo4jService service, String name) {
            super(service, name);
        }

        @Override
        public String getNeo4jDatabaseUri() {
            return getService().getNeo4jDatabaseUri();
        }

        @Override
        public String getNeo4jDatabaseUser() {
            return getService().getNeo4jDatabaseUser();
        }

        @Override
        public String getNeo4jDatabasePassword() {
            return getService().getNeo4jDatabasePassword();
        }
    }

    public static SimpleTestServiceBuilder<Neo4jService> builder() {
        return new SimpleTestServiceBuilder<>("neo4j");
    }

    public static Neo4jService createService() {
        return builder()
                .addLocalMapping(Neo4jLocalContainerService::new)
                .addRemoteMapping(Neo4jRemoteService::new)
                .build();
    }

    public static Neo4jService createSingletonService() {
        return builder()
                .addLocalMapping(() -> new SingletonNeo4jService(new Neo4jLocalContainerService(), "neo4j"))
                .build();
    }

    public static class Neo4jLocalContainerService extends Neo4jLocalContainerInfraService implements Neo4jService {
    }

    public static class Neo4jRemoteService extends Neo4jRemoteInfraService implements Neo4jService {
    }
}
