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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.influxdb.InfluxDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The influxdb component allows you to interact with <a href="https://influxdata.com/time-series-platform/influxdb/">InfluxDB</a>, a time series database.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "influxdb", title = "InfluxDB", syntax = "influxdb:connectionBean", label = "database", producerOnly = true)
public class InfluxDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbEndpoint.class);

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
}
