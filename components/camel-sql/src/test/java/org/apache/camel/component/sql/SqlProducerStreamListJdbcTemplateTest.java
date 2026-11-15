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
package org.apache.camel.component.sql;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * With outputType=StreamList the producer gets its connection and statement settings like the other output types: it
 * takes part in the transaction of the route and applies the template options of the endpoint.
 */
public class SqlProducerStreamListJdbcTemplateTest extends CamelTestSupport {

    private static final String SELECT = "select * from projects order by id";

    private EmbeddedDatabase db;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();
        registry.bind("db", db);

        DataSourceTransactionManager txMgr = new DataSourceTransactionManager(db);
        SpringTransactionPolicy txPolicy = new SpringTransactionPolicy();
        txPolicy.setTransactionManager(txMgr);
        txPolicy.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        registry.bind("required", txPolicy);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testStreamListSeesTheRowsOfTheTransaction() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tx");
        mock.expectedMessageCount(1);

        Exchange out = template.send("direct:tx", e -> e.getIn().setBody(null));

        assertFalse(out.isFailed(), "exchange failed: " + out.getException());
        mock.assertIsSatisfied();
        assertEquals(99, ((Number) mock.getExchanges().get(0).getIn().getBody(Map.class).get("ID")).intValue());
        // the statement after the stream used the connection of the transaction, which is committed
        JdbcTemplate jdbc = new JdbcTemplate(db);
        assertEquals(2, jdbc.queryForObject("select count(*) from projects where id in (99, 100)", Integer.class));
    }

    @Test
    public void testStreamListAppliesTemplateMaxRows() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:row");
        mock.expectedMessageCount(1);
        mock.setAssertPeriod(100);

        template.sendBody("direct:streamMaxRows", null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testDataSourceHeaderKeepsTemplateMaxRows() {
        List<?> rows = template.requestBodyAndHeader("direct:selectMaxRows", null, SqlConstants.SQL_DATA_SOURCE, db,
                List.class);

        assertEquals(1, rows.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tx")
                        .transacted("required")
                        .to("sql:insert into projects values (99, 'TX', 'ASF')?dataSource=#db")
                        .to("sql:select * from projects where id = 99?dataSource=#db&outputType=StreamList")
                        .split(body()).streaming()
                            .to("mock:tx")
                        .end()
                        .to("sql:insert into projects values (100, 'TX', 'ASF')?dataSource=#db");

                from("direct:streamMaxRows")
                        .to("sql:" + SELECT + "?dataSource=#db&outputType=StreamList&template.maxRows=1")
                        .split(body()).streaming()
                        .to("mock:row");

                from("direct:selectMaxRows")
                        .to("sql:" + SELECT + "?dataSource=#db&outputType=SelectList&template.maxRows=1");
            }
        };
    }
}
