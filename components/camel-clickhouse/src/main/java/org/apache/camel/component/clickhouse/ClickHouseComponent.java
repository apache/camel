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

import java.util.Map;

import com.clickhouse.client.api.Client;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("clickhouse")
public class ClickHouseComponent extends DefaultComponent {

    @Metadata(autowired = true)
    private Client client;
    @Metadata(description = "The ClickHouse HTTP endpoint URL, e.g. http://localhost:8123. Can be overridden per endpoint."
                            + " Required unless a shared Client bean is autowired or configured on the component.")
    private String serverUrl;
    @Metadata(label = "security", defaultValue = "default", description = "The username used to authenticate to ClickHouse.")
    private String username = "default";
    @Metadata(label = "security", secret = true, description = "The password used to authenticate to ClickHouse.")
    private String password;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether to connect to ClickHouse over a secure (HTTPS) connection.")
    private boolean ssl;
    @Metadata(defaultValue = "false",
              description = "Whether to compress the insert request payload sent to the server (LZ4).")
    private boolean compression;

    public ClickHouseComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "A database (and optionally a table) must be configured on the URI path, e.g. clickhouse://database.table");
        }

        ClickHouseEndpoint endpoint = new ClickHouseEndpoint(uri, this);
        endpoint.setClient(client);
        if (ObjectHelper.isNotEmpty(serverUrl)) {
            endpoint.setServerUrl(serverUrl);
        }
        endpoint.setUsername(username);
        endpoint.setPassword(password);
        endpoint.setSsl(ssl);
        endpoint.setCompression(compression);

        String database = remaining;
        String table = null;
        int dot = remaining.indexOf('.');
        if (dot > 0) {
            database = remaining.substring(0, dot);
            table = remaining.substring(dot + 1);
        }
        endpoint.setDatabase(database);
        if (ObjectHelper.isNotEmpty(table)) {
            endpoint.setTable(table);
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public Client getClient() {
        return client;
    }

    /**
     * The shared ClickHouse client to use for all endpoints, of type {@code com.clickhouse.client.api.Client}. When
     * set, the endpoint-level connection options (serverUrl, username, password, ssl) are ignored.
     */
    public void setClient(Client client) {
        this.client = client;
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

    public boolean isCompression() {
        return compression;
    }

    public void setCompression(boolean compression) {
        this.compression = compression;
    }
}
