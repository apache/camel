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
package org.apache.camel.component.clickhouse;

import com.clickhouse.client.api.Client;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Interact with <a href="https://clickhouse.com/">ClickHouse</a>, the high-performance columnar OLAP database, for
 * high-throughput ingestion and OLAP queries.
 */
@UriEndpoint(firstVersion = "4.22.0", scheme = "clickhouse", title = "ClickHouse",
             syntax = "clickhouse:database", category = { Category.DATABASE, Category.BIGDATA },
             producerOnly = true, headersClass = ClickHouseConstants.class)
public class ClickHouseEndpoint extends DefaultEndpoint {

    private Client client;
    private boolean clientCreated;

    @UriPath
    @Metadata(required = true,
              description = "The ClickHouse database. A table may also be provided using the database.table syntax.")
    private String database;
    @UriParam(description = "The target table for insert operations. Can also be given in the path as database.table"
                            + " or overridden per message with the CamelClickHouseTable header.")
    private String table;
    @UriParam(description = "The ClickHouse HTTP endpoint URL, e.g. http://localhost:8123. Required unless a shared"
                            + " Client bean is autowired or configured on the component.")
    private String serverUrl;
    @UriParam(label = "security", defaultValue = "default", description = "The username used to authenticate to ClickHouse.")
    private String username = "default";
    @UriParam(label = "security", secret = true, description = "The password used to authenticate to ClickHouse.")
    private String password;
    @UriParam(label = "security", defaultValue = "false",
              description = "Whether to connect to ClickHouse over a secure (HTTPS) connection.")
    private boolean ssl;
    @UriParam(defaultValue = "INSERT", description = "The operation to perform: insert, query or ping.")
    private ClickHouseOperation operation = ClickHouseOperation.INSERT;
    @UriParam(defaultValue = "JSONEachRow",
              description = "The ClickHouse data format used for insert and query operations, e.g. JSONEachRow,"
                            + " RowBinary, CSV, TSV or Parquet.")
    private String format = "JSONEachRow";
    @UriParam(defaultValue = "0",
              description = "The client-side batch size hint for insert operations. A value of 0 disables client-side"
                            + " batching (data is streamed as-is).")
    private int batchSize;
    @UriParam(defaultValue = "false",
              description = "Whether to use ClickHouse server-side asynchronous inserts (async_insert=1).")
    private boolean asyncInsert;
    @UriParam(defaultValue = "true",
              description = "When asyncInsert is enabled, whether the server should wait for the async insert to be"
                            + " flushed before acknowledging (wait_for_async_insert).")
    private boolean waitForAsyncInsert = true;
    @UriParam(defaultValue = "false",
              description = "Whether to compress the insert request payload sent to the server (LZ4).")
    private boolean compression;

    public ClickHouseEndpoint() {
    }

    public ClickHouseEndpoint(String uri, ClickHouseComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ClickHouseProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null && ObjectHelper.isEmpty(serverUrl)) {
            throw new IllegalArgumentException(
                    "Either a shared Client must be autowired or the serverUrl option must be configured");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (clientCreated && client != null) {
            client.close();
            client = null;
            clientCreated = false;
        }
        super.doStop();
    }

    /**
     * Returns the ClickHouse client, lazily building one from the endpoint connection options (serverUrl, username,
     * password, ...) when no shared client has been provided.
     */
    public Client getClient() {
        if (client == null && ObjectHelper.isNotEmpty(serverUrl)) {
            synchronized (this) {
                if (client == null) {
                    Client.Builder builder = new Client.Builder()
                            .addEndpoint(serverUrl)
                            .setUsername(username != null ? username : "default")
                            .setPassword(password != null ? password : "");
                    if (ObjectHelper.isNotEmpty(database)) {
                        builder.setDefaultDatabase(database);
                    }
                    if (compression) {
                        builder.compressClientRequest(true);
                    }
                    client = builder.build();
                    clientCreated = true;
                }
            }
        }
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public ClickHouseOperation getOperation() {
        return operation;
    }

    public void setOperation(ClickHouseOperation operation) {
        this.operation = operation;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isAsyncInsert() {
        return asyncInsert;
    }

    public void setAsyncInsert(boolean asyncInsert) {
        this.asyncInsert = asyncInsert;
    }

    public boolean isWaitForAsyncInsert() {
        return waitForAsyncInsert;
    }

    public void setWaitForAsyncInsert(boolean waitForAsyncInsert) {
        this.waitForAsyncInsert = waitForAsyncInsert;
    }

    public boolean isCompression() {
        return compression;
    }

    public void setCompression(boolean compression) {
        this.compression = compression;
    }
}
