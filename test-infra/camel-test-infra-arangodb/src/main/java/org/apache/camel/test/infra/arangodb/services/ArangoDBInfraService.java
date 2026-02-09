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
package org.apache.camel.test.infra.arangodb.services;

import com.arangodb.ArangoDB;
import org.apache.camel.test.infra.common.services.InfrastructureService;

public interface ArangoDBInfraService extends InfrastructureService {

    // User port
    @Deprecated
    int getPort();

    int port();

    String host();

    // User host
    @Deprecated
    String getHost();

    default String getServiceAddress() {
        return String.format("%s:%d", getHost(), getPort());
    }

    default String database() {
        String database = "myDatabase";
        ArangoDB arangoDB = new ArangoDB.Builder().host(host(), port()).build();

        arangoDB.createDatabase(database);
        arangoDB.db(database).createCollection(documentCollection());

        return database;
    }

    default String documentCollection() {
        return "myCollection";
    }

    default String user() {
        return "root";
    }

    default String password() {
        return "";
    }
}
