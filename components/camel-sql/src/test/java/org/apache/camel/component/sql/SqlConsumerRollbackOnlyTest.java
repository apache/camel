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

import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row whose exchange was marked rollback only (without an exception) did not complete successfully: it must get
 * onConsumeFailed instead of onConsume (no statement when onConsumeFailed is not set), and it must break out of a
 * transacted batch as a failed exchange does.
 */
class SqlConsumerRollbackOnlyTest extends CamelTestSupport {

    private static final String ON_CONSUME = "&onConsume=update projects set license = 'DONE' where id = :#id";
    private static final String ON_CONSUME_FAILED = "&onConsumeFailed=update projects set license = 'BAD' where id = :#id";

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
    void testRollbackOnlyExchangeRunsOnConsumeFailed() throws Exception {
        SqlConsumer consumer = (SqlConsumer) context.getRoute("on-consume").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(3, consumer.poll());

        assertEquals(List.of("DONE", "BAD", "DONE"), licenses(),
                "the AMQ row, whose exchange was marked rollback only, must get onConsumeFailed");
    }

    @Test
    void testRollbackOnlyExchangeWithoutOnConsumeFailedIsNotConsumed() throws Exception {
        SqlConsumer consumer = (SqlConsumer) context.getRoute("on-consume-only").getConsumer();
        assertEquals(3, consumer.poll());

        assertEquals(List.of("DONE", "ASF", "DONE"), licenses(),
                "without onConsumeFailed, the AMQ row, whose exchange was marked rollback only, must not get onConsume");
    }

    @Test
    void testTransactedRollbackOnlyExchangeBreaksOutOfBatch() throws Exception {
        SqlConsumer consumer = (SqlConsumer) context.getRoute("transacted").getConsumer();
        // the consumer wraps the exception of the batch
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, consumer::poll,
                "a rollback only exchange must break out of a transacted batch, as a failed exchange does");
        assertInstanceOf(RollbackExchangeException.class, e.getCause());
        // there was no error, and the message (logged by the consumer at every such poll) must not say so
        assertTrue(e.getCause().getMessage().startsWith("Rollback transaction as the exchange was marked as rollback only"),
                e.getCause().getMessage());

        assertEquals(List.of("DONE", "ASF", "XXX"), licenses(),
                "neither the rollback only row nor the rows after it must be consumed");
    }

    private List<String> licenses() {
        return jdbcTemplate.queryForList("select license from projects order by id", String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                consume("on-consume", ON_CONSUME + ON_CONSUME_FAILED);
                consume("on-consume-only", ON_CONSUME);
                consume("transacted", ON_CONSUME + ON_CONSUME_FAILED + "&transacted=true");
            }

            private void consume(String routeId, String options) {
                from("sql:select * from projects order by id?startScheduler=false" + options)
                        .routeId(routeId)
                        .choice().when(simple("${body[PROJECT]} == 'AMQ'")).markRollbackOnly().end()
                        .to("mock:processed");
            }
        };
    }
}
