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

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for the InfluxDB components
 */
public class InfluxDbProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbProducer.class);

    InfluxDbEndpoint endpoint;
    InfluxDB connection;

    public InfluxDbProducer(InfluxDbEndpoint endpoint) {
        super(endpoint);
        this.connection = endpoint.getInfluxDB();
        this.endpoint = endpoint;
    }

    /**
     * Processes the message exchange
     *
     * @param exchange the message exchange
     * @throws Exception if an internal processing error has occurred.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String dataBaseName = calculateDatabaseName(exchange);
        String retentionPolicy = calculateRetentionPolicy(exchange);
        switch (endpoint.getOperation()) {
            case InfluxDbOperations.INSERT:
                doInsert(exchange, dataBaseName, retentionPolicy);
                break;
            case InfluxDbOperations.QUERY:
                doQuery(exchange, dataBaseName, retentionPolicy);
                break;
            case InfluxDbOperations.PING:
                doPing(exchange);
                break;
            default:
                throw new IllegalArgumentException("The operation " + endpoint.getOperation() + " is not supported");
        }
    }

    private void doInsert(Exchange exchange, String dataBaseName, String retentionPolicy) throws InvalidPayloadException {
        if (!endpoint.isBatch()) {
            Point p = exchange.getIn().getMandatoryBody(Point.class);
            try {
                LOG.debug("Writing point {}", p.lineProtocol());
                if (!connection.databaseExists(dataBaseName)) {
                    LOG.debug("Database {} doesn't exist. Creating it...", dataBaseName);
                    connection.createDatabase(dataBaseName);
                }
                connection.write(dataBaseName, retentionPolicy, p);
            } catch (Exception ex) {
                exchange.setException(new CamelInfluxDbException(ex));
            }
        } else {
            BatchPoints batchPoints = exchange.getIn().getMandatoryBody(BatchPoints.class);
            try {
                LOG.debug("Writing BatchPoints {}", batchPoints.lineProtocol());
                connection.write(batchPoints);
            } catch (Exception ex) {
                exchange.setException(new CamelInfluxDbException(ex));
            }
        }
    }

    private void doQuery(Exchange exchange, String dataBaseName, String retentionPolicy) {
        String query = calculateQuery(exchange);
        Query influxdbQuery = new Query(query, dataBaseName);
        QueryResult resultSet = connection.query(influxdbQuery);
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(resultSet);
    }

    private void doPing(Exchange exchange) {
        Pong result = connection.ping();
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(result);
    }

    private String calculateRetentionPolicy(Exchange exchange) {
        String retentionPolicy = exchange.getIn().getHeader(InfluxDbConstants.RETENTION_POLICY_HEADER, String.class);
        if (ObjectHelper.isNotEmpty(retentionPolicy)) {
            return retentionPolicy;
        }
        return endpoint.getRetentionPolicy();
    }

    private String calculateDatabaseName(Exchange exchange) {
        String dbName = exchange.getIn().getHeader(InfluxDbConstants.DBNAME_HEADER, String.class);
        if (ObjectHelper.isNotEmpty(dbName)) {
            return dbName;
        }
        return endpoint.getDatabaseName();
    }

    private String calculateQuery(Exchange exchange) {
        String query = exchange.getIn().getHeader(InfluxDbConstants.INFLUXDB_QUERY, String.class);
        if (ObjectHelper.isNotEmpty(query)) {
            return query;
        } else {
            query = endpoint.getQuery();
        }
        if (ObjectHelper.isEmpty(query)) {
            throw new IllegalArgumentException("The query option must be set if you want to run a query operation");
        }
        return query;
    }

}
