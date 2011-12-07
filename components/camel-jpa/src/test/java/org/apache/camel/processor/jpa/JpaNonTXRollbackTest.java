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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringCamelContext;
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
public class JpaNonTXRollbackTest extends CamelTestSupport {

    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";
    private static AtomicInteger foo = new AtomicInteger();
    private static AtomicInteger bar = new AtomicInteger();
    private static AtomicInteger kaboom = new AtomicInteger();

    protected ApplicationContext applicationContext;
    protected JpaTemplate jpaTemplate;

    @Test
    public void testNonTXRollback() throws Exception {
        // first create three records
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("foo@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("bar@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("kaboom@beer.org"));

        // should only rollback the failed
        getMockEndpoint("mock:start").expectedMinimumMessageCount(5);
        // and only the 2 good messages goes here
        getMockEndpoint("mock:result").expectedMessageCount(2);

        // start route
        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        assertEquals(1, foo.intValue());
        assertEquals(1, bar.intValue());

        // kaboom fails and we retry it again
        assertTrue("Should be >= 2, was: " + kaboom.intValue(), kaboom.intValue() >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jpa://" + SendEmail.class.getName() + "?consumer.transacted=false&delay=100").routeId("foo").noAutoStartup()
                        .to("mock:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                SendEmail send = exchange.getIn().getBody(SendEmail.class);
                                if ("kaboom@beer.org".equals(send.getAddress())) {
                                    kaboom.incrementAndGet();
                                    throw new IllegalArgumentException("Forced");
                                }

                                if ("foo@beer.org".equals(send.getAddress())) {
                                    foo.incrementAndGet();
                                } else if ("bar@beer.org".equals(send.getAddress())) {
                                    bar.incrementAndGet();
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/processor/jpa/springJpaRouteTest.xml");
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    protected void cleanupRepository() {
        jpaTemplate = (JpaTemplate)applicationContext.getBean("jpaTemplate", JpaTemplate.class);

        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus arg0) {
                List<?> list = jpaTemplate.find(SELECT_ALL_STRING);
                for (Object item : list) {
                    jpaTemplate.remove(item);
                }
                jpaTemplate.flush();
                return Boolean.TRUE;
            }
        });
    }


}
