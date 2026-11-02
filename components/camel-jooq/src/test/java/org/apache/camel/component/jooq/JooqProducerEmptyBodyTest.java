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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jooq.db.Tables.BOOK_STORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message with no body must fail with a clear message, and must not store an empty record.
 */
public class JooqProducerEmptyBodyTest extends BaseJooqTest {

    @Test
    public void testStoreWithNoBodyFails() {
        InvalidPayloadException cause = sendNullBody("direct:insert");
        assertEquals(BookStoreRecord.class, cause.getType());
        assertTrue(cause.getMessage().contains("operation NONE needs a record of the entityType"), cause.getMessage());

        assertEquals(0, create.fetchCount(BOOK_STORE), "An empty record must not be stored");
    }

    @Test
    public void testExecuteWithNoBodyFails() {
        InvalidPayloadException cause = sendNullBody("direct:execute");
        assertTrue(cause.getMessage().contains("operation EXECUTE needs an org.jooq.Query in the message body, or the query"),
                cause.getMessage());
    }

    @Test
    public void testFetchWithNoBodyFails() {
        InvalidPayloadException cause = sendNullBody("direct:fetch");
        assertTrue(
                cause.getMessage().contains("operation FETCH needs an org.jooq.ResultQuery in the message body, or the query"),
                cause.getMessage());
    }

    private InvalidPayloadException sendNullBody(String uri) {
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> producerTemplate.sendBody(uri, null));
        InvalidPayloadException cause = ObjectHelper.getException(InvalidPayloadException.class, e);
        assertNotNull(cause, "Expected an InvalidPayloadException but got: " + e);
        return cause;
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

                from("direct:execute")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=execute");

                from("direct:fetch")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=fetch");
            }
        };
    }
}
