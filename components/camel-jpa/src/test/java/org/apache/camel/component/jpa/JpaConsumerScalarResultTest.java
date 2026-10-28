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

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.processor.jpa.AbstractJpaTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A query that selects values that are not entities (such as a column) delivers them with the default
 * consumeLockEntity=true and consumeDelete=true: there is no entity to lock or to delete.
 */
class JpaConsumerScalarResultTest extends AbstractJpaTest {

    private static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    void testScalarResultsAreDelivered() throws Exception {
        saveEntityInDB(new SendEmail("bar@beer.org"));
        saveEntityInDB(new SendEmail("foo@beer.org"));
        // a NULL column gives a message with a null body
        saveEntityInDB(new SendEmail(null));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("bar@beer.org", "foo@beer.org", null);

        JpaConsumer consumer = (JpaConsumer) context.getRoute("scalar").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(3, consumer.poll());

        mock.assertIsSatisfied();
        entityManager.clear();
        List<?> rows = entityManager.createQuery(SELECT_ALL_STRING).getResultList();
        assertEquals(3, rows.size(), "a value that is not an entity cannot be deleted, so the rows stay: " + rows);

        // the values were not deleted, so the next poll delivers them again
        assertEquals(3, consumer.poll());
        assertEquals(6, mock.getReceivedCounter());
    }

    @Test
    void testNoLockIsAttemptedForAValueThatIsNotAnEntity() {
        // a failed lock marks the transaction rollback only on some JPA providers, so it must not be attempted
        EntityManager em = mock(EntityManager.class);
        Metamodel metamodel = entityManager.getMetamodel();
        when(em.getMetamodel()).thenReturn(metamodel);
        JpaConsumer consumer = (JpaConsumer) context.getRoute("scalar").getConsumer();

        assertTrue(consumer.lockEntity("bar@beer.org", em), "a selected column has nothing to lock");
        assertTrue(consumer.lockEntity(new Object[] { 1L, "bar@beer.org" }, em),
                "the Object array of a native query has nothing to lock");
        verify(em, never()).lock(any(), any());
        verify(em, never()).lock(any(), any(), anyMap());

        SendEmail entity = new SendEmail("bar@beer.org");
        assertTrue(consumer.lockEntity(entity, em), "an entity is locked");
        verify(em).lock(eq(entity), any());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jpa://" + SendEmail.class.getName() + "?query=select x.address from " + SendEmail.class.getName()
                     + " x&startScheduler=false").routeId("scalar")
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
