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
 * camel cmd browse: the endpoints of an integration that queue messages, and the messages queued in them.
 */
@ExtendWith(MockitoExtension.class)
class CamelBrowseActionViewsTest extends ActionCommandTestSupport {

    @Test
    void theEndpointsAndTheirSize() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), endpoints())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("filter")).isEqualTo("*");
        assertThat(action.getInteger("limit")).isEqualTo(100);
        String out = printer.getOutput();
        // the queue holds at least the limit
        assertThat(out).contains("seda://orders", "seda://payments", "100+");
        assertThat(out.indexOf("seda://orders")).isLessThan(out.indexOf("seda://payments"));
    }

    @Test
    void sortBySize() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--sort=-size"), endpoints())).isZero();

        String out = printer.getOutput();
        assertThat(out.indexOf("seda://payments")).isLessThan(out.indexOf("seda://orders"));
    }

    @Test
    void maskHidesTheSecretsOfTheEndpoints() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--mask", "--wide-uri"), endpoints())).isZero();

        assertThat(printer.getOutput()).contains("seda://payments").doesNotContain("secret");
    }

    @Test
    void dumpTheMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--endpoint=seda:orders", "--dump", "--tail=5", "--body-max-chars=200"),
                endpoints())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("filter")).isEqualTo("seda:orders");
        assertThat(action.getBoolean("dump")).isTrue();
        assertThat(action.getInteger("tail")).isEqualTo(5);
        assertThat(action.getInteger("bodyMaxChars")).isEqualTo(200);
        assertThat(printer.getOutput()).contains("Browse Message: (1/2)", "Browse Message: (2/2)", "order-1", "orderId");
    }

    @Test
    void dumpOnlyTheBodies() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--dump", "--only-body"), endpoints())).isZero();

        assertThat(printer.getOutput()).contains("order-1").doesNotContain("orderId");
    }

    @Test
    void theEndpointsAsJson() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--json", "--dump"), endpoints())).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(rows).hasSize(2);
        JsonObject orders = rows.getMap(0);
        assertThat(orders.getString("endpointUri")).isEqualTo("seda://orders");
        assertThat(orders.getInteger("queueSize")).isEqualTo(2);
        assertThat((List<?>) orders.get("messages")).hasSize(2);
    }

    @Test
    void anEndpointThatCannotBeBrowsedIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelBrowseAction command = command("--endpoint=kafka:orders");

        assertThatThrownBy(() -> callWithResponse(command, new JsonObject(Map.of("browse", new JsonArray()))))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No browsable endpoint matches: kafka:orders");
    }

    @Test
    void unknownSortIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelBrowseAction command = command("--sort=age");

        assertThatThrownBy(() -> callWithResponse(command, endpoints()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE));
    }

    private CamelBrowseAction command(String... args) {
        CamelBrowseAction command = new CamelBrowseAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("myApp", "--logging-color=false"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // the answer of the browse action: orders with two messages, payments at its limit
    private static JsonObject endpoints() {
        JsonObject orders = new JsonObject();
        orders.put("endpointUri", "seda://orders");
        orders.put("queueSize", 2);
        orders.put("limit", 100);
        orders.put("position", 0);
        orders.put("firstTimestamp", System.currentTimeMillis() - 60000);
        orders.put("lastTimestamp", System.currentTimeMillis() - 1000);
        orders.put("messages", new JsonArray(List.of(message("A-1", "order-1"), message("A-2", "order-2"))));
        JsonObject payments = new JsonObject();
        payments.put("endpointUri", "seda://payments?password=secret");
        payments.put("queueSize", 100);
        payments.put("limit", 100);
        payments.put("position", 0);
        return new JsonObject(Map.of("browse", new JsonArray(List.of(orders, payments))));
    }

    private static JsonObject message(String exchangeId, String body) {
        JsonObject header = new JsonObject(Map.of("key", "orderId", "type", "java.lang.String", "value", "42"));
        JsonObject value = new JsonObject(Map.of("type", "java.lang.String", "value", body));
        JsonObject message = new JsonObject();
        message.put("messageType", "DefaultMessage");
        message.put("headers", new JsonArray(List.of(header)));
        message.put("body", value);
        return new JsonObject(Map.of("exchangeId", exchangeId, "message", message));
    }
}
