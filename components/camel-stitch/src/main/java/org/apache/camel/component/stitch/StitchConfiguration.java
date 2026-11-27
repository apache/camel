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

    @UriPath(description = "The name of the destination table that the records are loaded into. The CamelStitchTableName"
                           + " header overrides it, and it overrides the table_name of a StitchRequestBody or Map body."
                           + " Table names must be unique in each destination schema, and Stitch rejects the data when the"
                           + " name is longer than the destination allows.")
    private String tableName;
    @UriParam(label = "security", security = "secret",
              description = "The access token of the Stitch Import API, sent as a Bearer token. It is required, also when"
                            + " stitchClient is set.")
    @Metadata(required = true)
    private String token;
    @UriParam(label = "producer", defaultValue = "EUROPE",
              description = "The region of the Stitch account, which selects the host of the Import API: EUROPE"
                            + " (api.eu-central-1.stitchdata.com) or NORTH_AMERICA (api.stitchdata.com). Not used when"
                            + " stitchClient is set.")
    private StitchRegion region = StitchRegion.EUROPE;
    @UriParam(label = "producer",
              description = "The JSON schema that describes the records of the batch; Stitch rejects records that do not"
                            + " conform to it. The CamelStitchSchema header overrides it, and it overrides the schema of a"
                            + " StitchRequestBody or Map body.")
    @Metadata(autowired = true)
    private StitchSchema stitchSchema;
    @UriParam(label = "producer",
              description = "The Primary Key fields of the source table, as a comma separated list without spaces. Stitch"
                            + " uses them to de-duplicate the records when it loads them, and loads the table append-only"
                            + " when there are none. The CamelStitchKeyNames header overrides it, and the names are added"
                            + " to the key_names of a StitchRequestBody or Map body.")
    private String keyNames;
    @UriParam(label = "producer,advanced",
              description = "A custom Reactor Netty HttpClient that sends the requests. The endpoint still sets the host"
                            + " of the region and the Authorization and Content-Type headers on it, and does not use"
                            + " connectionProvider. Not used when stitchClient is set.")
    @Metadata(autowired = true)
    private HttpClient httpClient;
    @UriParam(label = "producer,advanced",
              description = "A Reactor Netty ConnectionProvider, for example a pool with a maximum number of connections,"
                            + " that the endpoint creates its HttpClient with. Not used when httpClient or stitchClient is"
                            + " set.")
    @Metadata(autowired = true)
    private ConnectionProvider connectionProvider;
    @UriParam(label = "advanced",
              description = "A custom implementation of the org.apache.camel.component.stitch.client.StitchClient"
                            + " interface that sends the batches. When set, the region, httpClient and connectionProvider"
                            + " options are not used, and the endpoint does not close the client when it stops.")
    @Metadata(autowired = true)
    private StitchClient stitchClient;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public StitchRegion getRegion() {
        return region;
    }

    public void setRegion(StitchRegion region) {
        this.region = region;
    }

    public StitchSchema getStitchSchema() {
        return stitchSchema;
    }

    public void setStitchSchema(StitchSchema stitchSchema) {
        this.stitchSchema = stitchSchema;
    }

    public String getKeyNames() {
        return keyNames;
    }

    public void setKeyNames(String keyNames) {
        this.keyNames = keyNames;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    public void setConnectionProvider(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

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
