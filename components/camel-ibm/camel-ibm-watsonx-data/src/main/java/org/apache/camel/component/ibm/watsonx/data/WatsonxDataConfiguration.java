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
package org.apache.camel.component.ibm.watsonx.data;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for IBM watsonx.data component.
 */
@UriParams
public class WatsonxDataConfiguration implements Cloneable {

    // Authentication
    @UriParam(label = "security", security = "secret",
              description = "The IBM Cloud API key, exchanged at IBM Cloud IAM for an access token that is sent to"
                            + " watsonx.data. Required unless oauthProfile is set; when both are set, oauthProfile is"
                            + " used.")
    private String apiKey;

    @UriParam(label = "security",
              description = "OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant."
                            + " When set, the token of the configured identity provider is sent to watsonx.data as a Bearer"
                            + " token instead of an IBM Cloud API key, and obtained again when it expires. Requires"
                            + " camel-oauth on the classpath.")
    private String oauthProfile;

    // Connection
    @UriParam(label = "common",
              description = "The base URL of the watsonx.data REST API, to which the IBM SDK appends the resource paths,"
                            + " for example https://us-south.lakehouse.cloud.ibm.com/lakehouse/api/v3. The endpoint does"
                            + " not start without it.")
    @Metadata(required = true)
    private String serviceUrl;

    // Operation
    @UriParam(label = "producer",
              description = "The operation to perform. The CamelIBMWatsonxDataOperation header overrides it; when neither"
                            + " is set, the exchange fails.")
    private WatsonxDataOperations operation;

    // Catalog
    @UriParam(label = "producer",
              description = "The name of the catalog for getCatalog, deleteCatalog, getAllColumns and the schema and"
                            + " table operations, except registerTable, which takes the catalog ID header instead. The"
                            + " CamelIBMWatsonxDataCatalogName header overrides it.")
    private String catalogName;

    // Schema
    @UriParam(label = "producer",
              description = "The name of the schema for deleteSchema and the table operations, except registerTable;"
                            + " getAllColumns returns only the columns of this schema. The"
                            + " CamelIBMWatsonxDataSchemaName header overrides it. createSchema ignores this option and"
                            + " takes the name of the new schema from the header only.")
    private String schemaName;

    // Engine
    @UriParam(label = "producer",
              description = "The ID of the engine that the schema and table operations, except registerTable, run on,"
                            + " and of the engine that getPrestoEngine and getPrestissimoEngine return. The"
                            + " CamelIBMWatsonxDataEngineId header overrides it.")
    private String engineId;

    // Auth Instance ID
    @UriParam(label = "common",
              description = "The ID of the watsonx.data instance (on IBM Cloud, the CRN of the instance), sent in the"
                            + " AuthInstanceId HTTP header of every request. The CamelIBMWatsonxDataAuthInstanceId"
                            + " header overrides it; when neither is set, the request has no AuthInstanceId header.")
    private String authInstanceId;

    public WatsonxDataConfiguration copy() {
        try {
            return (WatsonxDataConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    // Getters and Setters

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public WatsonxDataOperations getOperation() {
        return operation;
    }

    public void setOperation(WatsonxDataOperations operation) {
        this.operation = operation;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getEngineId() {
        return engineId;
    }

    public void setEngineId(String engineId) {
        this.engineId = engineId;
    }

    public String getAuthInstanceId() {
        return authInstanceId;
    }

    public void setAuthInstanceId(String authInstanceId) {
        this.authInstanceId = authInstanceId;
    }
}
