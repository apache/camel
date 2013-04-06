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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JdbcStatementParametersTest extends AbstractJdbcTestSupport {
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testMax2Rows() throws Exception {
        List rows = template.requestBody("direct:hello", "select * from customer order by id", List.class);

        assertEquals(2, rows.size());
        assertEquals(2, context.getEndpoints().size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testMax5Rows() throws Exception {
        List rows = template.requestBody("jdbc:testdb?statement.maxRows=5&statement.fetchSize=50", "select * from customer order by id", List.class);

        assertEquals(3, rows.size());
        assertEquals(3, context.getEndpoints().size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testNoParameters() throws Exception {
        List rows = template.requestBody("jdbc:testdb", "select * from customer order by id", List.class);

        assertEquals(3, rows.size());
        assertEquals(3, context.getEndpoints().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?statement.maxRows=2");
            }
        };
    }
}