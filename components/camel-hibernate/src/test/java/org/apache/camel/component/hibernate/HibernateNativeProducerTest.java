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
package org.apache.camel.component.hibernate;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HibernateNativeProducerTest extends CamelTestSupport {

    private EntityManagerFactory entityManagerFactory;
    private SessionFactory sessionFactory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        entityManagerFactory = Persistence.createEntityManagerFactory("camel-hibernate-tests");
        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);

        HibernateComponent component = new HibernateComponent();
        component.setSessionFactory(sessionFactory);
        camelContext.addComponent("hibernate", component);

        return camelContext;
    }

    @BeforeEach
    public void cleanDatabase() {
        if (sessionFactory == null) {
            return;
        }
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            session.createMutationQuery("DELETE FROM Item").executeUpdate();
            transaction.commit();
        }
    }

    @AfterEach
    public void tearDownPersistence() {
        if (context != null) {
            context.stop();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:persist")
                        .to("hibernate:org.apache.camel.component.hibernate.Item?usePersist=true");

                from("direct:query")
                        .to("hibernate:org.apache.camel.component.hibernate.Item");
            }
        };
    }

    @Test
    public void testNativeProducerPersistAndQuery() {
        Item item = new Item("NativePersist", 10.0);

        Item persisted = template.requestBody("direct:persist", item, Item.class);
        assertNotNull(persisted);

        List<?> results = template.requestBodyAndHeader(
                "direct:query",
                null,
                HibernateConstants.HIBERNATE_QUERY,
                "SELECT i FROM Item i WHERE i.name = 'NativePersist'",
                List.class);

        assertEquals(1, results.size());
        assertEquals("NativePersist", ((Item) results.get(0)).getName());
    }
}
