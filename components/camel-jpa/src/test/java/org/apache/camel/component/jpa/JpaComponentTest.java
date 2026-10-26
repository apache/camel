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
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;

import org.apache.camel.examples.SendEmail;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testJpaComponentDefaultTransactionStrategy() throws Exception {
        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            comp.setEntityManagerFactory(fac);
            comp.init();
            assertInstanceOf(DefaultTransactionStrategy.class, comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
            jpa.init();
            assertSame(comp.getTransactionStrategy(), jpa.getTransactionStrategy());
        } finally {
            fac.close();
        }
    }

    @Test
    public void testJpaComponentTransactionStrategyFromRegistry() throws Exception {
        TransactionStrategy strategy = Runnable::run;
        context.getRegistry().bind("myTransactionStrategy", strategy);

        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            comp.setEntityManagerFactory(fac);
            comp.init();
            assertSame(strategy, comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
            jpa.init();
            assertSame(strategy, jpa.getTransactionStrategy());
        } finally {
            fac.close();
        }
    }

    @Test
    public void testJpaEndpointTransactionStrategyFromRegistry() throws Exception {
        TransactionStrategy strategy = Runnable::run;
        context.getRegistry().bind("myTransactionStrategy", strategy);

        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            comp.init();
            // no EntityManagerFactory on the component, so the endpoint resolves its own strategy
            assertNull(comp.getTransactionStrategy());

            JpaEndpoint jpa = (JpaEndpoint) comp.createEndpoint("jpa://" + SendEmail.class.getName());
            jpa.init();
            assertSame(strategy, jpa.getTransactionStrategy());
        }
    }

    @Test
    public void testJpaComponentMoreTransactionStrategiesInRegistry() throws Exception {
        TransactionStrategy strategy = Runnable::run;
        TransactionStrategy otherStrategy = Runnable::run;
        context.getRegistry().bind("myTransactionStrategy", strategy);
        context.getRegistry().bind("myOtherTransactionStrategy", otherStrategy);

        List<String> warnings = new CopyOnWriteArrayList<>();
        Logger logger = (Logger) LogManager.getLogger(JpaHelper.class);
        AbstractAppender appender = new AbstractAppender("warnings", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        logger.addAppender(appender);

        EntityManagerFactory fac = Persistence.createEntityManagerFactory("camel");
        try (JpaComponent comp = new JpaComponent()) {
            comp.setCamelContext(context);
            comp.setEntityManagerFactory(fac);
            comp.init();
            // none of them is used
            assertInstanceOf(DefaultTransactionStrategy.class, comp.getTransactionStrategy());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).startsWith("Found 2 TransactionStrategy beans in the registry"));
        } finally {
            logger.removeAppender(appender);
            appender.stop();
            fac.close();
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
