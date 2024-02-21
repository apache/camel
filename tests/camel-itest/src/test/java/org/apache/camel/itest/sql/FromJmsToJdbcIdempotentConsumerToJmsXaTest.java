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

import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JMS with JDBC idempotent consumer test using XA.
 */
public class FromJmsToJdbcIdempotentConsumerToJmsXaTest extends FromJmsToJdbcIdempotentConsumerToJmsTest {

    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/testdb");

        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        // shutdown the embedded Derby database so that the next test becomes a clean initial state
        try {
            DriverManager.getConnection("jdbc:derby:target/testdb;shutdown=true");
            fail("Should have thrown exception");
        } catch (SQLException e) {
            // a successful shutdown always results in an SQLException to indicate that Derby has shut down and that there is no other exception.
            assertEquals("Database 'target/testdb' shutdown.", e.getMessage());
        }
    }

    @Override
    protected String getDatasourceName() {
        return "myXADataSource";
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/sql/FromJmsToJdbcIdempotentConsumerToJmsXaTest.xml");
    }
}
