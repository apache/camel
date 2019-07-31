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
import org.apache.camel.component.jooq.beans.BookStoreRecordBean;
import org.apache.camel.component.jooq.db.tables.records.AuthorRecord;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.apache.camel.component.mock.MockEndpoint;
import org.jooq.Result;
import org.junit.Assert;
import org.junit.Test;

public class JooqConsumerTest extends BaseJooqTest {

    @Test
    public void testConsumerConfig() {
        JooqConsumer consumer = (JooqConsumer) context.getRoute("consumer-config").getConsumer();
        Assert.assertEquals(1000, consumer.getInitialDelay());
        Assert.assertEquals(2000, consumer.getDelay());
    }

    @Test
    public void testConsumerNoDelete() throws InterruptedException {
        MockEndpoint mockResult = getMockEndpoint("mock:resultBookStoreRecord");
        MockEndpoint mockInserted = getMockEndpoint("mock:insertedBookStoreRecord");
        mockResult.expectedMessageCount(1);
        mockInserted.expectedMessageCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert
        BookStoreRecord bookStoreRecord = new BookStoreRecord("test");
        producerTemplate.sendBody(context.getEndpoint("direct:insertBookStoreRecord"), ExchangePattern.InOut, bookStoreRecord);

        assertMockEndpointsSatisfied();
        Assert.assertEquals(bookStoreRecord, mockInserted.getExchanges().get(0).getMessage().getBody());
        Assert.assertEquals(1, ((Result)mockResult.getExchanges().get(0).getMessage().getBody()).size());
    }

    @Test
    public void testConsumerDelete() throws InterruptedException {
        MockEndpoint mockResult = getMockEndpoint("mock:resultAuthorRecord");
        MockEndpoint mockInserted = getMockEndpoint("mock:insertedAuthorRecord");
        mockResult.expectedMessageCount(1);
        mockInserted.expectedMessageCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert
        AuthorRecord authorRecord = new AuthorRecord(1, null, "test", null, null, null);
        producerTemplate.sendBody(context.getEndpoint("direct:insertAuthorRecord"), ExchangePattern.InOut, authorRecord);

        assertMockEndpointsSatisfied();
        Assert.assertEquals(authorRecord, mockInserted.getExchanges().get(0).getMessage().getBody());
        Assert.assertEquals(0, ((Result)mockResult.getExchanges().get(0).getMessage().getBody()).size());
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
                // Only for configuration test
                from("jooq://org.apache.camel.component.jooq.db.tables.records.BookRecord?initialDelay=1000&delay=2000")
                        .id("consumer-config")
                        .to("log:foo");

                // Book store
                from("direct:insertBookStoreRecord")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord");

                from("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?consumeDelete=false&initialDelay=0&delay=100")
                        .to("mock:insertedBookStoreRecord")
                        .transform()
                        .method(BookStoreRecordBean.class, "select")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord/fetch")
                        .to("mock:resultBookStoreRecord");

                // Author
                from("direct:insertAuthorRecord")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.AuthorRecord");

                from("jooq://org.apache.camel.component.jooq.db.tables.records.AuthorRecord?initialDelay=0&delay=100")
                        .to("mock:insertedAuthorRecord")
                        .transform()
                        .method(BookStoreRecordBean.class, "select")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.AuthorRecord/fetch")
                        .to("mock:resultAuthorRecord");
            }
        };
    }

}
