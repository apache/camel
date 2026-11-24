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

import java.util.List;
import java.util.Objects;

import org.apache.camel.util.URISupport;

/**
 * The questions the AI panel is opened with on the SQL Trace tab (Shift+F8, CAMEL-25362): fix a statement that failed,
 * with the exception of its exchange; or explain a slow one, with how often the same SQL ran and how long it took. It
 * is put in the input of the panel, for the user to send with Enter or change first.
 */
final class SqlTraceAiPrompt {

    /** A statement this slow or slower is slow, as the KPI strip and the table count it. */
    static final long SLOW_MS = 100;

    private SqlTraceAiPrompt() {
    }

    /** Whether Shift+F8 has something to ask about a statement: it failed, or it was slow. */
    static boolean hasTrouble(SqlTraceInfo si) {
        return si.failed || si.duration >= SLOW_MS;
    }

    static String of(IntegrationInfo info, SqlTraceInfo si) {
        StringBuilder sb = new StringBuilder();
        String name = info.name != null ? info.name : info.pid;
        if (si.failed) {
            sb.append("A SQL statement failed in the running integration ").append(name);
        } else {
            sb.append("A SQL statement is slow in the running integration ").append(name).append(": it took ")
                    .append(si.duration).append(" ms");
        }
        sb.append(".\n");
        sb.append("The statement").append(si.category != null ? " (" + si.category + ")" : "").append(":\n```sql\n")
                .append(si.query != null ? si.query.strip() : "").append("\n```\n");
        if (si.endpoint != null) {
            sb.append("Its endpoint: ").append(URISupport.sanitizeUri(si.endpoint)).append('\n');
        }
        if (si.routeId != null) {
            sb.append("It ran in route ").append(si.routeId);
            if (si.nodeId != null) {
                sb.append(" at step ").append(si.nodeId);
            }
            if (si.location != null) {
                sb.append(", ").append(si.location);
            }
            sb.append(".\n");
        }
        if (si.failed) {
            ErrorInfo error = errorOf(info, si);
            if (error != null) {
                sb.append("The error: ").append(RuntimeFailures.failureOf(error)).append('\n');
            }
            sb.append("Find the cause (such as the SQL itself, a table or column that does not exist, the parameters,")
                    .append(" or the connection) from the integration's errors and log. When it is in the project's")
                    .append(" routes or configuration, fix it with camel_edit_file and check the file with")
                    .append(" camel_validate_source; dev mode reloads the file when it is saved. When it is in the")
                    .append(" database (its schema or data), say what to change there, and make no changes to the")
                    .append(" files.");
        } else {
            same(sb, info.sqlTraceStatements, si);
            if (si.rowCount > 0) {
                sb.append("It returned ").append(si.rowCount).append(" rows.\n");
            } else if (si.updateCount > 0) {
                sb.append("It updated ").append(si.updateCount).append(" rows.\n");
            }
            sb.append("Tell why it may be slow (such as a missing index, many rows, a lock, or a statement per")
                    .append(" message that could be a batch) and suggest what could make it faster, in the SQL, the")
                    .append(" database or the route. tui_execute_sql can run an EXPLAIN of it to check.")
                    .append(" Make no changes to the files unless I agree.");
        }
        return sb.toString();
    }

    /** How often the same SQL ran among the traced statements, and its mean and slowest time. */
    private static void same(StringBuilder sb, List<SqlTraceInfo> all, SqlTraceInfo si) {
        long count = 0;
        long sum = 0;
        long max = 0;
        for (SqlTraceInfo other : all) {
            if (Objects.equals(other.query, si.query)) {
                count++;
                sum += other.duration;
                max = Math.max(max, other.duration);
            }
        }
        if (count > 1) {
            sb.append("The same SQL ran ").append(count).append(" times among the traced statements: mean ")
                    .append(sum / count).append(" ms, slowest ").append(max).append(" ms.\n");
        }
    }

    /** The error of the statement's exchange, else the newest error of its route; null when there is none. */
    static ErrorInfo errorOf(IntegrationInfo info, SqlTraceInfo si) {
        if (si.exchangeId != null) {
            for (ErrorInfo e : info.errors) {
                if (si.exchangeId.equals(e.exchangeId)) {
                    return e;
                }
            }
        }
        return RouteAiPrompt.lastError(info, si.routeId);
    }
}
