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
package org.apache.camel.component.duckdb.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.duckdb.DuckDbConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.duckdb.services.DuckDbEmbeddedService;
import org.apache.camel.test.infra.duckdb.services.DuckDbService;
import org.apache.camel.test.infra.duckdb.services.DuckDbServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDbProducerIT extends CamelTestSupport {

    @RegisterExtension
    static DuckDbService service = DuckDbServiceFactory.createService();

    @BeforeEach
    void setUpDatabase() throws Exception {
        if (service instanceof DuckDbEmbeddedService embedded) {
            embedded.executeSql("CREATE TABLE IF NOT EXISTS camel_events (id INTEGER, name VARCHAR)");
            embedded.executeSql("DELETE FROM camel_events");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String endpointBase = "duckdb::memory:?jdbcUrl=" + URLEncoder.encode(service.getJdbcUrl(), StandardCharsets.UTF_8);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert")
                        .to(endpointBase + "&operation=insert&table=camel_events")
                        .to("mock:result");
                from("direct:query")
                        .to(endpointBase + "&operation=query")
                        .to("mock:result");
                from("direct:execute")
                        .to(endpointBase + "&operation=execute")
                        .to("mock:result");
                from("direct:ping")
                        .to(endpointBase + "&operation=ping")
                        .to("mock:result");
            }
        };
    }

    @Test
    void insertAndQueryRoundTrip() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:insert", List.of(Map.of("id", 1, "name", "alice"), Map.of("id", 2, "name", "bob")));

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = template.requestBody("direct:query", "SELECT id, name FROM camel_events ORDER BY id",
                List.class);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("name")).isEqualTo("alice");
    }

    @Test
    void executeDdlViaBody() {
        template.sendBody("direct:execute", "CREATE TABLE IF NOT EXISTS ping_check (id INTEGER)");
        assertThat(template.requestBody("direct:ping", null, Boolean.class)).isTrue();
    }

    @Test
    void pingSetsHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(DuckDbConstants.PING_OK, true);

        template.sendBody("direct:ping", null);

        MockEndpoint.assertIsSatisfied(context);
    }
}
