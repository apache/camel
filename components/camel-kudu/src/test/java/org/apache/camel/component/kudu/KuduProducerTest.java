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
package org.apache.camel.component.kudu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.Schema;
import org.apache.kudu.Type;
import org.apache.kudu.client.CreateTableOptions;
import org.junit.Before;
import org.junit.Test;

public class KuduProducerTest extends AbstractKuduTest {

    @EndpointInject(value = "mock:result")
    public MockEndpoint successEndpoint;

    @EndpointInject(value = "mock:error")
    public MockEndpoint errorEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));

                //integration test route
                from("direct:create")
                    .to("kudu:localhost:7051/TestTable?operation=create_table")
                    .to("mock:result");

                from("direct:insert")
                    .to("kudu:localhost:7051/TestTable?operation=insert")
                    .to("mock:result");

                from("direct:data")
                    .to("kudu:localhost:7051/TestTable?operation=insert")
                    .to("mock:result");
            }
        };
    }

    @Before
    public void resetEndpoints() {
        errorEndpoint.reset();
        successEndpoint.reset();
        deleteTestTable("TestTable");
    }

    @Test
    public void createTable() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        final Map<String, Object> headers = new HashMap<>();

        List<ColumnSchema> columns = new ArrayList<>(5);
        final List<String> columnNames = Arrays.asList("id", "title", "name", "lastname", "address");

        for (int i = 0; i < columnNames.size(); i++) {
            columns.add(
                new ColumnSchema.ColumnSchemaBuilder(columnNames.get(i), Type.STRING)
                    .key(i == 0)
                    .build()
            );
        }

        List<String> rangeKeys = new ArrayList<>();
        rangeKeys.add("id");

        headers.put(KuduConstants.CAMEL_KUDU_SCHEMA, new Schema(columns));
        headers.put(KuduConstants.CAMEL_KUDU_TABLE_OPTIONS, new CreateTableOptions().setRangePartitionColumns(rangeKeys));

        template().requestBodyAndHeaders("direct://create", null, headers);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void insertRow() throws InterruptedException {
        createTestTable("TestTable");

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        // Create a sample row that can be inserted in the test table
        Map<String, Object> row = new HashMap<>();
        row.put("id", 5);
        row.put("title", "Mr.");
        row.put("name", "Samuel");
        row.put("lastname", "Smith");
        row.put("address", "4359  Plainfield Avenue");

        sendBody("direct:insert", row);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void insertRowDifferentData() throws InterruptedException {
        createTestTable("TestTable");
        errorEndpoint.expectedMessageCount(1);
        successEndpoint.expectedMessageCount(0);

        Map<String, Object> row = new HashMap<>();
        row.put("id", ThreadLocalRandom.current().nextInt(1, 99));
        row.put("_integer", ThreadLocalRandom.current().nextInt(1, 99));
        row.put("_long", ThreadLocalRandom.current().nextLong(500, 600));
        row.put("_double", ThreadLocalRandom.current().nextDouble(9000, 9999));
        row.put("_float", ThreadLocalRandom.current().nextFloat() * (499 - 100) + 100);

        sendBody("direct:data", row);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }
}
