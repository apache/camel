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
import java.util.List;
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

    private static final List<String> SSL_MODES
            = List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

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
    @UriParam(description = "The SSL mode of the connection to the PostgreSQL server: disable, allow, prefer, require,"
                            + " verify-ca or verify-full. When not set, the default of the PostgreSQL JDBC driver applies,"
                            + " which is prefer: TLS when the server offers it, without checking the server certificate, and"
                            + " a plain connection otherwise. Use verify-full to require TLS and check both the certificate"
                            + " and the host name of the server.",
              label = "security", enums = "disable,allow,prefer,require,verify-ca,verify-full")
    private String sslMode;
    @UriParam(description = "The file of the root certificates (PEM) that the server certificate is checked against with"
                            + " sslMode verify-ca or verify-full. When not set, the PostgreSQL JDBC driver reads"
                            + " root.crt in the .postgresql folder of the home directory of the user.",
              label = "security")
    private String sslRootCert;
    @UriParam(description = "The file of the client certificate (PEM) for a server that checks client certificates. When"
                            + " not set, the PostgreSQL JDBC driver reads postgresql.crt in the .postgresql folder of the home"
                            + " directory of the user, if it exists.",
              label = "security")
    private String sslCert;
    @UriParam(description = "The file of the private key of the client certificate. The PostgreSQL JDBC driver accepts"
                            + " PKCS-8 in DER format, PKCS-8 in PEM format (a .pem file, or a file that starts with"
                            + " -----BEGIN PRIVATE KEY-----), or a PKCS-12 file (.p12 or .pfx) that holds the certificate"
                            + " and the key."
                            + " When not set, the driver reads postgresql.pk8 in the .postgresql folder of the home directory"
                            + " of the user, if it exists.",
              label = "security")
    private String sslKey;
    @UriParam(description = "The password of the private key in sslKey.", label = "security", security = "secret")
    private String sslPassword;

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
        return DriverManager.getConnection(
                String.format("jdbc:postgresql://%s:%d/%s", this.getHost(), this.getPort(), this.getDatabase()),
                createConnectionProperties());
    }

    /**
     * The properties of the JDBC connection.
     */
    Properties createConnectionProperties() {
        Properties props = new Properties();

        PGProperty.USER.set(props, this.getUser());
        PGProperty.PASSWORD.set(props, this.getPassword());
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, "9.6");
        PGProperty.REPLICATION.set(props, "database");
        PGProperty.PREFER_QUERY_MODE.set(props, "simple");
        PGProperty.TCP_KEEP_ALIVE.set(props, true);

        // TLS: only the options that are set, so the defaults of the driver apply otherwise
        if (sslMode != null) {
            PGProperty.SSL_MODE.set(props, sslMode);
        }
        if (sslRootCert != null) {
            PGProperty.SSL_ROOT_CERT.set(props, sslRootCert);
        }
        if (sslCert != null) {
            PGProperty.SSL_CERT.set(props, sslCert);
        }
        if (sslKey != null) {
            PGProperty.SSL_KEY.set(props, sslKey);
        }
        if (sslPassword != null) {
            PGProperty.SSL_PASSWORD.set(props, sslPassword);
        }

        return props;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (sslMode != null && !SSL_MODES.contains(sslMode)) {
            throw new IllegalArgumentException(
                    "Invalid sslMode: " + sslMode + ". Use one of: " + String.join(", ", SSL_MODES));
        }
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

    public String getSslMode() {
        return sslMode;
    }

    public void setSslMode(String sslMode) {
        this.sslMode = sslMode;
    }

    public String getSslRootCert() {
        return sslRootCert;
    }

    public void setSslRootCert(String sslRootCert) {
        this.sslRootCert = sslRootCert;
    }

    public String getSslCert() {
        return sslCert;
    }

    public void setSslCert(String sslCert) {
        this.sslCert = sslCert;
    }

    public String getSslKey() {
        return sslKey;
    }

    public void setSslKey(String sslKey) {
        this.sslKey = sslKey;
    }

    public String getSslPassword() {
        return sslPassword;
    }

    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }
}
