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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcParameterizedQueryTest extends AbstractJdbcTestSupport {

    @Test
    public void testParameterizedQueryNoNames() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // The linkedHashMap values has different order in JDK7 and JDK8
        // so I had to reduce the parameters size 
        Map<String, Object> jdbcParams = new HashMap<String, Object>();
        jdbcParams.put("name", "jstrachan");

        template.sendBodyAndHeaders("direct:start", "select * from customer where id = 'cust1' and name = ? order by ID", jdbcParams);

        assertMockEndpointsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(1, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("jstrachan", row.get("NAME"));
    }

    @Test
    public void testParameterizedQuery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Map<String, Object> jdbcParams = new HashMap<String, Object>();
        jdbcParams.put("name", "jstrachan");
        jdbcParams.put("id", "cust1");
        

        template.sendBodyAndHeaders("direct:start", "select * from customer where id = :?id and name = :?name order by ID", jdbcParams);

        assertMockEndpointsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(1, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("jstrachan", row.get("NAME"));
    }

    @Test
    public void testParameterizedQueryJdbcHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Map<String, Object> jdbcParams = new HashMap<String, Object>();
        jdbcParams.put("id", "cust1");
        jdbcParams.put("name", "jstrachan");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("id", "cust2");
        // this header should take precedence so we will not get cust2
        headers.put(JdbcConstants.JDBC_PARAMETERS, jdbcParams);

        template.sendBodyAndHeaders("direct:start", "select * from customer where id = :?id and name = :?name order by ID", headers);

        assertMockEndpointsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(1, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("jstrachan", row.get("NAME"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().setUseBreadcrumb(false);

                getContext().getComponent("jdbc", JdbcComponent.class).setDataSource(db);

                from("direct:start")
                        .to("jdbc:testdb?useHeadersAsParameters=true")
                        .to("mock:result");
            }
        };
    }

}       