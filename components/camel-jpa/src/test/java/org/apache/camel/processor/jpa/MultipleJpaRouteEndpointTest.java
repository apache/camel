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
package org.apache.camel.processor.jpa;

import java.util.HashMap;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.JpaComponent;
import org.apache.camel.component.jpa.JpaConstants;
import org.apache.camel.component.jpa.JpaEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultipleJpaRouteEndpointTest extends CamelTestSupport {

    @Mock
    private final EntityManagerFactory emf1 = mock(EntityManagerFactory.class);
    @Mock
    private final EntityManager em1 = mock(EntityManager.class);
    @Mock
    private final EntityManagerFactory emf2 = mock(EntityManagerFactory.class);
    @Mock
    private final EntityManager em2 = mock(EntityManager.class);

    private final SendEmail value1 = new SendEmail("someone@somewhere.org");
    private final SendEmail value2 = new SendEmail("someone@somewhere.org");

    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.whenAnyExchangeReceived(this::assertEntityManagerMap);

        template.sendBody("direct:start", "start");

        MockEndpoint.assertIsSatisfied(context);
        verify(em1).merge(value1);
        verify(em2).merge(value2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        when(em1.getTransaction()).thenReturn(mock(EntityTransaction.class));
        when(emf1.createEntityManager()).thenReturn(em1);
        when(em2.getTransaction()).thenReturn(mock(EntityTransaction.class));
        when(emf2.createEntityManager()).thenReturn(em2);

        return new RouteBuilder() {
            public void configure() {
                JpaEndpoint jpa1 = new JpaEndpoint();
                jpa1.setComponent(new JpaComponent());
                jpa1.setCamelContext(context);
                jpa1.setEntityType(SendEmail.class);
                jpa1.setEntityManagerFactory(emf1);

                JpaEndpoint jpa2 = new JpaEndpoint();
                jpa2.setComponent(new JpaComponent());
                jpa2.setCamelContext(context);
                jpa2.setEntityType(SendEmail.class);
                jpa2.setEntityManagerFactory(emf2);

                from("direct:start")
                        .setBody(constant(value1))
                        .to(jpa1)
                        .setBody(constant(value2))
                        .to(jpa2)
                        .to("mock:result");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void assertEntityManagerMap(Exchange exchange) {
        HashMap<String, EntityManager> entityManagerMap = exchange.getProperty(JpaConstants.ENTITY_MANAGER, HashMap.class);
        assertNotNull(entityManagerMap);
        assertEquals(2, entityManagerMap.keySet().size());
        assertTrue(entityManagerMap.containsKey(String.valueOf(emf1.hashCode())));
        EntityManager entityManager1 = entityManagerMap.get(String.valueOf(emf1.hashCode()));
        assertTrue(entityManagerMap.containsKey(String.valueOf(emf2.hashCode())));
        EntityManager entityManager2 = entityManagerMap.get(String.valueOf(emf2.hashCode()));
        assertNotEquals(entityManager1, entityManager2);
    }
}
