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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class CsvMarshalTest extends CamelTestSupport {

    @Test
    public void testCsvMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();

        Map<String, Object> row1 = new LinkedHashMap<String, Object>();
        row1.put("orderId", 123);
        row1.put("item", "Camel in Action");
        row1.put("amount", 1);
        data.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<String, Object>();
        row2.put("orderId", 124);
        row2.put("item", "ActiveMQ in Action");
        row2.put("amount", 2);
        data.add(row2);

        template.sendBody("direct:toCsv", data);

        assertMockEndpointsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String[] lines = body.split("\n");
        assertEquals("There should be 2 rows", 2, lines.length);
        assertEquals("123,Camel in Action,1", lines[0]);
        assertEquals("124,ActiveMQ in Action,2", lines[1]);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:toCsv")
                    .marshal().csv()
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }
}
