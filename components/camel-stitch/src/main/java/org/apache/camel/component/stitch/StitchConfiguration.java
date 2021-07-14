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
package org.apache.camel.component.stitch;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.StitchRegion;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@UriParams
public class StitchConfiguration implements Cloneable {

    @UriPath
    private String tableName;
    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String token;
    @UriParam(label = "producer", defaultValue = "EUROPE")
    private StitchRegion region = StitchRegion.EUROPE;
    @UriParam(label = "producer")
    @Metadata(autowired = true)
    private StitchSchema stitchSchema;
    @UriParam(label = "producer")
    private String keyNames;
    @UriParam(label = "producer,advanced")
    @Metadata(autowired = true)
    private HttpClient httpClient;
    @UriParam(label = "producer,advanced")
    @Metadata(autowired = true)
    private ConnectionProvider connectionProvider;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private StitchClient stitchClient;

    /**
     * The name of the destination table the data is being pushed to. Table names must be unique in each destination
     * schema, or loading issues will occur.
     *
     * Note: The number of characters in the table name should be within the destination's allowed limits or data will
     * rejected.
     */
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Stitch access token for the Stitch Import API
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Stitch account region, e.g: europe
     */
    public StitchRegion getRegion() {
        return region;
    }

    public void setRegion(StitchRegion region) {
        this.region = region;
    }

    /**
     * A schema that describes the record(s)
     */
    public StitchSchema getStitchSchema() {
        return stitchSchema;
    }

    public void setStitchSchema(StitchSchema stitchSchema) {
        this.stitchSchema = stitchSchema;
    }

    /**
     * A collection of comma separated strings representing the Primary Key fields in the source table. Stitch use these
     * Primary Keys to de-dupe data during loading If not provided, the table will be loaded in an append-only manner.
     */
    public String getKeyNames() {
        return keyNames;
    }

    public void setKeyNames(String keyNames) {
        this.keyNames = keyNames;
    }

    /**
     * Reactor Netty HttpClient, you can injected it if you want to have custom HttpClient
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * ConnectionProvider contain configuration for the HttpClient like Maximum connection limit .. etc, you can inject
     * this ConnectionProvider and the StitchClient will initialize HttpClient with this ConnectionProvider
     */
    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    public void setConnectionProvider(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Set a custom StitchClient that implements org.apache.camel.component.stitch.client.StitchClient interface
     */
    public StitchClient getStitchClient() {
        return stitchClient;
    }

    public void setStitchClient(StitchClient stitchClient) {
        this.stitchClient = stitchClient;
    }

    // *************************************************
    //
    // *************************************************

    public StitchConfiguration copy() {
        try {
            return (StitchConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
