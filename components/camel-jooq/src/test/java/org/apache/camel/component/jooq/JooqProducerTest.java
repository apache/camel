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

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.jooq.Query;
import org.jooq.Result;
import org.jooq.ResultQuery;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.component.jooq.db.Tables.BOOK_STORE;

public class JooqProducerTest extends BaseJooqTest {

    @Test
    public void testCRUD() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert and select
        BookStoreRecord bookStoreRecord = new BookStoreRecord("test");
        producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, bookStoreRecord);
        ResultQuery querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq("test"));
        Result actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(bookStoreRecord, actual.get(0));

        // Update and select
        String newName = "testNew";
        Query query = create.update(BOOK_STORE).set(BOOK_STORE.NAME, newName).where(BOOK_STORE.NAME.eq("test"));
        producerTemplate.sendBody(context.getEndpoint("direct:update"), ExchangePattern.InOut, query);
        querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(newName, ((BookStoreRecord)actual.get(0)).getName());

        // Delete and select
        query = create.delete(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        producerTemplate.sendBody(context.getEndpoint("direct:delete"), ExchangePattern.InOut, query);
        querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(0, actual.size());
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
                from("direct:insert")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord");

                from("direct:update")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=execute");

                from("direct:delete")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=execute");

                from("direct:select")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=fetch");
            }
        };
    }

}
