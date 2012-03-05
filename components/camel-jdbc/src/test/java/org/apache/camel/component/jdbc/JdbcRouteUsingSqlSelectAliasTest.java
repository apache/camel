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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JdbcRouteUsingSqlSelectAliasTest extends AbstractJdbcTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testJdbcRoutes() throws Exception {
        // START SNIPPET: invoke
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");
        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body
        exchange.getIn().setBody("select id as identifier, name from customer order by ID");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getOut());
        List<Map<String, Object>> data = out.getOut().getBody(List.class);
        assertNotNull("out body could not be converted to a List - was: "
            + out.getOut().getBody(), data);
        assertEquals(3, data.size());
        Map<String, Object> row = data.get(0);
        assertEquals("cust1", row.get("IDENTIFIER"));
        assertEquals("jstrachan", row.get("NAME"));
        row = data.get(1);
        assertEquals("cust2", row.get("IDENTIFIER"));
        assertEquals("nsandhu", row.get("NAME"));
        // END SNIPPET: invoke
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            // lets add simple route
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?readSize=100");
            }
            // END SNIPPET: route
        };
    }
}