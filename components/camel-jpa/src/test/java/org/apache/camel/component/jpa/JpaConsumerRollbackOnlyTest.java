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
package org.apache.camel.component.jpa;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.PersistenceException;

import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.MultiSteps;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.processor.jpa.AbstractJpaTest;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An entity whose exchange was marked rollback only (without an exception) must not be consumed: the consumer must not
 * delete it (or call its @Consumed callback), and with transacted=true the whole poll must roll back.
 */
public class JpaConsumerRollbackOnlyTest extends AbstractJpaTest {

    private static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";
    private static final String KABOOM = "kaboom@beer.org";

    @Test
    public void testNonTransactedRollbackOnlyEntityIsNotDeleted() throws Exception {
        insertSendEmails("foo@beer.org", KABOOM, "bar@beer.org");

        JpaConsumer consumer = (JpaConsumer) context.getRoute("non-tx").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(3, consumer.poll());

        assertEquals(List.of(KABOOM), sendEmailAddresses(),
                "only the entity whose exchange was marked rollback only must stay in the table");
        assertEquals(2, getMockEndpoint("mock:result").getReceivedCounter());

        // the rollback only entity is consumed again by the next poll
        assertEquals(1, consumer.poll());
        assertEquals(List.of(KABOOM), sendEmailAddresses());
        assertEquals(4, getMockEndpoint("mock:start").getReceivedCounter());
    }

    @Test
    public void testTransactedRollbackOnlyEntityRollsBackThePoll() throws Exception {
        insertSendEmails("foo@beer.org", KABOOM, "bar@beer.org");

        JpaConsumer consumer = (JpaConsumer) context.getRoute("tx").getConsumer();
        PersistenceException e = assertThrows(PersistenceException.class, consumer::poll,
                "a rollback only exchange must roll back a transacted poll");
        assertNotNull(ObjectHelper.getException(RollbackExchangeException.class, e),
                "the poll must be rolled back by a RollbackExchangeException");

        assertEquals(List.of("bar@beer.org", "foo@beer.org", KABOOM), sendEmailAddresses(),
                "no entity must be deleted when the transacted poll is rolled back");
    }

    @Test
    public void testNonTransactedRollbackOnlyEntityIsNotConsumedCallback() throws Exception {
        for (String address : List.of("foo@beer.org", KABOOM, "bar@beer.org")) {
            saveEntityInDB(new MultiSteps(address));
        }

        JpaConsumer consumer = (JpaConsumer) context.getRoute("consumed").getConsumer();
        assertEquals(3, consumer.poll());

        entityManager.clear();
        List<?> step1 = entityManager.createQuery("select x from " + MultiSteps.class.getName() + " x where x.step = 1")
                .getResultList();
        assertEquals(1, step1.size(), "the @Consumed callback must not be called for the rollback only entity");
        assertEquals(KABOOM, ((MultiSteps) step1.get(0)).getAddress());
    }

    private void insertSendEmails(String... addresses) {
        for (String address : addresses) {
            saveEntityInDB(new SendEmail(address));
        }
    }

    private List<String> sendEmailAddresses() {
        entityManager.clear();
        List<?> list = entityManager.createQuery(SELECT_ALL_STRING).getResultList();
        List<String> answer = new ArrayList<>();
        for (Object item : list) {
            answer.add(((SendEmail) item).getAddress());
        }
        answer.sort(null);
        return answer;
    }

    @Override
    protected void cleanupRepository() {
        super.cleanupRepository();
        transactionTemplate.execute(status -> {
            entityManager.joinTransaction();
            List<?> list = entityManager.createQuery("select x from " + MultiSteps.class.getName() + " x").getResultList();
            for (Object item : list) {
                entityManager.remove(item);
            }
            entityManager.flush();
            return Boolean.TRUE;
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jpa://" + SendEmail.class.getName() + "?transacted=false&startScheduler=false").routeId("non-tx")
                        .to("mock:start")
                        .choice().when(simple("${body.address} == '" + KABOOM + "'")).markRollbackOnly().end()
                        .to("mock:result");

                from("jpa://" + SendEmail.class.getName() + "?transacted=true&startScheduler=false").routeId("tx")
                        .choice().when(simple("${body.address} == '" + KABOOM + "'")).markRollbackOnly().end()
                        .to("mock:result");

                from("jpa://" + MultiSteps.class.getName() + "?query=select x from " + MultiSteps.class.getName()
                     + " x where x.step = 1&startScheduler=false").routeId("consumed")
                        .choice().when(simple("${body.address} == '" + KABOOM + "'")).markRollbackOnly().end()
                        .to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}
