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

import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.metamodel.Metamodel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.processor.jpa.AbstractJpaTest;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The lockTimeout option is given to the JPA provider as the jakarta.persistence.lock.timeout hint of the lock of each
 * entity. JpaConsumerLockTimeoutIT shows its effect on PostgreSQL.
 */
class JpaConsumerLockTimeoutTest extends AbstractJpaTest {

    private static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    void lockTimeoutIsGivenToTheLock() {
        SendEmail entity = new SendEmail("bar@beer.org");
        EntityManager em = entityManagerWithMetamodel();

        assertTrue(consumer("lockTimeout").lockEntity(entity, em));
        verify(em).lock(entity, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 1500));
    }

    @Test
    void noHintWithoutLockTimeout() {
        SendEmail entity = new SendEmail("bar@beer.org");
        EntityManager em = entityManagerWithMetamodel();

        assertTrue(consumer("default").lockEntity(entity, em));
        verify(em).lock(entity, LockModeType.PESSIMISTIC_WRITE);
        verify(em, never()).lock(eq(entity), eq(LockModeType.PESSIMISTIC_WRITE), anyMap());
    }

    @Test
    void skipLockedEntityDoesNotWaitWhateverTheLockTimeout() {
        SendEmail entity = new SendEmail("bar@beer.org");
        EntityManager em = entityManagerWithMetamodel();

        assertTrue(consumer("skipLocked").lockEntity(entity, em));
        // an Integer, as the hint of lockTimeout
        verify(em).lock(entity, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 0));
    }

    @Test
    void negativeLockTimeoutInTheUriIsRejected() {
        // a negative duration converts without error ("-1", "-1s" or "-PT1S"), and would wait for ever
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jpa://" + SendEmail.class.getName() + "?startScheduler=false&lockTimeout=-1s").to("mock:result");
            }
        }));
        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, e);
        assertNotNull(cause, "the route must not start with a negative lockTimeout");
        assertEquals("lockTimeout must be >= 0, was: -1000", cause.getMessage());
    }

    @Test
    void negativeLockTimeoutOfTheConsumerIsRejected() throws Exception {
        JpaEndpoint endpoint = context.getEndpoint("jpa://" + SendEmail.class.getName(), JpaEndpoint.class);
        JpaConsumer consumer = (JpaConsumer) endpoint.createConsumer(exchange -> {
        });
        consumer.setLockTimeout(-1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, consumer::init);
        assertEquals("lockTimeout must be >= 0, was: -1", e.getMessage());
    }

    private EntityManager entityManagerWithMetamodel() {
        EntityManager em = mock(EntityManager.class);
        Metamodel metamodel = entityManager.getMetamodel();
        when(em.getMetamodel()).thenReturn(metamodel);
        return em;
    }

    private JpaConsumer consumer(String routeId) {
        return (JpaConsumer) context.getRoute(routeId).getConsumer();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "jpa://" + SendEmail.class.getName() + "?startScheduler=false";
                from(uri + "&lockTimeout=1500").routeId("lockTimeout").to("mock:result");
                from(uri).routeId("default").to("mock:result");
                from(uri + "&lockTimeout=1500&skipLockedEntity=true").routeId("skipLocked").to("mock:result");
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
