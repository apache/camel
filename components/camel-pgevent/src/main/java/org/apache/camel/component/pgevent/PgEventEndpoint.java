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
package org.apache.camel.component.pgevent;

import java.sql.DriverManager;

import javax.sql.DataSource;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.jdbc.PGDriver;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive PostgreSQL events via LISTEN and NOTIFY commands.
 * <p/>
 * This requires using PostgreSQL 8.3 or newer.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "pgevent", title = "PostgresSQL Event",
             syntax = "pgevent:host:port/database/channel",
             category = { Category.DATABASE }, headersClass = PgEventConstants.class)
public class PgEventEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PgEventEndpoint.class);

    private static final String FORMAT1 = "^pgevent://([^:]*):(\\d+)/(.+)/(\\w+).*$";
    private static final String FORMAT2 = "^pgevent://([^:]+)/(.+)/(\\w+).*$";
    private static final String FORMAT3 = "^pgevent:///(.+)/(\\w+).*$";
    private static final String FORMAT4 = "^pgevent:(.+)/(\\w+)/(\\w+).*$";

    @UriPath(defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(defaultValue = "5432")
    private Integer port = 5432;
    @UriPath
    @Metadata(required = true)
    private String database;
    @UriPath
    @Metadata(required = true)
    private String channel;
    @UriParam(defaultValue = "postgres", label = "security", secret = true)
    private String user = "postgres";
    @UriParam(label = "security", secret = true)
    private String pass;
    @UriParam
    private DataSource datasource;

    private final String uri;

    public PgEventEndpoint(String uri, PgEventComponent component) {
        super(uri, component);
        this.uri = uri;
        parseUri();
    }

    public PgEventEndpoint(String uri, PgEventComponent component, DataSource dataSource) {
        super(uri, component);
        this.uri = uri;
        this.datasource = dataSource;
        parseUri();
    }

    public final PGConnection initJdbc() throws Exception {
        PGConnection conn;
        if (this.getDatasource() != null) {
            conn = PgEventHelper.toPGConnection(this.getDatasource().getConnection());
        } else {
            // ensure we can load the class
            ClassResolver classResolver = getCamelContext().getClassResolver();
            classResolver.resolveMandatoryClass(PGDriver.class.getName(), PgEventComponent.class.getClassLoader());
            conn = (PGConnection) DriverManager.getConnection(
                    "jdbc:pgsql://" + this.getHost() + ":" + this.getPort() + "/" + this.getDatabase(), this.getUser(),
                    this.getPass());
        }
        return conn;
    }

    /**
     * Parse the provided URI and extract available parameters
     *
     * @throws IllegalArgumentException if there is an error in the parameters
     */
    protected final void parseUri() throws IllegalArgumentException {
        LOG.debug("URI: {}", uri);
        if (uri.matches(FORMAT1)) {
            LOG.trace("FORMAT1");
            String[] parts = uri.replaceFirst(FORMAT1, "$1:$2:$3:$4").split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
            database = parts[2];
            channel = parts[3];
        } else if (uri.matches(FORMAT2)) {
            LOG.trace("FORMAT2");
            String[] parts = uri.replaceFirst(FORMAT2, "$1:$2:$3").split(":");
            host = parts[0];
            port = 5432;
            database = parts[1];
            channel = parts[2];
        } else if (uri.matches(FORMAT3)) {
            LOG.trace("FORMAT3");
            String[] parts = uri.replaceFirst(FORMAT3, "$1:$2").split(":");
            host = "localhost";
            port = 5432;
            database = parts[0];
            channel = parts[1];
        } else if (uri.matches(FORMAT4)) {
            LOG.trace("FORMAT4");
            String[] parts = uri.replaceFirst(FORMAT4, "$1:$2").split(":");
            database = parts[0];
            channel = parts[1];
        } else {
            throw new IllegalArgumentException("The provided URL does not match the acceptable patterns.");
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        validateInputs();
        return new PgEventProducer(this);
    }

    private void validateInputs() throws IllegalArgumentException {
        if (getChannel() == null || getChannel().isEmpty()) {
            throw new IllegalArgumentException("A required parameter was not set when creating this Endpoint (channel)");
        }

        if (datasource == null && user == null) {
            throw new IllegalArgumentException(
                    "A required parameter was "
                                               + "not set when creating this Endpoint (pgUser or pgDataSource)");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        validateInputs();
        PgEventConsumer consumer = new PgEventConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getHost() {
        return host;
    }

    /**
     * To connect using hostname and port to the database.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * To connect using hostname and port to the database.
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * The database name. The database name can take any characters because it is sent as a quoted identifier. It is
     * part of the endpoint URI, so diacritical marks and non-Latin letters have to be URL encoded.
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getChannel() {
        return channel;
    }

    /**
     * The channel name
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUser() {
        return user;
    }

    /**
     * Username for login
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    /**
     * Password for login
     */
    public void setPass(String pass) {
        this.pass = pass;
    }

    public DataSource getDatasource() {
        return datasource;
    }

    /**
     * To connect using the given {@link javax.sql.DataSource} instead of using hostname and port.
     */
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }
}
