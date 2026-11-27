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

import java.util.Locale;
import java.util.Set;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
 * Write points to and run queries on <a href="https://influxdata.com/time-series-platform/influxdb/">InfluxDB</a> v1, a
 * time series database.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "influxdb", title = "InfluxDB", syntax = "influxdb:connectionBean",
             category = { Category.DATABASE }, producerOnly = true, headersClass = InfluxDbConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.POJO, Carrier.MAP },
                                  byOption = {
                                          @BodyType.When(option = "batch", value = "true", carriers = Carrier.POJO,
                                                         javaType = "org.influxdb.dto.BatchPoints"),
                                          @BodyType.When(option = "operation", value = "query", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "ping", carriers = Carrier.ANY) },
                                  description = "The org.influxdb.dto.Point to write, or a Map of the field names and their"
                                                + " values with the measurement name under the camelInfluxDB.MeasurementName"
                                                + " key. Text, bytes or a List of Points fail: line protocol is not parsed."
                                                + " With batch=true the body must be a BatchPoints. The query and ping"
                                                + " operations do not read the body: the query comes from the"
                                                + " camelInfluxDB.query header or the query option"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "query", carriers = Carrier.POJO,
                                                          javaType = "org.influxdb.dto.QueryResult"),
                                           @BodyType.When(option = "operation", value = "ping", carriers = Carrier.POJO,
                                                          javaType = "org.influxdb.dto.Pong") },
                                   description = "An insert leaves the body unchanged. A query gives the QueryResult of"
                                                 + " InfluxDB; an error the server reports in it (such as an unknown"
                                                 + " database) fails the exchange instead. Ping gives the Pong with the"
                                                 + " version of the server"))
public class InfluxDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbEndpoint.class);
    private static final String CREATE_DATABASE = "CREATE DATABASE ";
    private static final String SHOW_DATABASES = "SHOW DATABASES";
    private static final Set<String> OPERATIONS
            = Set.of(InfluxDbOperations.INSERT, InfluxDbOperations.QUERY, InfluxDbOperations.PING);

    private InfluxDB influxDB;

    @UriPath(description = "The name of the org.influxdb.InfluxDB client bean in the registry that the endpoint uses. The"
                           + " bean is not looked up when the influxDB component option is set or autowired: that client is"
                           + " used instead.")
    @Metadata(required = true)
    private String connectionBean;
    @UriParam(description = "The name of the database that the producer writes points to and runs queries on. The"
                            + " camelInfluxDB.databaseName header replaces this option. If neither is set, the database set"
                            + " on the InfluxDB client is used. A BatchPoints body carries its own database.")
    private String databaseName;
    @UriParam(description = "The name of the retention policy that the producer writes a single point to. InfluxDB takes the"
                            + " value as the name of a policy of the database, so the default works only on a database with a"
                            + " policy named default (InfluxDB 1.0 and later name the policy they create autogen). The"
                            + " camelInfluxDB.RetentionPolicy header replaces this option. A BatchPoints body carries its own"
                            + " retention policy.",
              defaultValue = "default")
    private String retentionPolicy = "default";
    @UriParam(description = "Whether the insert operation writes an org.influxdb.dto.BatchPoints body in one request, instead"
                            + " of a single org.influxdb.dto.Point. The database and the retention policy of the BatchPoints"
                            + " are used, not the databaseName and retentionPolicy options or their headers.",
              defaultValue = "false")
    private boolean batch;
    @UriParam(description = "The operation that the producer performs: insert writes the body to InfluxDB, query runs the"
                            + " query and gives its QueryResult as the body, and ping gives the Pong of the server as the body."
                            + " The value is not case-sensitive, and another value fails the start of the endpoint.",
              defaultValue = InfluxDbOperations.INSERT, enums = "insert,query,ping")
    private String operation = InfluxDbOperations.INSERT;
    @UriParam(description = "The InfluxQL query that the query operation runs. The camelInfluxDB.query header replaces this"
                            + " option, and the query operation fails if neither is set.")
    private String query;
    @UriParam(description = "Whether to run SHOW DATABASES when the endpoint starts, to check that the database of the"
                            + " databaseName option exists. It has an effect only together with autoCreateDatabase: on its own"
                            + " a missing database is neither reported nor created.",
              defaultValue = "false")
    private boolean checkDatabaseExistence;
    @UriParam(description = "Whether to create the database of the databaseName option when the endpoint starts and the"
                            + " database does not exist. It is used only when checkDatabaseExistence is true.",
              defaultValue = "false")
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

        // an operation that is not supported fails the start of the endpoint, instead of its first message
        if (operation == null || !OPERATIONS.contains(operation.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "The operation " + operation + " is not supported: use one of insert, query or ping");
        }
        if (checkDatabaseExistence) {
            ensureDatabaseExists();
        }

    }

    public InfluxDB getInfluxDB() {
        return influxDB;
    }

    public void setInfluxDB(InfluxDB influxDB) {
        this.influxDB = influxDB;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public String getConnectionBean() {
        return connectionBean;
    }

    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    public boolean isBatch() {
        return batch;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isCheckDatabaseExistence() {
        return checkDatabaseExistence;
    }

    public void setCheckDatabaseExistence(boolean checkDatabaseExistence) {
        this.checkDatabaseExistence = checkDatabaseExistence;
    }

    public boolean isAutoCreateDatabase() {
        return autoCreateDatabase;
    }

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
