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
package org.apache.camel.processor.idempotent.jdbc;

import java.util.List;

import javax.sql.DataSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcMessageIdRepositoryTest extends CamelSpringTestSupport {

    protected static final String SELECT_ALL_STRING = "SELECT messageId FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";
    protected static final String CLEAR_STRING = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";
    protected static final String PROCESSOR_NAME = "myProcessorName";

    protected JdbcTemplate jdbcTemplate;

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject("mock:error")
    protected MockEndpoint errorEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        DataSource dataSource = context.getRegistry().lookupByNameAndType("dataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        resultEndpoint.expectedBodiesReceived("one", "two", "three");
        errorEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");

        MockEndpoint.assertIsSatisfied(context);

        // all 3 messages should be in jdbc repo
        List<String> receivedMessageIds = jdbcTemplate.queryForList(SELECT_ALL_STRING, String.class, PROCESSOR_NAME);

        assertEquals(3, receivedMessageIds.size());
        assertTrue(receivedMessageIds.contains("1"));
        assertTrue(receivedMessageIds.contains("2"));
        assertTrue(receivedMessageIds.contains("3"));
    }

    @Test
    public void testFailedExchangesNotAdded() throws Exception {
        RouteBuilder interceptor = new RouteBuilder(context) {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:result")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String id = exchange.getIn().getHeader("messageId", String.class);
                                if (id.equals("2")) {
                                    throw new IllegalArgumentException("Damn I cannot handle id 2");
                                }
                            }
                        });
            }
        };
        RouteDefinition routeDefinition = context.getRouteDefinition("JdbcMessageIdRepositoryTest");
        AdviceWith.adviceWith(routeDefinition, context, interceptor);

        // we send in 2 messages with id 2 that fails
        errorEndpoint.expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");

        MockEndpoint.assertIsSatisfied(context);

        jdbcTemplate.update(CLEAR_STRING, PROCESSOR_NAME);

        // only message 1 and 3 should be in jdbc repo
        List<String> receivedMessageIds = jdbcTemplate.queryForList(SELECT_ALL_STRING, String.class, PROCESSOR_NAME);

        assertEquals(0, receivedMessageIds.size());
        assertFalse(receivedMessageIds.contains("1"), "Should not contain message 1");
        assertFalse(receivedMessageIds.contains("3"), "Should not contain message 3");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/processor/idempotent/jdbc/spring.xml");
    }
}
