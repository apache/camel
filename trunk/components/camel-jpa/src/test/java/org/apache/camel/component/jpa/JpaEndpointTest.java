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
import javax.persistence.Persistence;

import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * @version 
 */
public class JpaEndpointTest extends CamelTestSupport {

    @Test
    public void testJpaEndpointCtr() throws Exception {
        JpaEndpoint jpa = new JpaEndpoint();
        jpa.setEntityType(SendEmail.class);

        assertNotNull(jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionManager());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
    }

    /**
     * 
     * @deprecated 
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrl() throws Exception {
        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail");
        jpa.setEntityType(SendEmail.class);

        assertNotNull(jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionManager());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
    }

    /**
     * 
     * @deprecated
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrlEMF() throws Exception {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");

        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail", fac);
        jpa.setEntityType(SendEmail.class);

        assertSame(fac, jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionManager());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
    }

    /**
     * 
     * @deprecated
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrlEMFandTM() throws Exception {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        JpaTransactionManager tm = new JpaTransactionManager(fac);
        tm.afterPropertiesSet();

        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail", fac, tm);
        jpa.setEntityType(SendEmail.class);

        assertSame(fac, jpa.getEntityManagerFactory());
        assertSame(tm, jpa.getTransactionManager());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
    }

    @Test
    public void testJpaEndpointCustomEMFandTM() throws Exception {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        JpaTransactionManager tm = new JpaTransactionManager(fac);
        tm.afterPropertiesSet();

        JpaEndpoint jpa = new JpaEndpoint();
        jpa.setEntityType(SendEmail.class);

        jpa.setEntityManagerFactory(fac);
        jpa.setTransactionManager(tm);

        assertSame(fac, jpa.getEntityManagerFactory());
        assertSame(tm, jpa.getTransactionManager());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
    }
}
