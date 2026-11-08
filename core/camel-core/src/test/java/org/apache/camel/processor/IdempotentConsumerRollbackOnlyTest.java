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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exchange marked rollback only (without an exception) did not complete successfully: the Idempotent Consumer must
 * remove its message id (removeOnFailure=true is the default), so a redelivered message is not dropped as a duplicate.
 */
class IdempotentConsumerRollbackOnlyTest extends ContextTestSupport {

    private final IdempotentRepository repository = MemoryIdempotentRepository.memoryIdempotentRepository(200);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:rollbackOnly").idempotentConsumer(header("messageId"), repository)
                        .to("mock:result")
                        .filter(header("rollback")).markRollbackOnly();

                from("direct:rollbackOnlyLast").idempotentConsumer(header("messageId"), repository)
                        .to("mock:result")
                        .filter(header("rollback")).markRollbackOnlyLast();

                from("direct:completionEager").idempotentConsumer(header("messageId"), repository).completionEager(true)
                        .to("mock:result")
                        .filter(header("rollback")).markRollbackOnly();

                from("direct:notEager").idempotentConsumer(header("messageId"), repository).eager(false)
                        .to("mock:result")
                        .filter(header("rollback")).markRollbackOnly();

                from("direct:keepOnFailure").idempotentConsumer(header("messageId"), repository).removeOnFailure(false)
                        .to("mock:result")
                        .filter(header("rollback")).markRollbackOnly();
            }
        };
    }

    @Test
    void testRollbackOnlyRemovesMessageId() throws Exception {
        assertRedeliveryProcessed("direct:rollbackOnly");
    }

    @Test
    void testRollbackOnlyLastRemovesMessageId() throws Exception {
        assertRedeliveryProcessed("direct:rollbackOnlyLast");
    }

    @Test
    void testRollbackOnlyCompletionEagerRemovesMessageId() throws Exception {
        assertRedeliveryProcessed("direct:completionEager");
    }

    @Test
    void testRollbackOnlyNotEagerDoesNotAddMessageId() throws Exception {
        assertRedeliveryProcessed("direct:notEager");
    }

    @Test
    void testRollbackOnlyKeepsMessageIdWhenNotRemovedOnFailure() throws Exception {
        // removeOnFailure=false keeps the message id of a failed exchange, so the redelivery is a duplicate
        getMockEndpoint("mock:result").expectedBodiesReceived("one");

        send("direct:keepOnFailure", true);
        assertTrue(repository.contains("1"));
        send("direct:keepOnFailure", false);

        assertMockEndpointsSatisfied();
    }

    private void assertRedeliveryProcessed(String uri) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("one", "one");

        Exchange rolledBack = send(uri, true);
        assertTrue(rolledBack.isRollbackOnly() || rolledBack.isRollbackOnlyLast());
        assertNull(rolledBack.getException());
        assertFalse(repository.contains("1"), "The message id of a rollback only exchange should be removed");

        // the message is redelivered, and processed this time
        send(uri, false);

        assertMockEndpointsSatisfied();
        assertTrue(repository.contains("1"));
    }

    private Exchange send(String uri, boolean rollback) {
        return template.send(uri, exchange -> {
            exchange.getIn().setBody("one");
            exchange.getIn().setHeader("messageId", "1");
            exchange.getIn().setHeader("rollback", rollback);
        });
    }
}
