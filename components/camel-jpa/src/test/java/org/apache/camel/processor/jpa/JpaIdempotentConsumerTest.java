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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.IdempotentConsumerTest;
import org.apache.camel.processor.idempotent.jpa.MessageProcessed;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringRouteBuilder;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.processor.idempotent.jpa.JpaMessageIdRepository.jpaMessageIdRepository;

/**
 * @version $Revision$
 */
public class JpaIdempotentConsumerTest extends IdempotentConsumerTest {
    protected static final String SELECT_ALL_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1";
    protected static final String PROCESSOR_NAME = "myProcessorName";

    protected ApplicationContext applicationContext;
    protected JpaTemplate jpaTemplate;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/processor/jpa/spring.xml");
        cleanupRepository();
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // START SNIPPET: idempotent
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start").idempotentConsumer(
                        header("messageId"),
                        jpaMessageIdRepository(bean(JpaTemplate.class), PROCESSOR_NAME)
                ).to("mock:result");
            }
        };
        // END SNIPPET: idempotent
    }

    protected void cleanupRepository() {
        jpaTemplate = (JpaTemplate)applicationContext.getBean("jpaTemplate", JpaTemplate.class);

        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(SELECT_ALL_STRING, PROCESSOR_NAME);
                for (Object item : list) {
                    jpaTemplate.remove(item);
                }
                jpaTemplate.flush();
                return Boolean.TRUE;
            }
        });
    }
}
