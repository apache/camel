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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliConnectorProtocolTest {

    @Test
    void parsesVersionOneHelloFrame() {
        var frame = CliConnectorProtocol.parseFrame("{\"v\":1,\"type\":\"hello\",\"name\":\"orders\"}");
        assertThat(frame.getString("type")).isEqualTo("hello");
    }

    @Test
    void rejectsUnknownProtocolVersions() {
        assertThatThrownBy(() -> CliConnectorProtocol.parseFrame("{\"v\":2,\"type\":\"hello\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported protocol version");
    }

    @Test
    void extractsAndValidatesExecutionId() {
        assertThat(CliConnectorProtocol.executionId("tenant=dev&executionId=orders-run-1"))
                .isEqualTo("orders-run-1");
        assertThat(CliConnectorProtocol.executionId("executionId=orders%3Ablue"))
                .isEqualTo("orders:blue");
    }

    @Test
    void extractsExecutionIdFromRawRequestUri() {
        assertThat(CliConnectorProtocol.executionId(
                CliConnectorProtocol.queryFromUri("/connect?environment=dev&executionId=orders-1")))
                .isEqualTo("orders-1");
        assertThat(CliConnectorProtocol.queryFromUri("/connect")).isNull();
    }

    @Test
    void rejectsMissingDuplicateAndUnsafeExecutionIds() {
        assertThatThrownBy(() -> CliConnectorProtocol.executionId((String) null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("executionId");
        assertThatThrownBy(() -> CliConnectorProtocol.executionId(""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("executionId");
        assertThatThrownBy(() -> CliConnectorProtocol.executionId(
                CliConnectorProtocol.queryFromUri("/connect")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("executionId");
        assertThatThrownBy(() -> CliConnectorProtocol.executionId("tenant=dev"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CliConnectorProtocol.executionId("executionId=a&executionId=b"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only once");
        assertThatThrownBy(() -> CliConnectorProtocol.executionId("executionId=../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
