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
package org.apache.camel.component.mybatis;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The onConsume statements must only run for a row whose exchange was processed successfully: a failed or rollback only
 * exchange must leave the row unprocessed, so the next poll consumes it again.
 */
class MyBatisOnConsumeFailedTest extends MyBatisTestSupport {

    @Override
    protected boolean createTestData() {
        return false;
    }

    @Override
    protected String getCreateStatement() {
        return "create table ACCOUNT (ACC_ID INTEGER, ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255), PROCESSED BOOLEAN DEFAULT false)";
    }

    @Test
    void testFailedExchangeIsNotConsumed() throws Exception {
        insertAccounts(account(1, "ok"), account(2, "fail"));

        MyBatisConsumer consumer = (MyBatisConsumer) context.getRoute("on-consume").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(2, consumer.poll());

        assertEquals(List.of(2), unprocessedAccountIds(), "only the successfully processed account must be consumed");
    }

    @Test
    void testRollbackOnlyExchangeIsNotConsumed() throws Exception {
        insertAccounts(account(1, "ok"), account(2, "rollback"));

        MyBatisConsumer consumer = (MyBatisConsumer) context.getRoute("on-consume").getConsumer();
        assertEquals(2, consumer.poll());

        assertEquals(List.of(2), unprocessedAccountIds(),
                "an account whose exchange was marked rollback only must not be consumed");
    }

    @Test
    void testRollbackOnlyLastExchangeIsNotConsumed() throws Exception {
        insertAccounts(account(1, "ok"), account(2, "rollbackLast"));

        MyBatisConsumer consumer = (MyBatisConsumer) context.getRoute("on-consume").getConsumer();
        assertEquals(2, consumer.poll());

        assertEquals(List.of(2), unprocessedAccountIds(),
                "an account whose exchange was marked rollback only last must not be consumed");
    }

    @Test
    void testTransactedRollbackOnlyExchangeBreaksOutOfBatch() throws Exception {
        insertAccounts(account(1, "ok"), account(2, "rollback"), account(3, "ok"));

        MyBatisConsumer consumer = (MyBatisConsumer) context.getRoute("transacted").getConsumer();
        RollbackExchangeException e = assertThrows(RollbackExchangeException.class, consumer::poll,
                "a rollback only exchange must break out of a transacted batch, as a failed exchange does");
        // there was no error, and the message (logged by the consumer at every such poll) must not say so
        assertTrue(e.getMessage().startsWith("Rollback transaction as the exchange was marked as rollback only"),
                e.getMessage());

        assertEquals(List.of(2, 3), unprocessedAccountIds(),
                "neither the rollback only account nor the accounts after it must be consumed");
        assertEquals(1, getMockEndpoint("mock:processed").getReceivedCounter());
    }

    private void insertAccounts(Account... accounts) {
        template.sendBody("mybatis:insertAccount?statementType=Insert", accounts);
    }

    private static Account account(int id, String lastName) {
        Account account = new Account();
        account.setId(id);
        account.setFirstName("Account" + id);
        account.setLastName(lastName);
        account.setEmailAddress("account" + id + "@example.com");
        return account;
    }

    private List<Integer> unprocessedAccountIds() {
        List<?> accounts = template.requestBody("mybatis:selectUnprocessedAccounts?statementType=SelectList", null,
                List.class);
        List<Integer> ids = new ArrayList<>();
        for (Object account : accounts) {
            ids.add(((Account) account).getId());
        }
        return ids;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                consume("mybatis:selectUnprocessedAccounts?onConsume=consumeAccount&startScheduler=false", "on-consume");
                consume("mybatis:selectUnprocessedAccounts?onConsume=consumeAccount&transacted=true&startScheduler=false",
                        "transacted");
            }

            private void consume(String uri, String routeId) {
                from(uri).routeId(routeId)
                        .choice()
                        .when(simple("${body.lastName} == 'rollback'")).markRollbackOnly()
                        .when(simple("${body.lastName} == 'rollbackLast'")).markRollbackOnlyLast()
                        .end()
                        .process(exchange -> {
                            Account account = exchange.getIn().getBody(Account.class);
                            if ("fail".equals(account.getLastName())) {
                                throw new IllegalStateException("Simulated failure for account " + account.getId());
                            }
                        })
                        .to("mock:processed");
            }
        };
    }
}
