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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.duckdb.common.DuckDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process DuckDB using a temporary database file (default) or {@code :memory:}.
 */
@InfraService(service = DuckDbInfraService.class,
              description = "DuckDB is an in-process analytical SQL database",
              serviceAlias = { "duckdb" })
public class DuckDbEmbeddedInfraService implements DuckDbInfraService {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbEmbeddedInfraService.class);

    private final boolean inMemory;
    private Path databaseFile;
    private String jdbcUrl;
    private String databasePath;
    private Connection connection;

    public DuckDbEmbeddedInfraService() {
        this(false);
    }

    public DuckDbEmbeddedInfraService(boolean inMemory) {
        this.inMemory = inMemory;
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String getDatabasePath() {
        return databasePath;
    }

    @Override
    public void registerProperties() {
        System.setProperty(DuckDbProperties.DUCKDB_JDBC_URL, jdbcUrl);
        System.setProperty(DuckDbProperties.DUCKDB_DATABASE_PATH, databasePath);
    }

    @Override
    public void initialize() {
        try {
            if (inMemory) {
                databasePath = ":memory:";
                jdbcUrl = "jdbc:duckdb:";
            } else {
                Path dir = Files.createTempDirectory("camel-duckdb-");
                databaseFile = dir.resolve("embedded.db");
                databasePath = databaseFile.toAbsolutePath().toString();
                jdbcUrl = "jdbc:duckdb:" + databasePath;
            }
            connection = DriverManager.getConnection(jdbcUrl);
            registerProperties();
            LOG.info("DuckDB running at {}", jdbcUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded DuckDB", e);
        }
    }

    /**
     * Runs DDL/DML on the shared embedded connection (for test setup).
     */
    public void executeSql(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOG.debug("Error closing DuckDB connection: {}", e.getMessage());
            }
            connection = null;
        }
        if (databaseFile != null) {
            try {
                Files.deleteIfExists(databaseFile);
                Path parent = databaseFile.getParent();
                if (parent != null) {
                    Files.deleteIfExists(parent);
                }
            } catch (Exception e) {
                LOG.debug("Could not delete temp DuckDB files {}: {}", databaseFile, e.getMessage());
            }
            databaseFile = null;
        }
    }
}
