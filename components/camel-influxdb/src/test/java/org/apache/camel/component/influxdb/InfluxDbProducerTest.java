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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class InfluxDbProducerTest extends AbstractInfluxDbTest {

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
                    .to("mock:test");
            }
        };
    }
    
    @Test
    public void writePointFromMapAndStaticHeader() throws InterruptedException {
        
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);
        
        Map<String, Object> pointMap = new HashMap<String, Object>();       
        sendBody("direct:test", pointMap);        
       
        
        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }
    
    @Test
    public void writePointFromMapAndDynamicHeader() throws InterruptedException {
       
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        Map<String, Object> pointMap = createMapPoint();
        sendBody("direct:test", pointMap);
        
        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
        
    }
    
    @Test
    public void missingMeassurementNameFails() throws InterruptedException {
        
        errorEndpoint.expectedMessageCount(1);
        successEndpoint.expectedMessageCount(0);
        
        Map<String, Object> pointMap = new HashMap<String, Object>();   
        pointMap.remove(InfluxDbConstants.MEASUREMENT_NAME);
        sendBody("direct:test", pointMap);        
        
        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

    }

    private Map<String, Object> createMapPoint() {
        Map<String, Object> pointMap = new HashMap<String, Object>();
        pointMap.put(InfluxDbConstants.DBNAME_HEADER, 1234);
        pointMap.put(InfluxDbConstants.MEASUREMENT_NAME, "MyTestMeasurement");
        return pointMap;
    }

}
