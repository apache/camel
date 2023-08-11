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
package org.apache.camel.itest.sql;

import java.net.ConnectException;

import javax.sql.DataSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JMS with JDBC idempotent consumer test.
 */
public class FromJmsToJdbcIdempotentConsumerToJmsTest extends CamelSpringTestSupport {

    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private JdbcTemplate jdbcTemplate;

    @EndpointInject("mock:a")
    private MockEndpoint mockA;

    @EndpointInject("mock:b")
    private MockEndpoint mockB;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/sql/FromJmsToJdbcIdempotentConsumerToJmsTest.xml");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        DataSource dataSource = context.getRegistry().lookupByNameAndType(getDatasourceName(), DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();
    }

    protected String getDatasourceName() {
        return "myNonXADataSource";
    }

    @Test
    void testJmsToJdbcJmsCommit() {
        checkInitialState();

        mockA.expectedMessageCount(1);
        mockB.expectedMessageCount(1);

        // use NotifyBuilder to know when the message is done
        NotifyBuilder notify
                = new NotifyBuilder(context).whenExactlyCompleted(1).whenDoneSatisfied(mockA).whenDoneSatisfied(mockB).create();

        template.sendBodyAndHeader("activemq2:queue:inbox", "A", "uid", 123);

        assertTrue(notify.matchesWaitTime(), "Should complete 1 message");

        // check that there is a message in the database and JMS queue
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        Object out = consumer.receiveBody("activemq2:queue:outbox", 3000);
        assertEquals("DONE-A", out);
    }

    @Test
    void testJmsToJdbcJmsRollbackAtA() {
        checkInitialState();

        mockA.expectedMessageCount(7);
        mockA.whenAnyExchangeReceived(exchange -> {
            throw new ConnectException("Forced cannot connect to database");
        });
        mockB.expectedMessageCount(0);

        // use NotifyBuilder to know that after 1+6 (1 original + 6 redelivery) attempts from ActiveMQ
        NotifyBuilder notify
                = new NotifyBuilder(context).whenExactlyDone(7).whenDoneSatisfied(mockA).whenDoneSatisfied(mockB).create();

        template.sendBodyAndHeader("activemq2:queue:inbox", "A", "uid", 123);

        assertTrue(notify.matchesWaitTime(), "Should complete 7 messages");

        // Start by checking the DLQ queue to prevent a mix-up between client and server resources being part of the same transaction

        // the message should have been moved to the AMQ DLQ queue
        assertEquals("A", consumer.receiveBody("activemq2:queue:DLQ", 3000));

        // check that there is no message in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        assertNull(consumer.receiveBody("activemq2:queue:outbox", 100));
    }

    @Test
    void testJmsToJdbcJmsRollbackAtB() {
        checkInitialState();

        mockA.expectedMessageCount(7);
        mockB.expectedMessageCount(7);
        mockB.whenAnyExchangeReceived(exchange -> {
            throw new ConnectException("Forced cannot send to AMQ queue");
        });

        // use NotifyBuilder to know that after 1+6 (1 original + 6 redelivery) attempts from ActiveMQ
        NotifyBuilder notify
                = new NotifyBuilder(context).whenExactlyDone(7).whenDoneSatisfied(mockA).whenDoneSatisfied(mockB).create();

        template.sendBodyAndHeader("activemq2:queue:inbox", "B", "uid", 456);

        assertTrue(notify.matchesWaitTime(), "Should complete 7 messages");

        // Start by checking the DLQ queue to prevent a mix-up between client and server resources being part of the same transaction

        // the message should have been moved to the AMQ DLQ queue
        assertEquals("B", consumer.receiveBody("activemq2:queue:DLQ", 3000));

        // check that there is no message in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        assertNull(consumer.receiveBody("activemq2:queue:outbox", 100));
    }

    @Test
    void testFilterIdempotent() {
        checkInitialState();

        mockA.expectedMessageCount(3);
        mockB.expectedMessageCount(2);

        // use NotifyBuilder to know when the message is done
        NotifyBuilder notify
                = new NotifyBuilder(context).whenExactlyDone(3).whenDoneSatisfied(mockA).whenDoneSatisfied(mockB).create();

        template.sendBodyAndHeader("activemq2:queue:inbox", "D", "uid", 111);
        template.sendBodyAndHeader("activemq2:queue:inbox", "E", "uid", 222);
        template.sendBodyAndHeader("activemq2:queue:inbox", "D", "uid", 111);

        assertTrue(notify.matchesWaitTime(), "Should complete 3 messages");

        // check that there is two messages in the database and JMS queue
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        assertEquals("DONE-D", consumer.receiveBody("activemq2:queue:outbox", 3000));
        assertEquals("DONE-E", consumer.receiveBody("activemq2:queue:outbox", 3000));
    }

    @Test
    void testRetryAfterException() {
        checkInitialState();

        mockA.expectedMessageCount(4);
        mockB.expectedMessageCount(4);
        mockB.whenAnyExchangeReceived(new Processor() {
            private boolean alreadyErrorThrown;

            @Override
            public void process(Exchange exchange) throws Exception {
                if (!alreadyErrorThrown) {
                    alreadyErrorThrown = true;
                    throw new ConnectException("Forced cannot send to AMQ queue");
                } else {
                    logger.info("Now successfully recovered from the error and can connect to AMQ queue");
                }
            }
        });

        // use NotifyBuilder to know when the message is done
        NotifyBuilder notify
                = new NotifyBuilder(context).whenExactlyDone(4).whenDoneSatisfied(mockA).whenDoneSatisfied(mockB).create();

        template.sendBodyAndHeader("activemq2:queue:inbox", "D", "uid", 111);
        template.sendBodyAndHeader("activemq2:queue:inbox", "E", "uid", 222);
        template.sendBodyAndHeader("activemq2:queue:inbox", "F", "uid", 333);

        assertTrue(notify.matchesWaitTime(), "Should complete 4 messages");

        // check that there is three messages in the database and JMS queue
        assertEquals(3, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        assertEquals("DONE-D", consumer.receiveBody("activemq2:queue:outbox", 3000));
        assertEquals("DONE-E", consumer.receiveBody("activemq2:queue:outbox", 3000));
        assertEquals("DONE-F", consumer.receiveBody("activemq2:queue:outbox", 3000));
    }

    protected void checkInitialState() {
        // check there are no messages in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from CAMEL_MESSAGEPROCESSED", int.class));
        assertNull(consumer.receiveBody("activemq2:queue:outbox", 100));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq2:queue:inbox")
                        .transacted("required")
                        .to(mockA)
                        .idempotentConsumer(header("uid"))
                        .idempotentRepository("messageIdRepository")
                        .to(mockB)
                        .transform(simple("DONE-${body}"))
                        .to("activemq2:queue:outbox");
            }
        };
    }
}
