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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.jooq.Result;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JooqPlainSQLTest extends BaseJooqTest {

    @Test
    public void testSQLProducer() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert
        BookStoreRecord bookStoreRecord = new BookStoreRecord("test");
        producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, bookStoreRecord);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Select
            Result res
                    = (Result) producerTemplate.sendBody(context.getEndpoint("direct:selectSQL"), ExchangePattern.InOut, null);
            assertEquals(1, res.size());
            assertEquals(bookStoreRecord, res.get(0));
        });

        // Delete
        Result actual
                = (Result) producerTemplate.sendBody(context.getEndpoint("direct:deleteSQL"), ExchangePattern.InOut, null);
        assertNull(actual);

        // Select
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Result res
                    = (Result) producerTemplate.sendBody(context.getEndpoint("direct:selectSQL"), ExchangePattern.InOut, null);
            assertEquals(0, res.size());
        });
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
                // Book store
                from("direct:insert")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord");

                // Producer SQL query select
                from("direct:selectSQL")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=fetch&query=select * from book_store x where x.name = 'test'");

                // Producer SQL query delete
                from("direct:deleteSQL")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=execute&query=delete from book_store x where x.name = 'test'");
            }
        };
    }

}
