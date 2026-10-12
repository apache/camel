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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An insert-or-update written in the dialect of another database than the datasource of the application: the statement
 * fails with a syntax error for every message, which a model reads as a problem of its route and rewrites in yet
 * another dialect (the service-sql example of the local-model benchmark, a PostgreSQL datasource, failed half of its
 * runs with ON DUPLICATE KEY UPDATE). The datasource is read from application.properties.
 */
final class SqlDialectChecks {

    private enum Product {
        POSTGRESQL("PostgreSQL"),
        MYSQL("MySQL");

        private final String title;

        Product(String title) {
            this.title = title;
        }
    }

    private static final Pattern JDBC_URL = Pattern.compile("jdbc:(postgresql|mysql|mariadb):");
    /**
     * The statement of a sql: endpoint: in the uri (uri: sql:INSERT ...) or as its query option (query: INSERT ...).
     */
    private static final Pattern STATEMENT
            = Pattern.compile("^\\s*(?:-\\s+)?(?:uri|query):\\s*[\"']?(?:sql:)?(.*?)[\"']?\\s*$");
    private static final Pattern INSERT_INTO = Pattern.compile("(?i)\\bINSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern ON_DUPLICATE = Pattern.compile("(?i)\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s+(.*)$");
    private static final Pattern ON_CONFLICT
            = Pattern.compile("(?i)\\bON\\s+CONFLICT\\s*(?:\\(([^)]*)\\))?\\s*DO\\s+UPDATE\\s+SET\\s+(.*)$");

    private SqlDialectChecks() {
    }

    /** The messages for the statements of the YAML route written in the dialect of another database. */
    static List<String> validate(String content, Path directory) {
        Product product = datasource(directory);
        if (product == null || content == null) {
            return List.of();
        }
        List<String> answer = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = STATEMENT.matcher(lines[i]);
            if (!m.matches()) {
                continue;
            }
            String sql = m.group(1).replaceAll("\\?\\w+=.*$", "");
            Matcher insert = INSERT_INTO.matcher(sql);
            if (!insert.find()) {
                continue;
            }
            String table = insert.group(1);
            String head = sql.substring(0, insertEnd(sql)).strip();
            Matcher dup = ON_DUPLICATE.matcher(sql);
            Matcher conflict = ON_CONFLICT.matcher(sql);
            if (product == Product.POSTGRESQL && dup.find()) {
                String key = primaryKey(content, table);
                answer.add("Line " + (i + 1) + ": sql: ON DUPLICATE KEY UPDATE is MySQL, and the datasource of"
                           + " application.properties is PostgreSQL, where the statement fails with a syntax error:"
                           + " write " + head + " ON CONFLICT (" + (key != null ? key : "<its primary key column>")
                           + ") DO UPDATE SET " + toPostgres(dup.group(1).strip(), table)
                           + " (the row already in the table is " + table + ".column, the row being inserted is"
                           + " EXCLUDED.column, both without :#)");
            } else if (product == Product.MYSQL && conflict.find()) {
                answer.add("Line " + (i + 1) + ": sql: ON CONFLICT ... DO UPDATE is PostgreSQL, and the datasource of"
                           + " application.properties is " + product.title + ", where the statement fails with a"
                           + " syntax error: write " + head + " ON DUPLICATE KEY UPDATE "
                           + toMysql(conflict.group(2).strip(), table)
                           + " (VALUES(column) is the row being inserted)");
            }
        }
        return answer;
    }

    /** The end of INSERT INTO ... VALUES (...): where the upsert clause starts. */
    private static int insertEnd(String sql) {
        Matcher m = Pattern.compile("(?i)\\s+ON\\s+(DUPLICATE|CONFLICT)\\b").matcher(sql);
        return m.find() ? m.start() : sql.length();
    }

    /** orders = orders + 1, VALUES(country) as orders = customers.orders + 1, EXCLUDED.country. */
    static String toPostgres(String assignments, String table) {
        List<String> parts = new ArrayList<>();
        for (String a : assignments.split(",(?![^(]*\\))")) {
            int eq = a.indexOf('=');
            if (eq < 0) {
                parts.add(a.strip());
                continue;
            }
            String column = a.substring(0, eq).strip();
            String value = a.substring(eq + 1).strip()
                    .replaceAll("(?i)\\bVALUES\\s*\\(\\s*(\\w+)\\s*\\)", "EXCLUDED.$1");
            value = value.replaceAll("(?<![.\\w:#])\\b" + Pattern.quote(column) + "\\b(?!\\s*\\()", table + "." + column);
            parts.add(column + " = " + value);
        }
        return String.join(", ", parts);
    }

    /** orders = customers.orders + 1, EXCLUDED.country as orders = orders + 1, VALUES(country). */
    static String toMysql(String assignments, String table) {
        return assignments.replaceAll("(?i)\\bEXCLUDED\\.(\\w+)", "VALUES($1)")
                .replaceAll("\\b" + Pattern.quote(table) + "\\.(\\w+)", "$1");
    }

    /** The primary key column of the table, from a CREATE TABLE in the same file; null when not there. */
    static String primaryKey(String content, String table) {
        Matcher m = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + Pattern.quote(table)
                                    + "\\s*\\((.*?)\\)\\s*[\"']?\\s*$",
                Pattern.MULTILINE | Pattern.DOTALL).matcher(content);
        if (!m.find()) {
            return null;
        }
        Matcher col = Pattern.compile("(?i)(\\w+)\\s+[\\w()]+(?:\\s+NOT\\s+NULL)?\\s+PRIMARY\\s+KEY").matcher(m.group(1));
        if (col.find()) {
            return col.group(1);
        }
        Matcher key = Pattern.compile("(?i)PRIMARY\\s+KEY\\s*\\(\\s*([\\w,\\s]+?)\\s*\\)").matcher(m.group(1));
        return key.find() ? key.group(1) : null;
    }

    /** The one database the application's properties point to, or null when none or more than one. */
    private static Product datasource(Path directory) {
        if (directory == null) {
            return null;
        }
        Path props = directory.resolve("application.properties");
        if (!Files.isRegularFile(props)) {
            return null;
        }
        Set<Product> found = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                String l = line.strip();
                if (l.startsWith("#")) {
                    continue;
                }
                Matcher m = JDBC_URL.matcher(l.toLowerCase(Locale.ROOT));
                if (m.find()) {
                    found.add("postgresql".equals(m.group(1)) ? Product.POSTGRESQL : Product.MYSQL);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return found.size() == 1 ? found.iterator().next() : null;
    }
}
