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
package org.apache.camel.processor.jpa;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
public abstract class AbstractJpaTest extends CamelTestSupport {
    protected ApplicationContext applicationContext;
    protected TransactionTemplate transactionTemplate;
    protected EntityManager entityManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        EntityManagerFactory entityManagerFactory = applicationContext.getBean("entityManagerFactory",
                                                                               EntityManagerFactory.class);
        transactionTemplate = applicationContext.getBean("transactionTemplate", TransactionTemplate.class);
        entityManager = entityManagerFactory.createEntityManager();
        cleanupRepository();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (entityManager != null) {
            entityManager.close();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext(routeXml());
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    protected void cleanupRepository() {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();
                List<?> list = entityManager.createQuery(selectAllString()).getResultList();
                for (Object item : list) {
                    entityManager.remove(item);
                }
                entityManager.flush();
                return Boolean.TRUE;
            }
        });
    }

    protected void assertEntityInDB(int size) throws Exception {
        assertEntityInDB(size, SendEmail.class);
    }

    protected void assertEntityInDB(int size, Class entityType) {
        List<?> results = entityManager.createQuery("select o from " + entityType.getName() + " o").getResultList();
        assertEquals(size, results.size());

        assertIsInstanceOf(entityType, results.get(0));
    }

    protected void saveEntityInDB(final Object entity) {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.persist(entity);
                entityManager.flush();
                return null;
            }
        });
    }
    
    protected abstract String routeXml();
    
    protected abstract String selectAllString();
}
