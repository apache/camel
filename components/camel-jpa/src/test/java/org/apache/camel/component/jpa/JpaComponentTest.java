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

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;

import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JpaComponentTest extends CamelTestSupport {

    @Test
    public void testJpaComponentConsumerHasLockModeType() throws Exception {
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            assertNull(comp.getEntityManagerFactory());
            assertNull(comp.getTransactionStrategy());

            JpaEndpoint jpa
                    = (JpaEndpoint) comp
                            .createEndpoint("jpa://" + SendEmail.class.getName() + "?lockModeType=PESSIMISTIC_WRITE");
            JpaConsumer consumer = (JpaConsumer) jpa.createConsumer(null);

            assertEquals(LockModeType.PESSIMISTIC_WRITE, consumer.getLockModeType());
        }
    }

    @Test
    public void testJpaComponentCtr() throws Exception {
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            assertNull(comp.getEntityManagerFactory());
            assertNull(comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
            assertNotNull(jpa);
            assertNotNull(jpa.getEntityType());
        }
    }

    @Test
    public void testJpaComponentEMFandTM() throws Exception {
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            assertNull(comp.getEntityManagerFactory());
            assertNull(comp.getTransactionStrategy());

            EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
            JpaTransactionManager tm = new JpaTransactionManager(fac);
            tm.afterPropertiesSet();

            comp.setEntityManagerFactory(fac);
            if (comp.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
                strategy.setTransactionManager(tm);
            }

            assertSame(fac, comp.getEntityManagerFactory());
            if (comp.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
                assertSame(tm, strategy.getTransactionManager());
            }

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
            assertNotNull(jpa);
            assertNotNull(jpa.getEntityType());
        }
    }

    @Test
    public void testJpaComponentWithPath() throws Exception {
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            assertNull(comp.getEntityManagerFactory());
            assertNull(comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint(
                    "jpa://" + SendEmail.class.getName() + "?persistenceUnit=journalPersistenceUnit&usePersist=true");
            assertNotNull(jpa);
            assertNotNull(jpa.getEntityType());
        }
    }

    @Test
    public void testJpaComponentEmptyPath() throws Exception {
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            assertNull(comp.getEntityManagerFactory());
            assertNull(comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa:?persistenceUnit=journalPersistenceUnit&usePersist=true");
            assertNotNull(jpa);
            assertNull(jpa.getEntityType());
        }
    }
}
