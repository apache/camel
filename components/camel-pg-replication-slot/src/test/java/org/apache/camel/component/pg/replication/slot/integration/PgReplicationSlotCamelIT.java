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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PgReplicationSlotCamelIT extends PgReplicationITSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockEndpoint;
    private Connection connection;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        String url = String.format("jdbc:postgresql://%s/camel", service.getServiceAddress());
        Properties props = new Properties();
        props.setProperty("user", service.userName());
        props.setProperty("password", service.password());

        this.connection = DriverManager.getConnection(url, props);
        try (Statement statement = this.connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS camel_test_table(id int);");
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        this.connection.close();
        super.tearDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                String uriFormat
                        = "pg-replication-slot://{{postgres.service.address}}/camel/camel_test_slot:test_decoding?"
                          + "user={{postgres.user.name}}&password={{postgres.user.password}}"
                          + "&slotOptions.skip-empty-xacts=true&slotOptions.include-xids=false";

                from(uriFormat).to(mockEndpoint);
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
