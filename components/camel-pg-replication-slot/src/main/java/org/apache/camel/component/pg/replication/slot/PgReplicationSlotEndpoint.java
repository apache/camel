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
package org.apache.camel.component.pg.replication.slot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poll for PostgreSQL Write-Ahead Log (WAL) records using Streaming Replication Slots.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "pg-replication-slot", title = "PostgresSQL Replication Slot",
             syntax = "pg-replication-slot:host:port/database/slot:outputPlugin",
             category = { Category.DATABASE }, consumerOnly = true)
public class PgReplicationSlotEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PgReplicationSlotEndpoint.class);

    private static final Pattern URI_PATTERN = Pattern.compile(
            "^pg-replication-slot:(//)?(?<host>[^:]*):?(?<port>\\d*)?/(?<database>\\w+)/(?<slot>\\w+):(?<plugin>\\w+).*$");

    @UriPath(description = "Postgres host", label = "common", defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(description = "Postgres port", label = "common", defaultValue = "5432")
    private Integer port = 5432;
    @UriPath(description = "Postgres database name", label = "common")
    @Metadata(required = true)
    private String database;
    @UriPath
    @Metadata(description = "Replication Slot name", label = "common", required = true)
    private String slot;
    @UriPath
    @Metadata(description = "Output plugin name", label = "common", required = true)
    private String outputPlugin;
    @UriParam(description = "Postgres user", label = "common", defaultValue = "postgres")
    private String user = "postgres";
    @UriParam(description = "Postgres password", label = "common", secret = true)
    private String password;
    @UriParam(label = "advanced", defaultValue = "10")
    private Integer statusInterval = 10;
    @UriParam(label = "advanced", prefix = "slotOptions.", multiValue = true)
    private Map<String, Object> slotOptions = Collections.emptyMap();
    @UriParam(label = "advanced", defaultValue = "true")
    private Boolean autoCreateSlot = true;

    public PgReplicationSlotEndpoint(String uri, Component component) {
        super(uri, component);
        parseUri(uri);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PgReplicationSlotConsumer consumer = new PgReplicationSlotConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Creates a new PostgreSQL JDBC connection that's setup for replication.
     */
    Connection newDbConnection() throws SQLException {
        Properties props = new Properties();

        PGProperty.USER.set(props, this.getUser());
        PGProperty.PASSWORD.set(props, this.getPassword());
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, "9.6");
        PGProperty.REPLICATION.set(props, "database");
        PGProperty.PREFER_QUERY_MODE.set(props, "simple");
        PGProperty.TCP_KEEP_ALIVE.set(props, true);

        return DriverManager.getConnection(
                String.format("jdbc:postgresql://%s:%d/%s", this.getHost(), this.getPort(), this.getDatabase()),
                props);
    }

    /**
     * Parse the provided URI and extract available parameters
     *
     * @throws IllegalArgumentException if there is an error in the parameters
     */
    protected final void parseUri(String uri) {
        LOG.debug("URI: {}", uri);

        Matcher matcher = URI_PATTERN.matcher(uri);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("The provided URL does not match the acceptable pattern");
        }

        if (matcher.group("host").length() > 0) {
            this.setHost(matcher.group("host"));
        }

        if (matcher.group("port").length() > 0) {
            this.setPort(Integer.valueOf(matcher.group("port")));
        }

        this.setDatabase(matcher.group("database"));
        this.setSlot(matcher.group("slot"));
        this.setOutputPlugin(matcher.group("plugin"));
    }

    public String getHost() {
        return host;
    }

    /**
     * PostgreSQL server host
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * PostgreSQL server port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * PostgreSQL database name
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getSlot() {
        return slot;
    }

    /**
     * Replication slot name.
     */
    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getOutputPlugin() {
        return outputPlugin;
    }

    /**
     * Output plugin name (e.g. test_decoding, wal2json)
     */
    public void setOutputPlugin(String outputPlugin) {
        this.outputPlugin = outputPlugin;
    }

    public String getUser() {
        return user;
    }

    /**
     * PostgreSQL username
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * PostgreSQL password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getStatusInterval() {
        return statusInterval;
    }

    /**
     * Specifies the number of seconds between status packets sent back to Postgres server.
     */
    public void setStatusInterval(Integer statusInterval) {
        this.statusInterval = statusInterval;
    }

    public Map<String, Object> getSlotOptions() {
        return slotOptions;
    }

    /**
     * Slot options to be passed to the output plugin.
     */
    public void setSlotOptions(Map<String, Object> slotOptions) {
        this.slotOptions = slotOptions;
    }

    public Boolean getAutoCreateSlot() {
        return autoCreateSlot;
    }

    /**
     * Auto create slot if it does not exist
     */
    public void setAutoCreateSlot(Boolean autoCreateSlot) {
        this.autoCreateSlot = autoCreateSlot;
    }
}
