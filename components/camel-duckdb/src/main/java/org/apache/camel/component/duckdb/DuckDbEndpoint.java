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
package org.apache.camel.component.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Interact with <a href="https://duckdb.org/">DuckDB</a>, the in-process analytical SQL database, for embedded
 * analytics workloads.
 */
@UriEndpoint(firstVersion = "4.22.0", scheme = "duckdb", title = "DuckDB",
             syntax = "duckdb:databasePath", category = { Category.DATABASE, Category.BIGDATA },
             producerOnly = true, headersClass = DuckDbConstants.class)
public class DuckDbEndpoint extends DefaultEndpoint {

    private final ReentrantLock connectionLock = new ReentrantLock();

    private DataSource dataSource;
    private Connection connection;
    private Connection injectedConnection;
    private boolean connectionOwned;

    @UriPath(description = "Database path, either :memory: or a relative or absolute file path.")
    private String databasePath = ":memory:";
    @UriParam(description = "Target table for insert and copy operations. Can be overridden per message with the"
                            + " CamelDuckDbTable header.")
    private String table;
    @UriParam(description = "Full JDBC URL override. When set, databasePath is not used to build the URL.")
    private String jdbcUrl;
    @UriParam(defaultValue = "EXECUTE", description = "The operation to perform: execute, query, insert, copy or ping.")
    private DuckDbOperation operation = DuckDbOperation.EXECUTE;
    @UriParam(description = "Static SQL for query when the message body is empty.")
    private String query;
    @UriParam(defaultValue = "0",
              description = "Batch size for insert when the body is a List. 0 inserts all rows in one JDBC batch.")
    private int batchSize;
    @UriParam(defaultValue = "auto",
              description = "File format for copy: csv, parquet, json or auto (inferred from the file extension).")
    private String format = "auto";
    @UriParam(defaultValue = "false",
              description = "Open the embedded database file in read-only mode. Only applies to connections created by"
                            + " this endpoint from databasePath or jdbcUrl, and requires an existing database file.")
    private boolean readOnly;
    @UriParam(defaultValue = "LIST_MAP",
              description = "How query results are returned: LIST_MAP (List of Map) or JSON array string.")
    private DuckDbResultFormat resultFormat = DuckDbResultFormat.LIST_MAP;

    public DuckDbEndpoint() {
    }

    public DuckDbEndpoint(String uri, DuckDbComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DuckDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    /**
     * Opens a connection scope for one producer invocation.
     * <p>
     * When {@code databasePathOverride} is set (from the {@code CamelDuckDbDatabasePath} header), a short-lived
     * connection to that path is opened and closed after use. Otherwise the endpoint-owned shared connection is used
     * under a lock so concurrent exchanges do not share a JDBC connection unsafely. DataSource connections are also
     * short-lived and closed after use.
     */
    DuckDbConnectionScope openConnectionScope(String databasePathOverride) throws Exception {
        if (ObjectHelper.isNotEmpty(databasePathOverride)) {
            String url = DuckDbJdbcSupport.resolveJdbcUrl(null, databasePathOverride.trim());
            Connection overrideConnection
                    = DriverManager.getConnection(url, DuckDbJdbcSupport.connectionProperties(readOnly));
            return new DuckDbConnectionScope(overrideConnection, true, null);
        }
        if (injectedConnection != null) {
            return new DuckDbConnectionScope(injectedConnection, false, null);
        }
        if (dataSource != null) {
            return new DuckDbConnectionScope(dataSource.getConnection(), true, null);
        }

        connectionLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                String url = DuckDbJdbcSupport.resolveJdbcUrl(jdbcUrl, databasePath);
                connection = DriverManager.getConnection(url, DuckDbJdbcSupport.connectionProperties(readOnly));
                connectionOwned = true;
            }
            return new DuckDbConnectionScope(connection, false, connectionLock);
        } catch (Exception e) {
            connectionLock.unlock();
            throw e;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (injectedConnection != null || dataSource != null) {
            return;
        }
        String url = DuckDbJdbcSupport.resolveJdbcUrl(jdbcUrl, databasePath);
        connection = DriverManager.getConnection(url, DuckDbJdbcSupport.connectionProperties(readOnly));
        connectionOwned = true;
    }

    @Override
    protected void doStop() throws Exception {
        connectionLock.lock();
        try {
            if (connectionOwned && connection != null) {
                connection.close();
                connection = null;
                connectionOwned = false;
            }
        } finally {
            connectionLock.unlock();
        }
        super.doStop();
    }

    void setConnectionForTesting(Connection connection) {
        this.injectedConnection = connection;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public DuckDbOperation getOperation() {
        return operation;
    }

    public void setOperation(DuckDbOperation operation) {
        this.operation = operation;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public DuckDbResultFormat getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(DuckDbResultFormat resultFormat) {
        this.resultFormat = resultFormat;
    }

    String resolvedJdbcUrl() {
        return DuckDbJdbcSupport.resolveJdbcUrl(jdbcUrl, databasePath);
    }
}
