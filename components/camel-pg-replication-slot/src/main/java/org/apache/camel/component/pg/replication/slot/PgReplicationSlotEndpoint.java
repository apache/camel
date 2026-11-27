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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive the changes of a PostgreSQL database from a logical replication slot.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "pg-replication-slot", title = "PostgreSQL Replication Slot",
             syntax = "pg-replication-slot:host:port/database/slot:outputPlugin",
             category = { Category.DATABASE }, consumerOnly = true, headersClass = PgReplicationSlotConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                   description = "One message of the output plugin of the slot, as bytes: text for"
                                                 + " test_decoding, JSON for wal2json, the binary protocol for pgoutput;"
                                                 + " convert it to a String for the text plugins. The LSN of the message is in"
                                                 + " the CamelPgReplicationSlotLsn header"))
public class PgReplicationSlotEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PgReplicationSlotEndpoint.class);

    private static final Pattern URI_PATTERN = Pattern.compile(
            "^pg-replication-slot:(//)?(?<host>[^:]*):?(?<port>\\d*)?/(?<database>\\w+)/(?<slot>\\w+):(?<plugin>\\w+).*$");

    @UriPath(description = "The host name or IP address of the PostgreSQL server.", label = "common",
             defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(description = "The port of the PostgreSQL server.", label = "common", defaultValue = "5432")
    private Integer port = 5432;
    @UriPath(description = "The name of the database to connect to. A logical replication slot belongs to one database and"
                           + " decodes only the changes of that database. The endpoint accepts only letters, digits and"
                           + " underscores in the name.",
             label = "common")
    @Metadata(required = true)
    private String database;
    @UriPath
    @Metadata(description = "The name of the logical replication slot to read the changes from. PostgreSQL accepts only lower"
                            + " case letters, digits and underscores in a slot name.",
              label = "common", required = true)
    private String slot;
    @UriPath
    @Metadata(description = "The name of the logical decoding output plugin, for example test_decoding, wal2json or pgoutput,"
                            + " which sets the format of the messages. The consumer uses it only when it creates the slot (see"
                            + " autoCreateSlot): an existing slot keeps the plugin that it was created with.",
              label = "common", required = true)
    private String outputPlugin;
    @UriParam(description = "The name of the database user. The user needs the REPLICATION attribute, or must be a"
                            + " superuser.",
              label = "common", defaultValue = "postgres")
    private String user = "postgres";
    @UriParam(description = "The password of the database user.", label = "common", security = "secret")
    private String password;
    @UriParam(description = "The number of seconds between the status updates that the consumer sends to the PostgreSQL"
                            + " server, which report the position that it has received and flushed. While a message is"
                            + " routed, the consumer also sends a status update at this interval, so that the server does not"
                            + " close the connection when routing takes longer than wal_sender_timeout.",
              label = "advanced", defaultValue = "10")
    private Integer statusInterval = 10;
    @UriParam(description = "The options that the consumer passes to the output plugin when it starts reading from the slot,"
                            + " for example slotOptions.include-xids=false for test_decoding, or slotOptions.proto_version=1"
                            + " and slotOptions.publication_names=mypub for pgoutput. The plugin defines which options exist.",
              label = "advanced", prefix = "slotOptions.", multiValue = true)
    private Map<String, Object> slotOptions = Collections.emptyMap();
    @UriParam(description = "Whether the consumer creates a logical replication slot with the outputPlugin when it connects"
                            + " and no slot of this name exists. If false, the slot must exist, otherwise each poll fails. The"
                            + " consumer never drops the slot, so PostgreSQL keeps the WAL that the slot has not confirmed, also"
                            + " while the route is stopped.",
              label = "advanced", defaultValue = "true")
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

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getOutputPlugin() {
        return outputPlugin;
    }

    public void setOutputPlugin(String outputPlugin) {
        this.outputPlugin = outputPlugin;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getStatusInterval() {
        return statusInterval;
    }

    public void setStatusInterval(Integer statusInterval) {
        this.statusInterval = statusInterval;
    }

    public Map<String, Object> getSlotOptions() {
        return slotOptions;
    }

    public void setSlotOptions(Map<String, Object> slotOptions) {
        this.slotOptions = slotOptions;
    }

    public Boolean getAutoCreateSlot() {
        return autoCreateSlot;
    }

    public void setAutoCreateSlot(Boolean autoCreateSlot) {
        this.autoCreateSlot = autoCreateSlot;
    }
}
