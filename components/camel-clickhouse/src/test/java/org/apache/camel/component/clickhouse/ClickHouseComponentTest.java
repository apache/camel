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
package org.apache.camel.component.clickhouse;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseComponentTest extends CamelTestSupport {

    private static final String URL = "serverUrl=http://localhost:8123";

    @Test
    void createEndpointParsesDatabaseAndTableFromPath() {
        ClickHouseEndpoint endpoint = context.getEndpoint(
                "clickhouse://analytics.events?" + URL, ClickHouseEndpoint.class);

        assertThat(endpoint.getDatabase()).isEqualTo("analytics");
        assertThat(endpoint.getTable()).isEqualTo("events");
        assertThat(endpoint.getServerUrl()).isEqualTo("http://localhost:8123");
    }

    @Test
    void createEndpointParsesDatabaseOnlyPath() {
        ClickHouseEndpoint endpoint = context.getEndpoint(
                "clickhouse://analytics?operation=query&" + URL, ClickHouseEndpoint.class);

        assertThat(endpoint.getDatabase()).isEqualTo("analytics");
        assertThat(endpoint.getTable()).isNull();
        assertThat(endpoint.getOperation()).isEqualTo(ClickHouseOperation.QUERY);
    }

    @Test
    void createEndpointAppliesDefaults() {
        ClickHouseEndpoint endpoint = context.getEndpoint("clickhouse://db?" + URL, ClickHouseEndpoint.class);

        assertThat(endpoint.getOperation()).isEqualTo(ClickHouseOperation.INSERT);
        assertThat(endpoint.getFormat()).isEqualTo("JSONEachRow");
        assertThat(endpoint.getUsername()).isEqualTo("default");
        assertThat(endpoint.isAsyncInsert()).isFalse();
        assertThat(endpoint.isWaitForAsyncInsert()).isTrue();
        assertThat(endpoint.isCompression()).isFalse();
    }

    @Test
    void createEndpointBindsAllOptions() {
        ClickHouseEndpoint endpoint = context.getEndpoint(
                "clickhouse://metrics.samples?operation=insert&format=RowBinary&asyncInsert=true"
                                                          + "&waitForAsyncInsert=false&compression=true&batchSize=5000"
                                                          + "&username=admin&password=secret&ssl=true&" + URL,
                ClickHouseEndpoint.class);

        assertThat(endpoint.getDatabase()).isEqualTo("metrics");
        assertThat(endpoint.getTable()).isEqualTo("samples");
        assertThat(endpoint.getFormat()).isEqualTo("RowBinary");
        assertThat(endpoint.isAsyncInsert()).isTrue();
        assertThat(endpoint.isWaitForAsyncInsert()).isFalse();
        assertThat(endpoint.isCompression()).isTrue();
        assertThat(endpoint.getBatchSize()).isEqualTo(5000);
        assertThat(endpoint.getUsername()).isEqualTo("admin");
        assertThat(endpoint.getPassword()).isEqualTo("secret");
        assertThat(endpoint.isSsl()).isTrue();
    }

    @Test
    void createConsumerIsNotSupported() {
        ClickHouseEndpoint endpoint = context.getEndpoint("clickhouse://db?" + URL, ClickHouseEndpoint.class);

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        })).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void startWithoutClientOrServerUrlFails() {
        assertThatThrownBy(() -> context.getEndpoint("clickhouse://db?operation=ping", ClickHouseEndpoint.class))
                .hasMessageContaining("serverUrl");
    }

    @Test
    void emptyPathIsRejected() {
        assertThatThrownBy(() -> context.getEndpoint("clickhouse:?" + URL, ClickHouseEndpoint.class))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("database");
    }
}
