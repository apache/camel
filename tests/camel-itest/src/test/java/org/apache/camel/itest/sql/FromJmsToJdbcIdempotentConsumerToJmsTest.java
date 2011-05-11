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
package org.apache.camel.itest.sql;

import java.io.File;
import java.net.ConnectException;
import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Jms with JDBC idempotent consumer using XA test.
 */
public class FromJmsToJdbcIdempotentConsumerToJmsTest extends CamelSpringTestSupport {

    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;
    protected IdempotentRepository repository;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/sql/FromJmsToJdbcIdempotentConsumerToJmsTest.xml");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        // delete transaction log and AMQ data
        FileUtil.deleteFile(new File("tm.out"));
        FileUtil.deleteFile(new File("tmlog0.log"));
        deleteDirectory("activemq-data");

        super.setUp();

        dataSource = context.getRegistry().lookup("myNonXADataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();

        setupRepository();
    }

    protected void setupRepository() {
        try {
            jdbcTemplate.execute("DROP TABLE CAMEL_MESSAGEPROCESSED");
        } catch (Exception e) {
            // ignore
        }
        jdbcTemplate.execute("CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR(20), messageId VARCHAR(10))");
    }

    @Test
    public void testJmsToJdbcJmsCommit() throws Exception {
        // check there are no messages in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 2000));

        // use a notify to know when the message is done
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        // use mock during testing as well
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBodyAndHeader("activemq:queue:inbox", "A", "uid", 123);

        // assert mock and wait for the message to be done
        assertMockEndpointsSatisfied();
        assertTrue("Should complete 1 message", notify.matchesMockWaitTime());

        // check that there is a message in the database and JMS queue
        assertEquals(1, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        Object out = consumer.receiveBody("activemq:queue:outbox", 3000);
        assertEquals("DONE-A", out);
    }

    @Test
    public void testJmsToJdbcJmsRollbackAtA() throws Exception {
        // check there are no messages in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 2000));

        // use a notify to know that after 1+6 (1 original + 6 redelivery) attempts from AcitveMQ
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(7).create();

        getMockEndpoint("mock:a").expectedMessageCount(7);
        // force exception to occur at mock a
        getMockEndpoint("mock:a").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new ConnectException("Forced cannot connect to database");
            }
        });
        getMockEndpoint("mock:b").expectedMessageCount(0);

        template.sendBodyAndHeader("activemq:queue:inbox", "A", "uid", 123);

        // assert mock and wait for the message to be done
        assertMockEndpointsSatisfied();
        assertTrue("Should complete 7 message", notify.matchesMockWaitTime());

        // check that there is a message in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 3000));

        // the message should have been moved to the AMQ DLQ queue
        assertEquals("A", consumer.receiveBody("activemq:queue:ActiveMQ.DLQ", 3000));
    }

    @Test
    public void testJmsToJdbcJmsRollbackAtB() throws Exception {
        // check there are no messages in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 2000));

        // use a notify to know that after 1+6 (1 original + 6 redelivery) attempts from AcitveMQ
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(7).create();

        getMockEndpoint("mock:a").expectedMessageCount(7);
        // force exception to occur at mock a
        getMockEndpoint("mock:b").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new ConnectException("Forced cannot send to AMQ queue");
            }
        });

        template.sendBodyAndHeader("activemq:queue:inbox", "B", "uid", 456);

        // assert mock and wait for the message to be done
        assertMockEndpointsSatisfied();
        assertTrue("Should complete 7 messages", notify.matchesMockWaitTime());

        // check that there is a message in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 3000));

        // the message should have been moved to the AMQ DLQ queue
        assertEquals("B", consumer.receiveBody("activemq:queue:ActiveMQ.DLQ", 3000));
    }

    @Test
    public void testFilterIdempotent() throws Exception {
        // check there are no messages in the database and JMS queue
        assertEquals(0, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals(null, consumer.receiveBody("activemq:queue:outbox", 2000));

        // use a notify to know when the message is done
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        // use mock during testing as well
        getMockEndpoint("mock:a").expectedMessageCount(3);
        // there should be 1 duplicate
        getMockEndpoint("mock:b").expectedMessageCount(2);

        template.sendBodyAndHeader("activemq:queue:inbox", "D", "uid", 111);
        template.sendBodyAndHeader("activemq:queue:inbox", "E", "uid", 222);
        template.sendBodyAndHeader("activemq:queue:inbox", "D", "uid", 111);

        // assert mock and wait for the message to be done
        assertMockEndpointsSatisfied();
        assertTrue("Should complete 3 messages", notify.matchesMockWaitTime());

        // check that there is two messages in the database and JMS queue
        assertEquals(2, jdbcTemplate.queryForInt("select count(*) from  CAMEL_MESSAGEPROCESSED"));
        assertEquals("DONE-D", consumer.receiveBody("activemq:queue:outbox", 3000));
        assertEquals("DONE-E", consumer.receiveBody("activemq:queue:outbox", 3000));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                repository = context.getRegistry().lookup("messageIdRepository", IdempotentRepository.class);

                from("activemq:queue:inbox")
                    .transacted("required")
                    .to("mock:a")
                    .idempotentConsumer(header("uid"), repository)
                    .to("mock:b")
                    .transform(simple("DONE-${body}"))
                    .to("activemq:queue:outbox");
            }
        };
    }
}
