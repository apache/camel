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
package org.apache.camel.component.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JdbcRSMetaDataTest extends AbstractJdbcTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    public void testJdbcRSMetaData() {
        Endpoint directHelloEndpoint = context.getEndpoint("direct:hello");
        Exchange directHelloExchange = directHelloEndpoint.createExchange();

        directHelloExchange.getIn().setBody("select * from customer order by ID");

        Exchange out = template.send(directHelloEndpoint, directHelloExchange);
        assertNotNull(out);
        assertNotNull(out.getOut());

        List<Map<String, Object>> returnValues = out.getOut().getBody(List.class);
        assertNotNull(returnValues);
        assertEquals(3, returnValues.size());
        Map<String, Object> row = returnValues.get(0);
        assertEquals("cust1", row.get("ID"));
        assertEquals("jstrachan", row.get("NAME"));

        Set<String> columnNames = (Set<String>) out.getOut().getHeader(JdbcConstants.JDBC_COLUMN_NAMES);
        assertNotNull(columnNames);
        assertEquals(2, columnNames.size());
        assertTrue(columnNames.contains("ID"));
        assertTrue(columnNames.contains("NAME"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?readSize=100");
            }
        };
    }

}
