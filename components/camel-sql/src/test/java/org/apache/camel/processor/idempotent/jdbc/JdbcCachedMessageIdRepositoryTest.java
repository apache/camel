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

import java.sql.Timestamp;

import javax.sql.DataSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcCachedMessageIdRepositoryTest extends CamelSpringTestSupport {

    protected static final String INSERT_STRING
            = "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)";
    protected static final String PROCESSOR_NAME = "myProcessorName";

    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;
    protected JdbcCachedMessageIdRepository repository;

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject("mock:error")
    protected MockEndpoint errorEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        dataSource = context.getRegistry().lookupByNameAndType("dataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(INSERT_STRING, PROCESSOR_NAME, "1", new Timestamp(System.currentTimeMillis()));
        jdbcTemplate.update(INSERT_STRING, PROCESSOR_NAME, "2", new Timestamp(System.currentTimeMillis()));
        repository = context.getRegistry().lookupByNameAndType("messageIdRepository", JdbcCachedMessageIdRepository.class);
        repository.reload();
    }

    @Test
    public void testCacheHit() throws Exception {
        resultEndpoint.expectedBodiesReceived("three");
        errorEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");
        template.sendBodyAndHeader("direct:start", "one", "messageId", "1");
        template.sendBodyAndHeader("direct:start", "two", "messageId", "2");
        template.sendBodyAndHeader("direct:start", "three", "messageId", "3");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(5, repository.getHitCount());
        assertEquals(1, repository.getMissCount());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/processor/idempotent/jdbc/cached-spring.xml");
    }
}
