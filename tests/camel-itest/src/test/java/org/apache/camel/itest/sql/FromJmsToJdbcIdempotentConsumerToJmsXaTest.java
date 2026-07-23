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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;

/**
 * JMS with JDBC idempotent consumer test using XA.
 */
@Timeout(60)
public class FromJmsToJdbcIdempotentConsumerToJmsXaTest extends FromJmsToJdbcIdempotentConsumerToJmsTest {

    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    @AfterEach
    public void shutdownDatabaseAndCleanup() {
        // Drop all objects first to clear all data, then shutdown and delete files
        try {
            DataSource dataSource = context.getRegistry().lookupByNameAndType(getDatasourceName(), DataSource.class);
            try (Connection conn = dataSource.getConnection()) {
                // Drop all tables and data
                conn.createStatement().execute("DROP ALL OBJECTS");
                // Then shutdown database to release file locks
                conn.createStatement().execute("SHUTDOWN");
            }
        } catch (SQLException e) {
            // Ignore - database might already be closed or not exist yet
        }

        // Delete the database files after shutdown
        deleteDirectory("target/testdb");
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
