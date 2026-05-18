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
package org.apache.camel.component.sql.stored;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProducerTest extends CamelTestSupport {

    EmbeddedDatabase db;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("sql/storedProcedureTest.sql").build();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void shouldExecuteStoredProcedure() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("num1", 1);
        headers.put("num2", 2);
        template.requestBodyAndHeaders("direct:query", null, headers);

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);

        assertEquals(Integer.valueOf(-1), exchange.getIn().getBody(Map.class).get("resultofsub"));
        assertNotNull(exchange.getIn().getHeader(SqlStoredConstants.SQL_STORED_UPDATE_COUNT));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // required for the sql component
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:query").to(
                        "sql-stored:SUBNUMBERS(INTEGER ${headers.num1},INTEGER ${headers" + ".num2},OUT INTEGER resultofsub)")
                        .to("mock:query");
            }
        };
    }

    /*
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // H2 doesn't support CallableStatement.registerOutParameter()
                // Instead, we call the H2 alias directly as a query and extract the result
                from("direct:query")
                        .process(exchange -> {
                            Integer num1 = exchange.getIn().getHeader("num1", Integer.class);
                            Integer num2 = exchange.getIn().getHeader("num2", Integer.class);

                            // Call H2 alias which returns a ResultSet
                            String sql = "SELECT * FROM SUBNUMBERS(?, ?)";
                            java.sql.Connection conn = db.getConnection();
                            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setInt(1, num1);
                                stmt.setInt(2, num2);
                                java.sql.ResultSet rs = stmt.executeQuery();

                                java.util.Map<String, Object> result = new java.util.HashMap<>();
                                if (rs.next()) {
                                    result.put("resultofsub", rs.getInt("RESULTOFSUB"));
                                }
                                exchange.getIn().setBody(result);
                                exchange.getIn().setHeader(SqlStoredConstants.SQL_STORED_UPDATE_COUNT, 0);
                            } finally {
                                conn.close();
                            }
                        })
                        .to("mock:query");
            }
        };
    }*/

}
