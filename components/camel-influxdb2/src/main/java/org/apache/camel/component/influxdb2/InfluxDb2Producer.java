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
package org.apache.camel.component.influxdb2;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.influxdb2.data.Measurement;
import org.apache.camel.component.influxdb2.data.Measurements;
import org.apache.camel.component.influxdb2.data.Points;
import org.apache.camel.component.influxdb2.data.Record;
import org.apache.camel.component.influxdb2.data.Records;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDb2Producer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDb2Producer.class);
    InfluxDb2Endpoint endpoint;

    InfluxDBClient connection;

    WriteApi writeApi;

    public InfluxDb2Producer(InfluxDb2Endpoint endpoint) {
        super(endpoint);
        this.connection = endpoint.getInfluxDBClient();
        this.endpoint = endpoint;
        this.writeApi = connection.makeWriteApi();
    }

    /**
     * Processes the message exchange
     *
     * @param  exchange  the message exchange
     * @throws Exception if an internal processing error has occurred.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String orgName = calculateOrgName(exchange);
        String bucketName = calculateBucketName(exchange);
        WritePrecision writePrecision = calculateWritePrecision(exchange);
        switch (endpoint.getOperation()) {
            case INSERT:
                doInsert(exchange, orgName, bucketName, writePrecision);
                break;
            case PING:
                doPing(exchange);
                break;
            default:
                throw new IllegalArgumentException("The operation " + endpoint.getOperation() + " is not supported");
        }
    }

    private void doInsert(Exchange exchange, String orgName, String bucketName, WritePrecision writePrecision)
            throws InvalidPayloadException {
        Object body = exchange.getIn().getBody();
        if (body instanceof Point) {
            insertPoint(exchange, orgName, bucketName);
        } else if (body instanceof Measurement) {
            insertMeasurement(exchange, orgName, bucketName, writePrecision);
        } else if (body instanceof Record) {
            insertRecord(exchange, orgName, bucketName, writePrecision);
        } else if (body instanceof Points) {
            insertPoints(exchange, orgName, bucketName);
        } else if (body instanceof Measurements) {
            insertMeasurements(exchange, orgName, bucketName, writePrecision);
        } else if (body instanceof Records) {
            insertRecords(exchange, orgName, bucketName, writePrecision);
        } else {
            // default insert as point
            insertPoint(exchange, orgName, bucketName);
        }
    }

    private void insertPoint(Exchange exchange, String orgName, String bucketName)
            throws InvalidPayloadException {
        Point point = exchange.getIn().getMandatoryBody(Point.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing point {}", point.toLineProtocol());
            }
            writeApi.writePoint(bucketName, orgName, point);
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    private void insertMeasurement(Exchange exchange, String orgName, String bucketName, WritePrecision writePrecision)
            throws InvalidPayloadException {
        Measurement measurement = exchange.getIn().getMandatoryBody(Measurement.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing measurement {}", measurement);
            }
            writeApi.writeMeasurement(bucketName, orgName, writePrecision, measurement.getInfluxMeasurement());
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    private void insertRecord(Exchange exchange, String orgName, String bucketName, WritePrecision writePrecision)
            throws InvalidPayloadException {
        Record record = exchange.getIn().getMandatoryBody(Record.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing record {}", record);
            }
            writeApi.writeRecord(bucketName, orgName, writePrecision, record.getInfluxRecord());
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    private void insertPoints(Exchange exchange, String orgName, String bucketName)
            throws InvalidPayloadException {
        @SuppressWarnings("unchecked")
        Points points = exchange.getIn().getMandatoryBody(Points.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing points {}", points);
            }
            writeApi.writePoints(bucketName, orgName, points.getInfluxPoints());
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void insertMeasurements(Exchange exchange, String orgName, String bucketName, WritePrecision writePrecision)
            throws InvalidPayloadException {
        Measurements measurements = exchange.getIn().getMandatoryBody(Measurements.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing measurements {}", measurements);
            }
            writeApi.writeMeasurements(bucketName, orgName, writePrecision, measurements.getInfluxMeasurements());
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    private void insertRecords(Exchange exchange, String orgName, String bucketName, WritePrecision writePrecision)
            throws InvalidPayloadException {
        Records records = exchange.getIn().getMandatoryBody(Records.class);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing records {}", records);
            }
            writeApi.writeRecords(bucketName, orgName, writePrecision, records.getInfluxRecords());
        } catch (Exception ex) {
            exchange.setException(new CamelInfluxDb2Exception(ex));
        }
    }

    private void doPing(Exchange exchange) {
        Boolean result = connection.ping();
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getMessage().setBody(result);
    }

    private String calculateOrgName(Exchange exchange) {
        String orgName = exchange.getIn().getHeader(InfluxDb2Constants.ORG, String.class);
        if (ObjectHelper.isNotEmpty(orgName)) {
            return orgName;
        }
        return endpoint.getOrg();
    }

    private String calculateBucketName(Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(InfluxDb2Constants.BUCKET, String.class);
        if (ObjectHelper.isNotEmpty(bucketName)) {
            return bucketName;
        }
        return endpoint.getBucket();
    }

    private WritePrecision calculateWritePrecision(Exchange exchange) {
        WritePrecision precision = exchange.getIn().getHeader(InfluxDb2Constants.WRITE_PRECISION, WritePrecision.class);
        if (ObjectHelper.isNotEmpty(precision)) {
            return precision;
        }
        return endpoint.getWritePrecision();
    }
}
