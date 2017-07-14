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

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.junit.Before;
import org.junit.Test;

public class InfluxDbProducerBatchTest extends AbstractInfluxDbTest {

    @EndpointInject(uri = "mock:test")
    MockEndpoint successEndpoint;

    @EndpointInject(uri = "mock:error")
    MockEndpoint errorEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));

                //test route
                from("direct:test")
                        .to("influxdb:influxDbBean?batch=true")
                        .to("mock:test");
            }
        };
    }

    @Before
    public void resetEndpoints() {
        errorEndpoint.reset();
        successEndpoint.reset();
    }

    @Test
    public void writeBatchPoints() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        BatchPoints batchPoints = createBatchPoints();
        sendBody("direct:test", batchPoints);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }

    private BatchPoints createBatchPoints() {
        BatchPoints batchPoints = BatchPoints.database("myTestTimeSeries").build();
        Point point1 = Point.measurement("cpu")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("idle", 90L)
                .addField("user", 9L)
                .addField("system", 1L)
                .build();
        Point point2 = Point.measurement("disk")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("used", 8L)
                .addField("free", 1L)
                .build();
        batchPoints.point(point1);
        batchPoints.point(point2);
        return batchPoints;
    }

}
