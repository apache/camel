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
package org.apache.camel.test.infra.duckdb.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Represents an embedded or remote DuckDB JDBC endpoint for tests.
 */
public interface DuckDbInfraService extends InfrastructureService {

    /**
     * JDBC URL to connect to the DuckDB instance (for example {@code jdbc:duckdb:} or {@code jdbc:duckdb:/path/to/db}).
     */
    String getJdbcUrl();

    /**
     * Database path segment used in Camel duckdb URIs ({@code :memory:} or a file path).
     */
    String getDatabasePath();
}
