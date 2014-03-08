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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.jpa.MessageProcessed;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import static org.apache.camel.processor.idempotent.jpa.JpaMessageIdRepository.jpaMessageIdRepository;

/**
 * @version 
 */
public class JpaIdempotentConsumerTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1";
    protected static final String PROCESSOR_NAME = "myProcessorName";

    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void cleanupRepository() {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();
                Query query = entityManager.createQuery(SELECT_ALL_STRING);
                query.setParameter(1, PROCESSOR_NAME);
                List<?> list = query.getResultList();
                for (Object item : list) {
                    entityManager.remove(item);
                }
                entityManager.flush();
                return Boolean.TRUE;
            }
        });
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        context.addRoutes(new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: idempotent
                from("direct:start").idempotentConsumer(
                        header("messageId"),
                        jpaMessageIdRepository(lookup(EntityManagerFactory.class), PROCESSOR_NAME)
                ).to("mock:result");
                // END SNIPPET: idempotent
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // all 3 messages should be in jpa repo
        Set<String> ids = new HashSet<String>();
        Query query = entityManager.createQuery(SELECT_ALL_STRING);
        query.setParameter(1, PROCESSOR_NAME);
        List<MessageProcessed> list = query.getResultList();
        for (MessageProcessed item : list) {
            ids.add(item.getMessageId());
        }

        assertEquals(3, ids.size());
        assertTrue("Should contain message 1", ids.contains("1"));
        assertTrue("Should contain message 2", ids.contains("2"));
        assertTrue("Should contain message 3", ids.contains("3"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailedExchangesNotAdded() throws Exception {
        context.addRoutes(new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(0).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").idempotentConsumer(
                        header("messageId"),
                        jpaMessageIdRepository(lookup(EntityManagerFactory.class), PROCESSOR_NAME)
                ).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        if (id.equals("2")) {
                            throw new IllegalArgumentException("Damn I cannot handle id 2");
                        }
                    }
                }).to("mock:result");
            }
        });
        context.start();

        // we send in 2 messages with id 2 that fails
        getMockEndpoint("mock:error").expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // only message 1 and 3 should be in jpa repo
        Set<String> ids = new HashSet<String>();
        Query query = entityManager.createQuery(SELECT_ALL_STRING);
        query.setParameter(1, PROCESSOR_NAME);
        List<MessageProcessed> list = query.getResultList();
        for (MessageProcessed item : list) {
            ids.add(item.getMessageId());
        }

        assertEquals(2, ids.size());
        assertTrue("Should contain message 1", ids.contains("1"));
        assertTrue("Should contain message 3", ids.contains("3"));
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/spring.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }

}
