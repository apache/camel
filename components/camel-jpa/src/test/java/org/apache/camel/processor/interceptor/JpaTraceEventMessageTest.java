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
package org.apache.camel.processor.interceptor;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.jpa.JpaTraceEventMessage;
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
public class JpaTraceEventMessageTest extends CamelTestSupport {
    protected static final String SELECT_ALL_STRING = "select x from " + JpaTraceEventMessage.class.getName() + " x";

    protected ApplicationContext applicationContext;
    protected JpaTemplate jpaTemplate;

    @Test
    public void testSendTraceMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        assertEntityInDB();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/processor/interceptor/springJpaTraveEvent.xml");
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("jpa://" + JpaTraceEventMessage.class.getName() + "?persistenceUnit=trace");
                tracer.setUseJpa(true);
                getContext().addInterceptStrategy(tracer);

                from("direct:start").to("mock:result");
            }
        };
    }

    private void assertEntityInDB() throws Exception {
        jpaTemplate = (JpaTemplate)applicationContext.getBean("jpaTemplate", JpaTemplate.class);

        List list = jpaTemplate.find(SELECT_ALL_STRING);
        assertEquals(1, list.size());
        
        JpaTraceEventMessage db = (JpaTraceEventMessage) list.get(0);
        assertNotNull(db.getId());
        assertEquals("direct://start", db.getFromEndpointUri());
        assertEquals("mock://result", db.getToNode());
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