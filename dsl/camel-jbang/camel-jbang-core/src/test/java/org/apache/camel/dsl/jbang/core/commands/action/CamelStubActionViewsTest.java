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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd stub: the stub queues of an integration (camel run --stub), and the messages in them.
 */
@ExtendWith(MockitoExtension.class)
class CamelStubActionViewsTest extends ActionCommandTestSupport {

    @Test
    void theQueues() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), queues())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("stub");
        String out = printer.getOutput();
        assertThat(out).contains("kafka:orders", "jms:payments");
        assertThat(out.indexOf("jms:payments")).isLessThan(out.indexOf("kafka:orders"));
    }

    @Test
    void sortByTotal() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--sort=-total"), queues())).isZero();

        String out = printer.getOutput();
        assertThat(out.indexOf("kafka:orders")).isLessThan(out.indexOf("jms:payments"));
    }

    @Test
    void filterTheQueues() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--filter=kafka*"), queues())).isZero();

        assertThat(printer.getOutput()).contains("kafka:orders").doesNotContain("jms:payments");
    }

    @Test
    void browseTheMessagesLatestFirst() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--browse", "--filter=kafka*"), queues())).isZero();

        assertThat(readActionFile(TEST_PID).getBoolean("browse")).isTrue();
        String out = printer.getOutput();
        assertThat(out).contains("order-1", "order-2", "orderId");
        assertThat(out.indexOf("order-2")).isLessThan(out.indexOf("order-1"));
    }

    @Test
    void browseTheMatchingMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--browse", "--filter=kafka*", "--grep=order-2", "--show-headers=false"),
                queues())).isZero();

        String out = printer.getOutput().replaceAll("\u001B\\[[;\\d]*m", "");
        assertThat(out).contains("order-2").doesNotContain("order-1", "orderId");
    }

    @Test
    void theQueuesAsJson() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--json"), queues())).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(rows).hasSize(2);
        JsonObject first = rows.getMap(0);
        assertThat(first.getString("queue")).isEqualTo("jms:payments");
        assertThat(first.getInteger("size")).isZero();
    }

    @Test
    void unknownSortIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelStubAction command = command("--sort=size");

        assertThatThrownBy(() -> callWithResponse(command, queues()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE));
    }

    private CamelStubAction command(String... args) {
        CamelStubAction command = new CamelStubAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("myApp", "--logging-color=false"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // the answer of the stub action: kafka:orders with two messages, an empty jms:payments
    private static JsonObject queues() {
        JsonObject orders = new JsonObject();
        orders.put("name", "kafka:orders");
        orders.put("max", 1000);
        orders.put("size", 2);
        orders.put("endpointUri", "stub://kafka:orders");
        orders.put("messages", new JsonArray(List.of(message("order-1"), message("order-2"))));
        JsonObject payments = new JsonObject();
        payments.put("name", "jms:payments");
        payments.put("max", 1000);
        payments.put("size", 0);
        payments.put("endpointUri", "stub://jms:payments");
        return new JsonObject(Map.of("queues", new JsonArray(List.of(orders, payments))));
    }

    private static JsonObject message(String body) {
        JsonObject header = new JsonObject(Map.of("key", "orderId", "type", "java.lang.String", "value", "42"));
        JsonObject message = new JsonObject();
        message.put("exchangeId", "E-" + body);
        message.put("exchangePattern", "InOnly");
        message.put("headers", new JsonArray(List.of(header)));
        message.put("body", new JsonObject(Map.of("type", "java.lang.String", "value", body)));
        return new JsonObject(Map.of("message", message));
    }
}
