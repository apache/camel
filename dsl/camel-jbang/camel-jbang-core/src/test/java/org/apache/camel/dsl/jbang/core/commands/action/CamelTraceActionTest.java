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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel trace: the status of tracing in the integrations, and starting, stopping and clearing it.
 */
@ExtendWith(MockitoExtension.class)
class CamelTraceActionTest extends ActionCommandTestSupport {

    @Test
    void statusAsJson() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));

        assertThat(callWithSingleProcess(command("--json"))).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        JsonObject row = rows.getMap(0);
        assertThat(row.getString("name")).isEqualTo("orders");
        assertThat(row.getString("status")).isEqualTo("Started");
        assertThat(row.getLong("total")).isEqualTo(7L);
        assertThat(row.getLong("queue")).isEqualTo(2L);
        assertThat(row.getString("pattern")).isEqualTo("route*");
    }

    @Test
    void statusOfStandbyTracing() throws Exception {
        writeStatusFile(TEST_PID, status(false, true));

        assertThat(callWithSingleProcess(command())).isZero();

        assertThat(printer.getStdout()).contains("Standby");
    }

    @Test
    void statusOfStoppedTracing() throws Exception {
        writeStatusFile(TEST_PID, status(false, false));

        assertThat(callWithSingleProcess(command())).isZero();

        assertThat(printer.getStdout()).contains("Stopped");
    }

    @Test
    void startAndStopSendTheAction() throws Exception {
        writeStatusFile(TEST_PID, status(false, false));

        assertThat(callWithSingleProcess(command("--action=start"))).isZero();
        assertThat(readActionFile(TEST_PID).getString("action")).isEqualTo("trace");
        assertThat(readActionFile(TEST_PID).getString("enabled")).isEqualTo("true");

        assertThat(callWithSingleProcess(command("--action=stop"))).isZero();
        assertThat(readActionFile(TEST_PID).getString("enabled")).isEqualTo("false");
    }

    @Test
    void clearEmptiesTheTraceFile() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        Path traceFile = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-trace.json");
        Files.writeString(traceFile, "{\"traces\":[{\"uid\":1}]}");

        assertThat(callWithSingleProcess(command("--action=clear"))).isZero();

        assertThat(Files.readString(traceFile)).isEqualTo("{}");
    }

    private CamelTraceAction command(String... args) {
        CamelTraceAction command = new CamelTraceAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    private static JsonObject status(boolean enabled, boolean standby) {
        JsonObject trace = new JsonObject();
        trace.put("enabled", enabled);
        trace.put("standby", standby);
        trace.put("counter", 7);
        trace.put("queueSize", 2);
        trace.put("traceFilter", "*");
        trace.put("tracePattern", "route*");
        JsonObject context = new JsonObject();
        context.put("name", "orders");
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("trace", trace);
        return root;
    }
}
