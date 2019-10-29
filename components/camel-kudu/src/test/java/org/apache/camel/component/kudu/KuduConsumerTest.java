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
package org.apache.camel.component.kudu;

import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.client.KuduException;
import org.apache.kudu.client.KuduScanner;
import org.apache.kudu.client.RowResult;
import org.apache.kudu.client.RowResultIterator;
import org.junit.Before;
import org.junit.Test;

public class KuduConsumerTest extends AbstractKuduTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                //integration test route
                from("kudu:localhost:7051/ConsumerTable?operation=scan")
                    .to("mock:result");
            }
        };
    }

    @Before
    public void setup() {
        deleteTestTable("ConsumerTable");
        createTestTable("ConsumerTable");
        insertRowInTestTable("ConsumerTable");
    }

    @Test
    public void scan() throws KuduException, InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        assertEquals(1, exchanges.size());

        KuduScanner scanner = exchanges.get(0).getIn().getBody(KuduScanner.class);

        RowResultIterator results = scanner.nextRows();
        RowResult result = results.next();

        ColumnSchema columnByIndex = result.getSchema().getColumnByIndex(0);
        String name = columnByIndex.getName();

        // INT32 id=??, STRING title=Mr.,
        // STRING name=Samuel, STRING lastname=Smith,
        // STRING address=4359  Plainfield Avenue
        assertEquals("id", name);
        assertEquals("Mr.", result.getString(1));
        assertEquals("Samuel", result.getString(2));
        assertEquals("Smith", result.getString(3));
        assertEquals("4359  Plainfield Avenue", result.getString(4));
    }
}