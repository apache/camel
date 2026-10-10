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
package org.apache.camel.component.jooq;

import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jooq.db.tables.records.AuthorRecord;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jooq.db.Tables.AUTHOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With consumeDelete (the default) an entity must only be deleted when its exchange was processed successfully: a
 * failed or rollback only exchange, or an entity that was not processed, must leave the row in the table, so the next
 * poll consumes it again.
 */
class JooqConsumerDeleteFailedTest extends BaseJooqTest {

    @Test
    void testFailedExchangeKeepsRow() throws Exception {
        create.executeInsert(new AuthorRecord(1, null, "ok", null, null, null));
        create.executeInsert(new AuthorRecord(2, null, "fail", null, null, null));

        JooqConsumer consumer = (JooqConsumer) context.getRoute("delete-failed").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(2, consumer.poll());

        List<Integer> remaining = create.select(AUTHOR.ID).from(AUTHOR).orderBy(AUTHOR.ID).fetch(AUTHOR.ID);
        assertEquals(List.of(2), remaining, "only the successfully processed author must be deleted");
    }

    @Test
    void testRollbackOnlyExchangeKeepsRow() throws Exception {
        create.executeInsert(new AuthorRecord(1, null, "ok", null, null, null));
        create.executeInsert(new AuthorRecord(2, null, "rollback", null, null, null));

        JooqConsumer consumer = (JooqConsumer) context.getRoute("delete-failed").getConsumer();
        assertEquals(2, consumer.poll());

        List<Integer> remaining = create.select(AUTHOR.ID).from(AUTHOR).orderBy(AUTHOR.ID).fetch(AUTHOR.ID);
        assertEquals(List.of(2), remaining, "an author whose exchange was marked rollback only must not be deleted");
    }

    @Test
    void testNotProcessedEntitiesKeepRows() throws Exception {
        create.executeInsert(new AuthorRecord(1, null, "ok", null, null, null));
        create.executeInsert(new AuthorRecord(3, null, "ok", null, null, null));

        JooqConsumer consumer = (JooqConsumer) context.getRoute("delete-failed").getConsumer();
        // what the shutdown strategy calls when a graceful shutdown starts while a poll has fetched the entities:
        // the batch is then not processed
        consumer.deferShutdown(ShutdownRunningTask.CompleteCurrentTaskOnly);
        consumer.poll();

        assertEquals(2, create.fetchCount(AUTHOR), "entities that were not processed must not be deleted");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        JooqComponent jooqComponent = (JooqComponent) context().getComponent("jooq");
        JooqConfiguration jooqConfiguration = new JooqConfiguration();
        jooqConfiguration.setDatabaseConfiguration(create.configuration());
        jooqComponent.setConfiguration(jooqConfiguration);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jooq://org.apache.camel.component.jooq.db.tables.records.AuthorRecord?startScheduler=false")
                        .routeId("delete-failed")
                        .choice().when(simple("${body.lastName} == 'rollback'")).markRollbackOnly().end()
                        .process(exchange -> {
                            AuthorRecord author = exchange.getIn().getBody(AuthorRecord.class);
                            if ("fail".equals(author.getLastName())) {
                                throw new IllegalStateException("Simulated failure for author " + author.getId());
                            }
                        });
            }
        };
    }
}
