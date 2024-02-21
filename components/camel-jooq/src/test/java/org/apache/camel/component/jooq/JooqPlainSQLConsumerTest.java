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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JooqPlainSQLConsumerTest extends BaseJooqTest {

    @Test
    public void testSQLConsumer() throws InterruptedException {
        MockEndpoint mockResult = getMockEndpoint("mock:result");
        mockResult.expectedMessageCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert
        BookStoreRecord bookStoreRecord = new BookStoreRecord("test");
        producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, bookStoreRecord);

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(bookStoreRecord, mockResult.getExchanges().get(0).getMessage().getBody());
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

                // Consumer SQL query
                from("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?initialDelay=0&delay=100")
                        .to("mock:result");
            }
        };
    }

}
