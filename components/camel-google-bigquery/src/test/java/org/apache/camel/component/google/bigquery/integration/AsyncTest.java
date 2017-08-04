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
package org.apache.camel.component.google.bigquery.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

public class AsyncTest extends BigQueryTestSupport {
    private static final String TABLE_ID = "asynctest";

    @EndpointInject(uri = "direct:in")
    private Endpoint directIn;

    @EndpointInject(uri = "google-bigquery:{{project.id}}:{{bigquery.datasetId}}:" + TABLE_ID)
    private Endpoint bigqueryEndpoint;

    @EndpointInject(uri = "mock:sendResult")
    private MockEndpoint sendResult;

    @Produce(uri = "direct:in")
    private ProducerTemplate producer;

    @Before
    public void init() throws Exception {
        createBqTable(TABLE_ID);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(directIn)
                        .to("seda:seda");
                from("seda:seda")
                        .routeId("Async")
                        .inOnly(bigqueryEndpoint)
                        .log(LoggingLevel.INFO, "To sendresult")
                        .to(sendResult);
            }
        };
    }

    @Test
    public void sendAsync() throws Exception {
        List<Map> objects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Exchange exchange = new DefaultExchange(context);
            String uuidCol1 = UUID.randomUUID().toString();
            String uuidCol2 = UUID.randomUUID().toString();

            Map<String, String> object = new HashMap<>();
            object.put("col1", uuidCol1);
            object.put("col2", uuidCol2);
            objects.add(object);
            exchange.getIn().setBody(object);
            producer.send(exchange);
        }
        sendResult.expectedMessageCount(5);

        sendResult.assertIsSatisfied(4000);

        for (Map object: objects) {
            assertRowExist(TABLE_ID, object);
        }
    }

}
