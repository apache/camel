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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25362: Shift+F8 on the SQL Trace tab: fix with AI a statement that failed, explain with AI a slow one.
 */
class SqlTraceAiPromptTest {

    private final List<String> asked = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "orders-app";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.askAiCallback = new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path file, int line, String problem, String lineText) {
                asked.add("fix: " + problem);
            }

            @Override
            public void ask(String question) {
                asked.add(question);
            }
        };
        ctx.notificationCallback = (message, error) -> notified.add(message);
    }

    @Test
    void aFailedStatementIsAskedWithTheErrorOfItsExchange() {
        SqlTraceInfo si = statement("INSERT INTO ordrs (id) VALUES (:#id)", 3, true);
        si.exchangeId = "EX-2";
        si.endpoint = "sql:INSERT INTO ordrs (id) VALUES (:#id)?dataSource=#ds&password=secret";
        ErrorInfo other = error("EX-1", "java.lang.IllegalStateException", "other");
        other.timestamp = 9000;
        ErrorInfo mine = error("EX-2", "org.h2.jdbc.JdbcSQLSyntaxErrorException", "Table \"ORDRS\" not found");
        info.errors = new ArrayList<>(List.of(other, mine));

        String question = SqlTraceAiPrompt.of(info, si);

        assertThat(question)
                .startsWith("A SQL statement failed in the running integration orders-app.\n"
                            + "The statement (INSERT):\n```sql\nINSERT INTO ordrs (id) VALUES (:#id)\n```\n")
                // the endpoint is masked before it goes to the AI
                .contains("password=xxxxxx").doesNotContain("secret")
                .contains("It ran in route orders at step insert-order, orders.camel.yaml:12.\n")
                .contains("The error: JdbcSQLSyntaxErrorException: Table \"ORDRS\" not found\n")
                .endsWith("say what to change there, and make no changes to the files.");
    }

    @Test
    void aSlowStatementIsAskedWithHowOftenTheSameSqlRan() {
        SqlTraceInfo slow = statement("SELECT * FROM orders WHERE status = 'NEW'", 450, false);
        slow.rowCount = 5000;
        info.sqlTraceStatements.add(slow);
        info.sqlTraceStatements.add(statement("SELECT * FROM orders WHERE status = 'NEW'", 150, false));
        info.sqlTraceStatements.add(statement("SELECT 1", 1, false));

        String question = SqlTraceAiPrompt.of(info, slow);

        assertThat(question)
                .startsWith("A SQL statement is slow in the running integration orders-app: it took 450 ms.\n")
                .contains("The same SQL ran 2 times among the traced statements: mean 300 ms, slowest 450 ms.\n")
                .contains("It returned 5000 rows.\n")
                .contains("tui_execute_sql")
                .endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void shiftF8OnlyOnAStatementThatFailedOrWasSlow() {
        SqlTraceInfo fast = statement("SELECT 1", 1, false);
        fast.timestamp = 2000;
        SqlTraceInfo failed = statement("SELECT * FROM nope", 2, true);
        failed.timestamp = 1000;
        info.sqlTraceStatements.add(fast);
        info.sqlTraceStatements.add(failed);
        SqlTraceTab tab = new SqlTraceTab(ctx);
        tab.onTabSelected();
        SqlTraceInfo first = tab.selectedStatement(info);
        int fastRow = first == fast ? 0 : 1;

        tab.tableState.select(fastRow);
        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).singleElement().asString().startsWith("This statement did not fail");

        tab.tableState.select(1 - fastRow);
        assertThat(fKeyHints(tab)).contains("fix with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().startsWith("A SQL statement failed");
    }

    private static SqlTraceInfo statement(String sql, long duration, boolean failed) {
        SqlTraceInfo si = new SqlTraceInfo();
        si.query = sql;
        si.category = sql.substring(0, sql.indexOf(' '));
        si.routeId = "orders";
        si.nodeId = "insert-order";
        si.location = "orders.camel.yaml:12";
        si.duration = duration;
        si.failed = failed;
        return si;
    }

    private static ErrorInfo error(String exchangeId, String type, String message) {
        ErrorInfo e = new ErrorInfo();
        e.exchangeId = exchangeId;
        e.routeId = "orders";
        e.exceptionType = type;
        e.exceptionMessage = message;
        return e;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
