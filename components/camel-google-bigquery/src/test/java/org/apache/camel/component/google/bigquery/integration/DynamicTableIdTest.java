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
package org.apache.camel.component.google.bigquery.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

public class DynamicTableIdTest extends BigQueryTestSupport {
    private static final String TABLE_ID_1 = "dynamic_table_1";
    private static final String TABLE_ID_2 = "dynamic_table_2";

    @EndpointInject("direct:in")
    private Endpoint directIn;

    @EndpointInject("google-bigquery:{{project.id}}:{{bigquery.datasetId}}")
    private Endpoint bigqueryEndpoint;

    @EndpointInject("mock:sendResult")
    private MockEndpoint sendResult;

    @Produce("direct:in")
    private ProducerTemplate producer;

    @Before
    public void init() throws Exception {
        createBqTable(TABLE_ID_1);
        createBqTable(TABLE_ID_2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(directIn)
                        .routeId("DynamicTable")
                        .to(bigqueryEndpoint)
                        .to(sendResult);
            }
        };
    }

    @Test
    public void dynamicTable() throws Exception {
        Exchange exchange1 = new DefaultExchange(context);
        String uuidCol11 = UUID.randomUUID().toString();
        String uuidCol21 = UUID.randomUUID().toString();
        exchange1.getIn().setHeader(GoogleBigQueryConstants.TABLE_ID, TABLE_ID_1);

        Map<String, String> object1 = new HashMap<>();
        object1.put("col1", uuidCol11);
        object1.put("col2", uuidCol21);
        exchange1.getIn().setBody(object1);

        Exchange exchange2 = new DefaultExchange(context);
        String uuidCol12 = UUID.randomUUID().toString();
        String uuidCol22 = UUID.randomUUID().toString();
        exchange2.getIn().setHeader(GoogleBigQueryConstants.TABLE_ID, TABLE_ID_2);

        Map<String, String> object2 = new HashMap<>();
        object2.put("col1", uuidCol12);
        object2.put("col2", uuidCol22);
        exchange2.getIn().setBody(object2);


        sendResult.expectedMessageCount(2);
        producer.send(exchange1);
        producer.send(exchange2);
        sendResult.assertIsSatisfied(4000);

        assertRowExist(TABLE_ID_1, object1);
        assertRowExist(TABLE_ID_2, object2);
    }

}
