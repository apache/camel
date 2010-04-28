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

import org.apache.camel.CamelContext;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
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
 * Unit test for shutdown.
 *
 * @version $Revision$
 */
public class JpaShutdownCompleteAllTasksTest extends CamelTestSupport {

    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    protected ApplicationContext applicationContext;
    protected JpaTemplate jpaTemplate;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("a@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("b@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("c@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("d@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("e@beer.org"));
    }

    @Test
    public void testCompleteAllTasks() throws Exception {
        // give it 20 seconds to shutdown
        context.getShutdownStrategy().setTimeout(20);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jpa://" + SendEmail.class.getName()).routeId("route1")
                     // let it complete all tasks
                     .shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks)
                     .to("seda:foo");

                from("seda:foo").delay(1000).to("mock:bar");
            }
        });
        context.start();

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(2);

        assertMockEndpointsSatisfied();

        // shutdown during processing
        context.stop();

        // should route all 5
        assertEquals("Should complete all messages", 5, bar.getReceivedCounter());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/processor/jpa/springJpaRouteTest.xml");
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
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