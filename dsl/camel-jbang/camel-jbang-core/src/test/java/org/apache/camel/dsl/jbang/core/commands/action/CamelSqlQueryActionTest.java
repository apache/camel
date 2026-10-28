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

import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd sql: the query it sends, and the rows or the update count the integration answers.
 */
@ExtendWith(MockitoExtension.class)
class CamelSqlQueryActionTest extends ActionCommandTestSupport {

    @Test
    void rowsAsATable() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject();
        answer.put("columns", new JsonArray(
                List.of(new JsonObject(Map.of("name", "ID")),
                        new JsonObject(Map.of("name", "ITEM")))));
        JsonObject row = new JsonObject();
        row.put("ID", 1);
        row.put("ITEM", "camel");
        JsonObject empty = new JsonObject();
        empty.put("ID", 2);
        answer.put("rows", new JsonArray(List.of(row, empty)));
        answer.put("rowCount", 2);
        answer.put("elapsed", 3);

        assertThat(callWithResponse(command("orders", "--query=select * from orders", "--max-rows=10"), answer)).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("sql-query");
        assertThat(action.getString("sql")).isEqualTo("select * from orders");
        assertThat(action.getInteger("maxRows")).isEqualTo(10);
        assertThat(printer.getLines()).containsExactly(
                "ID | ITEM",
                "---+------",
                "1  | camel",
                "2  | null",
                "",
                "2 row(s) in 3ms");
    }

    @Test
    void updateCount() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(Map.of("updateCount", 4, "elapsed", 2));

        assertThat(callWithResponse(command("orders", "--query=delete from orders"), answer)).isZero();

        assertThat(printer.getLines()).containsExactly("Update count: 4", "Elapsed: 2ms");
    }

    @Test
    void failedQueryIsAnError() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(Map.of("status", "error", "error", "Table ORDERS not found"));

        assertThatThrownBy(() -> callWithResponse(command("orders", "--query=select * from orders"), answer))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("Table ORDERS not found")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.FAILURE);
    }

    private CamelSqlQueryAction command(String... args) {
        CamelSqlQueryAction command = new CamelSqlQueryAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }
}
