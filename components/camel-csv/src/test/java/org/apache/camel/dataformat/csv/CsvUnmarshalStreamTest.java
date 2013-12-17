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
package org.apache.camel.dataformat.csv;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Spring based integration test for the <code>CsvDataFormat</code>
 * @version 
 */
public class CsvUnmarshalStreamTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @SuppressWarnings("unchecked")
    @Test
    public void testCsvUnMarshal() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start",
                "123|Camel in Action|\"1\n124\"|ActiveMQ in Action|2\n"
                        + "333|Shark in Action|\"1\n124\"|Cassandra in Action|3\n"
                        + "777|Penguin in Action|\"1\n124\"|Astyanax in Action|4\n");

        assertMockEndpointsSatisfied();

        Iterator<List<String>> body = result.getReceivedExchanges().get(0)
                .getIn().getBody(Iterator.class);
        assertTrue(body.hasNext());
        List<String> row = body.next();
        assertEquals(5, row.size());
        assertEquals("123", row.get(0));
        assertTrue(body.hasNext());
        row = body.next();
        assertEquals(5, row.size());
        assertTrue(body.hasNext());
        row = body.next();
        assertEquals(5, row.size());
        assertFalse(body.hasNext());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CsvDataFormat csv = new CsvDataFormat();
                csv.setLazyLoad(true);
                csv.setDelimiter("|");

                from("direct:start").unmarshal(csv)
                    .to("mock:result");
            }
        };
    }
}