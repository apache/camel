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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The onConsumeBatchComplete query runs after the entire batch was processed: a route stopped while it routes the first
 * row of a batch of three routes only that row, and the query does not run for the two rows it did not route.
 */
class SqlConsumerBatchCompleteStopTest extends CamelTestSupport {

    private final CountDownLatch firstRowArrived = new CountDownLatch(1);
    private final CountDownLatch releaseFirstRow = new CountDownLatch(1);
    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();

        jdbcTemplate = new JdbcTemplate(db);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    void testStopDuringBatchDoesNotRunBatchCompleteQuery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Camel");

        context.getRouteController().startRoute("batch");
        assertTrue(firstRowArrived.await(10, TimeUnit.SECONDS), "The first row of the batch should be routed");

        // a graceful stop of the route while it routes the first row: the consumer completes the current row only
        SqlConsumer consumer = (SqlConsumer) context.getRoute("batch").getConsumer();
        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("batch");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // the consumer routes no further row of the batch
        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
        releaseFirstRow.countDown();
        stop.get(20, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
        // the routed row was deleted by onConsume, the two rows that were not routed are still there
        assertEquals(List.of(2, 3),
                jdbcTemplate.queryForList("select id from projects order by id", Integer.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("sql:select * from projects order by id?initialDelay=0&delay=50"
                     + "&onConsume=delete from projects where id = :#id"
                     + "&onConsumeBatchComplete=delete from projects")
                        .routeId("batch").autoStartup(false)
                        .process(exchange -> {
                            firstRowArrived.countDown();
                            assertTrue(releaseFirstRow.await(20, TimeUnit.SECONDS));
                        })
                        .setBody(simple("${body[project]}"))
                        .to("mock:result");
            }
        };
    }
}
