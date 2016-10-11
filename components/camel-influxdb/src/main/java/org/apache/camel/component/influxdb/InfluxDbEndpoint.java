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
package org.apache.camel.component.influxdb;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.influxdb.InfluxDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The influxdb component allows you to interact with <a href="https://influxdata.com/time-series-platform/influxdb/">InfluxDB</a>, a time series database.
 */
@UriEndpoint(scheme = "influxdb", title = "InfluxDB", syntax = "influxdb:connectionBean", label = "database,ticks", producerOnly = true)
public class InfluxDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbEndpoint.class);

    private InfluxDB influxDB;

    @UriPath
    @Metadata(required = "true", description = "Connection to the influx database, of class InfluxDB.class")
    private String connectionBean;

    @UriParam(description = "the name of the series where the points will be created, name can be modified dynamically by headers")
    private String databaseName;

    @UriParam(defaultValue = "default", description = "defines the retention policy for the points created in influxdb")
    private String retentionPolicy = "default";

    /**
     * @param uri
     * @param influxDbComponent
     */
    public InfluxDbEndpoint(String uri, InfluxDbComponent influxDbComponent, InfluxDB dbConn) {
        super(uri, influxDbComponent);

        if (dbConn == null) {
            throw new IllegalArgumentException("dbConn is null");
        }

        this.influxDB = dbConn;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Prepairing influxdb enpoint with uri {}", uri);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating influx db producer connectionBean:{}, databaseName:{}, retentionPolicy:{}", connectionBean, databaseName, retentionPolicy);
        }

    }

    @Override
    public Producer createProducer() throws Exception {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating influx db producer");
        }
        return new InfluxDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating influx db consumer");
        }
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public boolean isSingleton() {
        return false;
    }



    public InfluxDB getInfluxDB() {
        return influxDB;
    }

    public void setInfluxDB(InfluxDB influxDB) {
        this.influxDB = influxDB;
    }

    /**
     * Getter for databaseName
     * 
     * @return the name of the database where the time series will be stored
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Setter for databaseName
     * 
     * @param databaseName
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Getter for retentionPolicy
     * 
     * @return the string that defines the retention policy to the data created
     *         by the endpoint
     */
    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    /**
     * Setter for retentionPolicy
     * 
     * @param retentionPolicy
     */
    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    /**
     * Getter for connectionBean
     * 
     * @return the name of the bean for the {@link org.influxdb.InfluxDB}
     *         connection
     */
    public String getConnectionBean() {
        return connectionBean;
    }

    /**
     * Name of {@link org.influxdb.InfluxDB} to use.
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }
}
