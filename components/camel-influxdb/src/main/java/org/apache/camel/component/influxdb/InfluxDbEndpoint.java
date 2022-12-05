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
package org.apache.camel.component.influxdb;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with <a href="https://influxdata.com/time-series-platform/influxdb/">InfluxDB</a> v1, a time series
 * database.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "influxdb", title = "InfluxDB", syntax = "influxdb:connectionBean",
             category = { Category.DATABASE }, producerOnly = true, headersClass = InfluxDbConstants.class)
public class InfluxDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbEndpoint.class);
    private static final String CREATE_DATABASE = "CREATE DATABASE ";
    private static final String SHOW_DATABASES = "SHOW DATABASES";

    private InfluxDB influxDB;

    @UriPath
    @Metadata(required = true)
    private String connectionBean;
    @UriParam
    private String databaseName;
    @UriParam(defaultValue = "default")
    private String retentionPolicy = "default";
    @UriParam(defaultValue = "false")
    private boolean batch;
    @UriParam(defaultValue = InfluxDbOperations.INSERT)
    private String operation = InfluxDbOperations.INSERT;
    @UriParam
    private String query;
    @UriParam(defaultValue = "false")
    private boolean checkDatabaseExistence;
    @UriParam(defaultValue = "false")
    private boolean autoCreateDatabase;

    public InfluxDbEndpoint(String uri, InfluxDbComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new InfluxDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (checkDatabaseExistence) {
            ensureDatabaseExists();
        }

    }

    public InfluxDB getInfluxDB() {
        return influxDB;
    }

    /**
     * The Influx DB to use
     */
    public void setInfluxDB(InfluxDB influxDB) {
        this.influxDB = influxDB;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * The name of the database where the time series will be stored
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    /**
     * The string that defines the retention policy to the data created by the endpoint
     */
    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public String getConnectionBean() {
        return connectionBean;
    }

    /**
     * Connection to the influx database, of class InfluxDB.class
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    public boolean isBatch() {
        return batch;
    }

    /**
     * Define if this operation is a batch operation or not
     */
    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Define if this operation is an insert or a query
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getQuery() {
        return query;
    }

    /**
     * Define the query in case of operation query
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isCheckDatabaseExistence() {
        return checkDatabaseExistence;
    }

    /**
     * Define if we want to check the database existence while starting the endpoint
     */
    public void setCheckDatabaseExistence(boolean checkDatabaseExistence) {
        this.checkDatabaseExistence = checkDatabaseExistence;
    }

    public boolean isAutoCreateDatabase() {
        return autoCreateDatabase;
    }

    /**
     * Define if we want to auto create the database if it's not present
     */
    public void setAutoCreateDatabase(boolean autoCreateDatabase) {
        this.autoCreateDatabase = autoCreateDatabase;
    }

    private void ensureDatabaseExists() {
        QueryResult result = getInfluxDB().query(new Query(SHOW_DATABASES));

        //values are located in the first item in series, where list of values is the first item in the Serie's values,
        //if any object on the 'path' is null, database does not exist
        boolean exists;
        try {
            //NPE could be thrown from objects deep in the structure.
            //try catch block with NullPointerException is used on purpose
            exists = result.getResults().get(0).getSeries().get(0).getValues().get(0).contains(databaseName);
        } catch (NullPointerException e) {
            exists = false;
        }

        if (!exists) {
            if (autoCreateDatabase) {
                LOG.debug("Database {} doesn't exist. Creating it...", databaseName);
                getInfluxDB().query(new Query(CREATE_DATABASE + databaseName, ""));
            }
        }
    }
}
