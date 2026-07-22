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
package org.apache.camel.component.clickhouse.integration;

import com.clickhouse.client.api.Client;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickhouse.ClickHouseComponent;
import org.apache.camel.component.clickhouse.ClickHouseConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.clickhouse.services.ClickHouseService;
import org.apache.camel.test.infra.clickhouse.services.ClickHouseServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseProducerIT extends CamelTestSupport {

    @RegisterExtension
    static ClickHouseService service = ClickHouseServiceFactory.createService();

    private static Client client;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        client = new Client.Builder()
                .addEndpoint(service.getHttpUrl())
                .setUsername(service.getUsername())
                .setPassword(service.getPassword())
                .setDefaultDatabase(service.getDatabaseName())
                .build();

        ClickHouseComponent component = new ClickHouseComponent();
        component.setClient(client);
        context.addComponent("clickhouse", component);
        return context;
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void createTable() throws Exception {
        client.query("CREATE TABLE IF NOT EXISTS " + service.getDatabaseName()
                     + ".camel_events (id UInt32, name String) ENGINE = Memory")
                .get();
        client.query("TRUNCATE TABLE " + service.getDatabaseName() + ".camel_events").get();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert")
                        .to("clickhouse://" + service.getDatabaseName()
                            + ".camel_events?operation=insert&format=JSONEachRow")
                        .to("mock:result");
                from("direct:query")
                        .to("clickhouse://" + service.getDatabaseName() + "?operation=query&format=CSV")
                        .to("mock:result");
                from("direct:ping")
                        .to("clickhouse://" + service.getDatabaseName() + "?operation=ping")
                        .to("mock:result");
            }
        };
    }

    @Test
    void insertAndQueryRoundTrip() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String body = "{\"id\":1,\"name\":\"alice\"}\n{\"id\":2,\"name\":\"bob\"}\n";
        template.sendBody("direct:insert", body);

        MockEndpoint.assertIsSatisfied(context);

        Object written = mock.getExchanges().get(0).getMessage().getHeader(ClickHouseConstants.WRITTEN_ROWS);
        assertThat(written).isEqualTo(2L);

        String result = template.requestBody("direct:query",
                "SELECT count() FROM " + service.getDatabaseName() + ".camel_events", String.class);
        assertThat(result.trim()).isEqualTo("2");
    }

    @Test
    void pingReturnsTrue() {
        Boolean result = template.requestBody("direct:ping", null, Boolean.class);
        assertThat(result).isTrue();
    }
}
