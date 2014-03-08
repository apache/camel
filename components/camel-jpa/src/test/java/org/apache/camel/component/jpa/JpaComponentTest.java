/**
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

import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;

import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * @version
 */
public class JpaComponentTest extends CamelTestSupport {

    @Test
    public void testJpaComponentConsumerHasLockModeType() throws Exception {
        JpaComponent comp = new JpaComponent();
        comp.setCamelContext(context);
        assertNull(comp.getEntityManagerFactory());
        assertNull(comp.getTransactionManager());

        JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName() + "?consumer.lockModeType=PESSIMISTIC_WRITE");
        JpaConsumer consumer = (JpaConsumer) jpa.createConsumer(null);

        assertEquals(consumer.getLockModeType(), LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    public void testJpaComponentCtr() throws Exception {
        JpaComponent comp = new JpaComponent();
        comp.setCamelContext(context);
        assertNull(comp.getEntityManagerFactory());
        assertNull(comp.getTransactionManager());

        JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
        assertNotNull(jpa);
        assertNotNull(jpa.getEntityType());
    }

    @Test
    public void testJpaComponentEMFandTM() throws Exception {
        JpaComponent comp = new JpaComponent();
        comp.setCamelContext(context);
        assertNull(comp.getEntityManagerFactory());
        assertNull(comp.getTransactionManager());

        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        JpaTransactionManager tm = new JpaTransactionManager(fac);
        tm.afterPropertiesSet();

        comp.setEntityManagerFactory(fac);
        comp.setTransactionManager(tm);

        assertSame(fac, comp.getEntityManagerFactory());
        assertSame(tm, comp.getTransactionManager());

        JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
        assertNotNull(jpa);
        assertNotNull(jpa.getEntityType());
    }

    @Test
    public void testJpaComponentWithPath() throws Exception {
        JpaComponent comp = new JpaComponent();
        comp.setCamelContext(context);
        assertNull(comp.getEntityManagerFactory());
        assertNull(comp.getTransactionManager());

        JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName() + "?persistenceUnit=journalPersistenceUnit&usePersist=true");
        assertNotNull(jpa);
        assertNotNull(jpa.getEntityType());
    }

    @Test
    public void testJpaComponentEmptyPath() throws Exception {
        JpaComponent comp = new JpaComponent();
        comp.setCamelContext(context);
        assertNull(comp.getEntityManagerFactory());
        assertNull(comp.getTransactionManager());

        JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa:?persistenceUnit=journalPersistenceUnit&usePersist=true");
        assertNotNull(jpa);
        assertNull(jpa.getEntityType());
    }
}
