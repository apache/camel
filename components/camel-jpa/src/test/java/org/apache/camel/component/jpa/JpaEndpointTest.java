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

import java.io.IOException;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JpaEndpointTest extends CamelTestSupport {

    @Test
    public void testJpaEndpointCtr() throws IOException {
        JpaEndpoint jpa = new JpaEndpoint();
        jpa.setEntityType(SendEmail.class);

        assertNotNull(jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionStrategy());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
        jpa.close();
    }

    /**
     *
     * @throws     IOException
     * @deprecated
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrl() throws IOException {
        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail", null);
        jpa.setEntityType(SendEmail.class);

        assertNotNull(jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionStrategy());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
        jpa.close();
    }

    /**
     *
     * @throws     IOException
     * @deprecated
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrlEMF() throws IOException {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");

        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail", null);
        jpa.setEntityManagerFactory(fac);
        jpa.setEntityType(SendEmail.class);

        assertSame(fac, jpa.getEntityManagerFactory());
        assertNotNull(jpa.getTransactionStrategy());

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
        jpa.close();
    }

    /**
     *
     * @throws     IOException
     * @deprecated
     */
    @Deprecated
    @Test
    public void testJpaEndpointCtrUrlEMFandTM() throws IOException {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        JpaTransactionManager tm = new JpaTransactionManager(fac);
        tm.afterPropertiesSet();

        JpaEndpoint jpa = new JpaEndpoint("jpa://org.apache.camel.examples.SendEmail", null);
        jpa.setEntityManagerFactory(fac);
        if (jpa.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            strategy.setTransactionManager(tm);
        }
        jpa.setEntityType(SendEmail.class);

        assertSame(fac, jpa.getEntityManagerFactory());
        if (jpa.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            assertSame(tm, strategy.getTransactionManager());
        }

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
        jpa.close();
    }

    @Test
    public void testJpaEndpointCustomEMFandTM() throws IOException {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        JpaTransactionManager tm = new JpaTransactionManager(fac);
        tm.afterPropertiesSet();

        JpaEndpoint jpa = new JpaEndpoint();
        jpa.setEntityType(SendEmail.class);

        jpa.setEntityManagerFactory(fac);
        if (jpa.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            strategy.setTransactionManager(tm);
        }

        assertSame(fac, jpa.getEntityManagerFactory());
        if (jpa.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            assertSame(tm, strategy.getTransactionManager());
        }

        assertEquals("jpa://org.apache.camel.examples.SendEmail", jpa.getEndpointUri());
        assertEquals("camel", jpa.getPersistenceUnit());
        jpa.close();
    }
}
