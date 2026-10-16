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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get sql-trace: the SQL statements the integrations ran, when SQL tracing is on.
 */
@ExtendWith(MockitoExtension.class)
class ListSqlTraceTest extends ProcessCommandTestSupport {

    @Test
    void statementsAsJson() throws Exception {
        JsonObject root = buildContextStatus("orders", 5);
        root.put("sqlTrace", statements(statement("SELECT * FROM orders", 12, false),
                statement("UPDATE orders SET done = true", 3, true)));
        writeStatusFile(TEST_PID, root);

        ListSqlTrace command = new ListSqlTrace(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;
        command.sort = "pid";

        assertThat(call(command)).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(rows).hasSize(2);
        JsonObject select = rows.getMap(0);
        assertThat(select.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(select.getString("query")).isEqualTo("SELECT * FROM orders");
        assertThat(select.getString("routeId")).isEqualTo("route1");
        assertThat(select.getLong("duration")).isEqualTo(12L);
        assertThat(select.getInteger("rowCount")).isEqualTo(4);
        assertThat(select.getBoolean("failed")).isFalse();
        JsonObject update = rows.getMap(1);
        assertThat(update.getBoolean("failed")).isTrue();
    }

    @Test
    void tableOfTheStatements() throws Exception {
        JsonObject root = buildContextStatus("orders", 5);
        root.put("sqlTrace", statements(statement("SELECT * FROM orders", 12, false)));
        writeStatusFile(TEST_PID, root);

        ListSqlTrace command = new ListSqlTrace(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        assertThat(call(command)).isZero();

        // a long statement is cut to fit the table
        assertThat(printer.getStdout()).contains("SELECT * FROM ord").contains("route1").contains("OK");
    }

    @Test
    void withoutSqlTracingListsNothing() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));

        ListSqlTrace command = new ListSqlTrace(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;
        command.sort = "pid";

        assertThat(call(command)).isZero();

        assertThat(printer.getStdout()).isEqualTo("[]");
    }

    private static JsonObject statements(JsonObject... statements) {
        JsonObject answer = new JsonObject();
        answer.put("statements", new JsonArray(List.of(statements)));
        return answer;
    }

    private static JsonObject statement(String query, long duration, boolean failed) {
        JsonObject statement = new JsonObject();
        statement.put("timestamp", 1000L);
        statement.put("exchangeId", "ex-1");
        statement.put("routeId", "route1");
        statement.put("query", query);
        statement.put("category", "jdbc");
        statement.put("endpoint", "sql://orders");
        statement.put("duration", duration);
        statement.put("rowCount", 4);
        statement.put("updateCount", 0);
        statement.put("failed", failed);
        return statement;
    }

    private int call(ListSqlTrace command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }
}
