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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.influxdb.annotations.Column;
import com.influxdb.client.write.Point;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.influxdb2.data.Measurement;
import org.apache.camel.component.influxdb2.data.Measurements;
import org.apache.camel.component.influxdb2.data.Points;
import org.apache.camel.component.influxdb2.data.Record;
import org.apache.camel.component.influxdb2.data.Records;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InfluxDb2ProducerTest extends AbstractInfluxDbTest {

    @EndpointInject("mock:test")
    MockEndpoint successEndpoint;

    @EndpointInject("mock:error")
    MockEndpoint errorEndpoint;

    @BeforeEach
    public void resetEndpoints() {
        errorEndpoint.reset();
        successEndpoint.reset();
    }

    @com.influxdb.annotations.Measurement(name = "temperature")
    private static class Temperature {

        @Column(tag = true)
        String location;

        @Column
        Double value;

        @Column(timestamp = true)
        Instant time;
    }

    @Test
    public void writePoint() throws Exception {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        Point point = Point.measurement("temperature");

        sendBody("direct:test", point);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }

    @Test
    public void writePointFromMap() throws Exception {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        Map<String, Object> pointMap = createMapPoint();
        sendBody("direct:test", pointMap);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }

    @Test
    public void writeRecord() throws Exception {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        Record record = Record.fromString("temperature,location=north value=60.0");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        sendBody("direct:test", record);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void writeMeasurement() throws Exception {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        Temperature temperature = new Temperature();
        temperature.value = 50.0d;
        temperature.time = Instant.now();
        temperature.location = "xxx";

        Measurement measurement = Measurement.fromObject(temperature);

        sendBody("direct:test", measurement);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void writePoints() throws Exception {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });
        ArrayList<Point> points = new ArrayList<>() {
            {
                add(Point.measurement("temperature"));
            }
        };

        sendBody("direct:test", Points.create(points));

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void writeRecords() throws Exception {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        sendBody("direct:test", Records.create("temperature,location=north value=60.0"));

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void writeMeasurements() throws Exception {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });
        Temperature temperature = new Temperature();
        temperature.value = 50.0d;
        temperature.time = Instant.now();
        temperature.location = "xxx";

        Measurement measurement = Measurement.fromObject(temperature);

        Measurements.create(measurement);
        sendBody("direct:test", Records.create("temperature,location=north value=60.0"));

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void missingMeasurementNameFails() throws Exception {

        errorEndpoint.expectedMessageCount(1);
        successEndpoint.expectedMessageCount(0);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));
                from("direct:test").to("influxdb2:influxDbBean?org={{influxdb2.testOrg}}&bucket={{influxdb2.testBucket}}")
                        .to("mock:test");
            }
        });

        Map<String, Object> pointMap = createMapPoint();
        pointMap.remove(InfluxDb2Constants.MEASUREMENT);
        sendBody("direct:test", pointMap);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }

    private Map<String, Object> createMapPoint() {
        Map<String, Object> pointMap = new HashMap<>();
        pointMap.put(InfluxDb2Constants.ORG, "MyTestOrg");
        pointMap.put(InfluxDb2Constants.BUCKET, "MyTestBucket");
        pointMap.put(InfluxDb2Constants.MEASUREMENT, "MyTestMeasurement");
        pointMap.put("CPU", 1);
        return pointMap;
    }

}
