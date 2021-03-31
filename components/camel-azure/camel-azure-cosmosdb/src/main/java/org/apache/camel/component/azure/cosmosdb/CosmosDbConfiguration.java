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
package org.apache.camel.component.azure.cosmosdb;

import com.azure.cosmos.CosmosAsyncClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CosmosDbConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String database;
    @UriPath
    private String container;
    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String accountKey;
    @UriParam(label = "common")
    @Metadata(required = true)
    private String databaseEndpoint;
    @UriParam(label = "common")
    @Metadata(autowired = true)
    private CosmosAsyncClient cosmosAsyncClient;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean createDatabaseIfNotExists = false;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean createContainerIfNotExists = false;

    public CosmosDbConfiguration() {
    }

    /**
     * The name of the Cosmos database that component should connect to. In case you are producing data and have
     * createDatabaseIfNotExists=true, the component will automatically auto create a Cosmos database.
     */
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    /**
     * The name of the Cosmos container that component should connect to. In case you are producing data and have
     * createContainerIfNotExists=true, the component will automatically auto create a Cosmos container.
     */
    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    /**
     * Sets either a master or readonly key used to perform authentication for accessing resource.
     */
    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    /**
     * Sets the Azure Cosmos database endpoint the component will connect to.
     */
    public String getDatabaseEndpoint() {
        return databaseEndpoint;
    }

    public void setDatabaseEndpoint(String databaseEndpoint) {
        this.databaseEndpoint = databaseEndpoint;
    }

    /**
     * Sets if the component should create Cosmos database automatically in case it doesn't exist in Cosmos account
     */
    public boolean isCreateDatabaseIfNotExists() {
        return createDatabaseIfNotExists;
    }

    public void setCreateDatabaseIfNotExists(boolean createDatabaseIfNotExists) {
        this.createDatabaseIfNotExists = createDatabaseIfNotExists;
    }

    /**
     * Sets if the component should create Cosmos container automatically in case it doesn't exist in Cosmos database
     */
    public boolean isCreateContainerIfNotExists() {
        return createContainerIfNotExists;
    }

    public void setCreateContainerIfNotExists(boolean createContainerIfNotExists) {
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    /**
     * Inject an external {@link CosmosAsyncClient} into the component
     */
    public CosmosAsyncClient getCosmosAsyncClient() {
        return cosmosAsyncClient;
    }

    public void setCosmosAsyncClient(CosmosAsyncClient cosmosAsyncClient) {
        this.cosmosAsyncClient = cosmosAsyncClient;
    }

    // *************************************************
    //
    // *************************************************

    public CosmosDbConfiguration copy() {
        try {
            return (CosmosDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
