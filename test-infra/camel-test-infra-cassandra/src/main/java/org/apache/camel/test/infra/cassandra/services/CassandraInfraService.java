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

package org.apache.camel.test.infra.cassandra.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Represents an endpoint to a Cassandra instance
 */
public interface CassandraInfraService extends InfrastructureService {

    int getCQL3Port();

    default String getCQL3Endpoint() {
        return String.format("%s:%d", getCassandraHost(), getCQL3Port());
    }

    String getCassandraHost();

    String hosts();

    int port();

    default String keyspace() {
        return "camel";
    }

    default String datacenter() {
        return "datacenter1";
    }

    default String username() {
        return "cassandra";
    }

    default String password() {
        return "cassandra";
    }

    default String endpointUri() {
        return String.format("cql:%s:%d/%s?username=%s&password=RAW(%s)", hosts(), port(), keyspace(), username(), password());
    }

    default String connectionBase() {
        return String.format("cql:%s:%d", hosts(), port());
    }
}
