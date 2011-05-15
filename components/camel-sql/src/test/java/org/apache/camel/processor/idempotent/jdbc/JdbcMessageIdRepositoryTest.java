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
package org.apache.camel.processor.idempotent.jdbc;

import java.util.List;

import javax.sql.DataSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;


public class JdbcMessageIdRepositoryTest extends CamelSpringTestSupport {

    protected static final String SELECT_ALL_STRING = "SELECT messageId FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";
    protected static final String DELETE_ALL_STRING = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";
    protected static final String PROCESSOR_NAME = "myProcessorName";

    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;
    
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:error")
    protected MockEndpoint errorEndpoint;
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        dataSource = context.getRegistry().lookup("dataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();
        
        setupRepository();
    }
    
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/processor/idempotent/jdbc/spring.xml");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void setupRepository() {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        
        transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                try {
                    jdbcTemplate.execute("CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR(20), messageId VARCHAR(10), createdAt timestamp)");
                } catch (DataAccessException e) {
                    // noop if table already exists 
                }
                jdbcTemplate.update(DELETE_ALL_STRING, PROCESSOR_NAME);
                return Boolean.TRUE;
            }
        });
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        resultEndpoint.expectedBodiesReceived("one", "two", "three");
        errorEndpoint.expectedMessageCount(0);

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // all 3 messages should be in jdbc repo
        List<String> receivedMessageIds = jdbcTemplate.queryForList(SELECT_ALL_STRING, String.class, PROCESSOR_NAME);

        assertEquals(3, receivedMessageIds.size());
        assertTrue(receivedMessageIds.contains("1"));
        assertTrue(receivedMessageIds.contains("2"));
        assertTrue(receivedMessageIds.contains("3"));
    }

    @Test
    public void testFailedExchangesNotAdded() throws Exception {
        RouteBuilder interceptor = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:result")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String id = exchange.getIn().getHeader("messageId", String.class);
                            if (id.equals("2")) {
                                throw new IllegalArgumentException("Damn I cannot handle id 2");
                            }
                        }
                    });
            }
        };
        RouteDefinition routeDefinition = context.getRouteDefinition("JdbcMessageIdRepositoryTest");
        routeDefinition.adviceWith(context, interceptor);

        // we send in 2 messages with id 2 that fails
        errorEndpoint.expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // only message 1 and 3 should be in jdbc repo
        List<String> receivedMessageIds = jdbcTemplate.queryForList(SELECT_ALL_STRING, String.class, PROCESSOR_NAME);

        assertEquals(2, receivedMessageIds.size());
        assertTrue("Should contain message 1", receivedMessageIds.contains("1"));
        assertTrue("Should contain message 3", receivedMessageIds.contains("3"));
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }
}