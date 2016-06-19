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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;

/**
 * Created by jose on 18/06/16.
 */
public class InfluxDbProducer extends DefaultProducer {

    InfluxDbEndpoint endpoint;

    public InfluxDbProducer(InfluxDbEndpoint endpoint) {
        super(endpoint);
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
        Point p = exchange.getIn().getMandatoryBody(Point.class);

        InfluxDB conn = endpoint.getInfluxDB();

        try {
            conn.write(dataBaseName, retentionPolicy, p);
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDbException(ex));
        }
    }

    private String calculateRetentionPolicy(Exchange exchange) {
        String retentionPolicy = exchange.getIn().getHeader(InfluxDbConstants.RETENTION_POLICY_HEADER, String.class);

        if (retentionPolicy != null) {
            return retentionPolicy;
        }

        return endpoint.getRetentionPolicy();
    }

    private String calculateDatabaseName(Exchange exchange) {
        String dbName = exchange.getIn().getHeader(InfluxDbConstants.DBNAME_HEADER, String.class);

        if (dbName != null) {
            return dbName;
        }

        return endpoint.getDatabaseName();
    }

}
