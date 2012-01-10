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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
public class JpaProducerConcurrentTest extends CamelTestSupport {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    protected ApplicationContext applicationContext;
    protected JpaTemplate jpaTemplate;

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").assertNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        Map<Integer, Future> responses = new ConcurrentHashMap<Integer, Future>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future out = executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBody("direct:start", new SendEmail("user" + index + "@somewhere.org"));
                    return null;
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();

        assertEquals(files, responses.size());

        // get them so they are complete
        for (Future future : responses.values()) {
            future.get();
        }

        // assert in the database
        assertEntityInDB(files);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/processor/jpa/springJpaRouteTest.xml");
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start").to("jpa://" + SendEmail.class.getName()).to("mock:result");
            }
        };
    }

    private void assertEntityInDB(int number) throws Exception {
        jpaTemplate = (JpaTemplate)applicationContext.getBean("jpaTemplate", JpaTemplate.class);

        List list = jpaTemplate.find(SELECT_ALL_STRING);
        assertEquals(number, list.size());

        assertIsInstanceOf(SendEmail.class, list.get(0));
    }

    @SuppressWarnings("unchecked")
    protected void cleanupRepository() {
        jpaTemplate = (JpaTemplate)applicationContext.getBean("jpaTemplate", JpaTemplate.class);

        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(SELECT_ALL_STRING);
                for (Object item : list) {
                    jpaTemplate.remove(item);
                }
                jpaTemplate.flush();
                return Boolean.TRUE;
            }
        });
    }
}