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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.Type;
import org.apache.kudu.client.KuduPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KuduScanTest extends AbstractKuduTest {

    public static final String TABLE = "ScanTableTest";

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
                from("direct:scan").to("kudu:localhost:7051/" + TABLE + "?operation=scan")
                        .to("mock:result");

                from("direct:scan2")
                        .to("kudu:localhost:7051/TestTable?operation=scan")
                        .to("mock:result");
            }
        };
    }

    @BeforeEach
    public void setup() {
        deleteTestTable(TABLE);
        createTestTable(TABLE);
        insertRowInTestTable(TABLE);
        insertRowInTestTable(TABLE);
    }

    @Test
    public void scan() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        sendBody("direct:scan", null);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

        List<Exchange> exchanges = successEndpoint.getReceivedExchanges();
        assertEquals(1, exchanges.size());

        List<Map<String, Object>> results = exchanges.get(0).getIn().getBody(List.class);

        assertEquals(2, results.size(), "Wrong number of results.");

        Map<String, Object> row = results.get(0);

        // INT32 id=??, STRING title=Mr.,
        // STRING name=Samuel, STRING lastname=Smith,
        // STRING address=4359  Plainfield Avenue
        assertTrue(row.containsKey("id"));
        assertEquals("Mr.", row.get("title"));
        assertEquals("Samuel", row.get("name"));
        assertEquals("Smith", row.get("lastname"));
        assertEquals("4359  Plainfield Avenue", row.get("address"));

        row = results.get(1);

        // INT32 id=??, STRING title=Mr.,
        // STRING name=Samuel, STRING lastname=Smith,
        // STRING address=4359  Plainfield Avenue
        assertTrue(row.containsKey("id"));
        assertEquals("Mr.", row.get("title"));
        assertEquals("Samuel", row.get("name"));
        assertEquals("Smith", row.get("lastname"));
        assertEquals("4359  Plainfield Avenue", row.get("address"));

    }

    @Test
    public void scanWithPredicate() throws InterruptedException {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(2);

        // without predicate
        Map<String, Object> headers = new HashMap<>();
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_PREDICATE, null);
        sendBody("direct:scan", null, headers);
        List<Map<String, Object>> results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges()
                .get(0).getIn().getBody(List.class);
        assertEquals(2, results.size(), "two records with id=1 and id=2 are expected to be returned");

        // with predicate
        ColumnSchema schema = new ColumnSchema.ColumnSchemaBuilder("id", Type.INT32).build();
        KuduPredicate predicate = KuduPredicate.newComparisonPredicate(schema, KuduPredicate.ComparisonOp.EQUAL, 2);
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_PREDICATE, predicate);
        sendBody("direct:scan", null, headers);
        results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges()
                .get(1).getIn().getBody(List.class);
        assertEquals(1, results.size(), "only one record with id=2 is expected to be returned");

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void scanWithColumnNames() throws InterruptedException {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(2);

        // without column names
        Map<String, Object> headers = new HashMap<>();
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_COLUMN_NAMES, null);
        sendBody("direct:scan", null, headers);
        List<Map<String, Object>> results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges()
                .get(0).getIn().getBody(List.class);
        assertEquals(5, results.get(0).size(), "returned rows are expected to have 5 columns");

        // with column names
        List<String> columnNames = Arrays.asList("id", "name");
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_COLUMN_NAMES, columnNames);
        sendBody("direct:scan", null, headers);
        results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges().get(1).getIn().getBody(List.class);
        Map<String, Object> result = results.get(0);
        assertEquals(2, result.size(), "returned rows are expected to have only 2 columns");
        for (String name : columnNames) {
            assertTrue(result.containsKey(name), "returned columns are expected to be identical to the specified ones");
        }

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void scanWithLimit() throws InterruptedException {
        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(2);

        // without limit
        Map<String, Object> headers = new HashMap<>();
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_LIMIT, null);
        sendBody("direct:scan", null, headers);
        List<Map<String, Object>> results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges()
                .get(0).getIn().getBody(List.class);
        assertEquals(2, results.size(), "returned result is expected to have 2 rows");

        // with limit
        headers.put(KuduConstants.CAMEL_KUDU_SCAN_LIMIT, 1L);
        sendBody("direct:scan", null, headers);
        results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges().get(1).getIn().getBody(List.class);
        assertEquals(1, results.size(), "returned result is expected to have only 1 row");

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void scanTable() throws InterruptedException {
        createTestTable("TestTable");
        insertRowInTestTable("TestTable");

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        sendBody("direct:scan2", null);

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();

        List<Map<String, Object>> results = (List<Map<String, Object>>) successEndpoint.getReceivedExchanges()
                .get(0).getIn().getBody(List.class);

        assertEquals(1, results.size(), "Wrong number of results.");

        Map<String, Object> row = results.get(0);

        // INT32 id=??, STRING title=Mr.,
        // STRING name=Samuel, STRING lastname=Smith,
        // STRING address=4359  Plainfield Avenue
        assertTrue(row.containsKey("id"));
        assertEquals("Mr.", row.get("title"));
        assertEquals("Samuel", row.get("name"));
        assertEquals("Smith", row.get("lastname"));
        assertEquals("4359  Plainfield Avenue", row.get("address"));
    }
}
