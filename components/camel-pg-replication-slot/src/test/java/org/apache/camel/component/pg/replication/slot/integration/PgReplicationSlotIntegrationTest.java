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
package org.apache.camel.component.pg.replication.slot.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.PropertyInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PgReplicationSlotIntegrationTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockEndpoint;

    @EndpointInject("pg-replication-slot://{{host}}:{{port}}/{{database}}/camel_test_slot:test_decoding?"
            + "user={{username}}&password={{password}}&slotOptions.skip-empty-xacts=true&slotOptions.include-xids=false")
    private Endpoint pgReplicationSlotEndpoint;

    @PropertyInject("host")
    private String host;

    @PropertyInject("port")
    private String port;

    @PropertyInject("database")
    private String database;

    @PropertyInject("username")
    private String username;

    @PropertyInject("password")
    private String password;

    private Connection connection;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        String url = String.format("jdbc:postgresql://%s:%s/%s", this.host, this.port, this.database);
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);

        this.connection = DriverManager.getConnection(url, props);
        try (Statement statement = this.connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS camel_test_table(id int);");
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        try (Statement statement = this.connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS camel_test_table;");
            statement.execute("SELECT pg_drop_replication_slot('camel_test_slot');");
        }
        this.connection.close();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getPropertiesComponent().setLocation("classpath:/test-options.properties");
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(pgReplicationSlotEndpoint).to(mockEndpoint);
            }
        };
    }

    @Test
    public void canReceiveFromSlot() throws InterruptedException, SQLException {
        mockEndpoint.expectedMessageCount(1);

        // test_decoding plugin writes each change in a separate message. Some other plugins can have different behaviour,
        // wal2json default behaviour is to write the whole transaction in one message.
        mockEndpoint.expectedBodiesReceived("BEGIN", "table public.camel_test_table: INSERT: id[integer]:1984", "COMMIT",
                "BEGIN", "table public.camel_test_table: INSERT: id[integer]:1998", "COMMIT");

        try (Statement statement = this.connection.createStatement()) {
            statement.execute("INSERT INTO camel_test_table(id) VALUES(1984);");
            statement.execute("INSERT INTO camel_test_table(id) VALUES(1998);");
        }

        mockEndpoint.assertIsSatisfied(5000);
    }
}
