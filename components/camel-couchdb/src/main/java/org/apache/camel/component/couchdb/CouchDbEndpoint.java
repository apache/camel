/**
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
package org.apache.camel.component.couchdb;

import java.net.URI;

import com.google.gson.JsonObject;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.lightcouch.CouchDbClient;

@UriEndpoint(scheme = "couchdb", title = "CouchDB", syntax = "couchdb:protocol:hostname:port/database", consumerClass = CouchDbConsumer.class, label = "database,nosql")
public class CouchDbEndpoint extends DefaultEndpoint {

    public static final String DEFAULT_STYLE = "main_only";
    public static final long DEFAULT_HEARTBEAT = 30000;
    public static final int DEFAULT_PORT = 5984;

    private static final String URI_ERROR = "Invalid URI. Format must be of the form couchdb:http[s]://hostname[:port]/database?[options...]";

    @UriPath @Metadata(required = "true")
    private String protocol;
    @UriPath @Metadata(required = "true")
    private String hostname;
    @UriPath(defaultValue = "" + DEFAULT_PORT)
    private int port;
    @UriPath @Metadata(required = "true")
    private String database;
    @UriParam(defaultValue = DEFAULT_STYLE)
    private String style = DEFAULT_STYLE;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam(defaultValue = "" + DEFAULT_HEARTBEAT)
    private long heartbeat = DEFAULT_HEARTBEAT;
    @UriParam(defaultValue = "false")
    private boolean createDatabase;
    @UriParam(defaultValue = "true")
    private boolean deletes = true;
    @UriParam(defaultValue = "true")
    private boolean updates = true;

    public CouchDbEndpoint() {
    }

    public CouchDbEndpoint(String endpointUri, String remaining, CouchDbComponent component) throws Exception {
        super(endpointUri, component);

        URI uri = new URI(remaining);

        protocol = uri.getScheme();
        if (protocol == null) {
            throw new IllegalArgumentException(URI_ERROR);
        }

        port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();

        if (uri.getPath() == null || uri.getPath().trim().length() == 0) {
            throw new IllegalArgumentException(URI_ERROR);
        }
        database = uri.getPath().substring(1);

        hostname = uri.getHost();
        if (hostname == null) {
            throw new IllegalArgumentException(URI_ERROR);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CouchDbConsumer answer = new CouchDbConsumer(this, createClient(), processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CouchDbProducer(this, createClient());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Exchange createExchange(String seq, String id, JsonObject obj, boolean deleted) {
        Exchange exchange = super.createExchange();
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DATABASE, database);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_SEQ, seq);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, id);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, obj.get("_rev").getAsString());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_METHOD, deleted ? "DELETE" : "UPDATE");
        exchange.getIn().setBody(obj);
        return exchange;
    }

    protected CouchDbClientWrapper createClient() {
        return new CouchDbClientWrapper(new CouchDbClient(database, createDatabase, protocol, hostname, port, username, password));
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(long heartbeat) {
        this.heartbeat = heartbeat;
    }

    public boolean isCreateDatabase() {
        return createDatabase;
    }

    public void setCreateDatabase(boolean createDatabase) {
        this.createDatabase = createDatabase;
    }

    public boolean isDeletes() {
        return deletes;
    }

    public void setDeletes(boolean deletes) {
        this.deletes = deletes;
    }

    public boolean isUpdates() {
        return updates;
    }

    public void setUpdates(boolean updates) {
        this.updates = updates;
    }
}
