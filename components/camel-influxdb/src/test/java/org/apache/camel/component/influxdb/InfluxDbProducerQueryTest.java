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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class InfluxDbProducerQueryTest extends AbstractInfluxDbTest {

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
                        .to("influxdb:influxDbBean?databaseName={{influxdb.testDb}}")
                        .process(new Processor() {

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setHeader(InfluxDbConstants.INFLUXDB_QUERY, "select * from cpu");
                            }
                        })
                        .to("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=query")
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
    public void writePointFromMapAndStaticDbName() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        Map<String, Object> pointMap = createMapPoint();
        sendBody("direct:test", pointMap);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    private Map<String, Object> createMapPoint() {
        Map<String, Object> pointMap = new HashMap<>();
        pointMap.put(InfluxDbConstants.MEASUREMENT_NAME, "MyTestMeasurement");
        pointMap.put("CPU", 1);
        return pointMap;
    }

}
