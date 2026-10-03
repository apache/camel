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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tells whether a SQL statement only reads (CAMEL-24834), so a small local model can query the integration's database
 * without being able to change it. A check of the statement's words, not a SQL parser: comments and quoted literals are
 * removed first (a {@code 'DELETE'} or {@code ';'} inside a string does not count), then the statement must be a single
 * one starting with SELECT, WITH, VALUES, TABLE, SHOW, EXPLAIN (not EXPLAIN ANALYZE, which runs the statement),
 * DESCRIBE or DESC, and must not write anywhere inside: no SELECT ... INTO, no FOR UPDATE / FOR SHARE locks, no INSERT,
 * UPDATE, DELETE or MERGE in a CTE. It errs on the side of refusing; the user can enable SQL writes.
 */
public final class SqlReadOnlyGuard {

    static final String ALLOWED = "only a SELECT (or WITH, SHOW, EXPLAIN, DESCRIBE) statement is allowed;"
                                  + " ask the user to enable SQL writes";

    private static final Set<String> READ_KEYWORDS
            = Set.of("SELECT", "WITH", "VALUES", "TABLE", "SHOW", "EXPLAIN", "DESCRIBE", "DESC");

    /** Words that change data, schema or grants, or run code, wherever they appear outside literals. */
    private static final Set<String> WRITE_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "TRUNCATE", "DROP", "ALTER", "CREATE",
            "GRANT", "REVOKE", "CALL", "EXEC", "EXECUTE");

    /** What follows FOR in a locking read: FOR UPDATE, FOR SHARE, FOR NO KEY UPDATE, FOR KEY SHARE. */
    private static final Set<String> LOCK_MODES = Set.of("UPDATE", "SHARE", "NO", "KEY");

    private SqlReadOnlyGuard() {
    }

    /**
     * Checks a statement.
     *
     * @return null when the statement only reads, otherwise the message to answer with
     */
    public static String check(String sql) {
        if (sql == null || sql.isBlank()) {
            return "read-only: the statement is empty; " + ALLOWED;
        }
        // MySQL reads quotes and comments differently from standard SQL (a backslash escapes a quote, # starts a
        // comment, /*! ... */ is code); where two readings disagree on what is a literal or a comment, a statement
        // could hide in the difference, so it must pass both
        String answer = check(sql, false);
        return answer != null ? answer : check(sql, true);
    }

    private static String check(String sql, boolean mysql) {
        String code = stripCommentsAndLiterals(sql, mysql);
        if (code == null) {
            return "read-only: a comment or quoted literal that is not terminated, or a nested or executable comment; "
                   + ALLOWED;
        }
        code = code.strip();
        while (code.endsWith(";")) {
            code = code.substring(0, code.length() - 1).strip();
        }
        if (code.indexOf(';') >= 0) {
            return "read-only: one statement at a time; " + ALLOWED;
        }
        List<String> words = words(code);
        if (words.isEmpty()) {
            return "read-only: the statement is empty; " + ALLOWED;
        }
        String first = words.get(0);
        if (!READ_KEYWORDS.contains(first)) {
            return "read-only: " + first + " is not a read; " + ALLOWED;
        }
        // SHOW CREATE TABLE and DESCRIBE only read the catalog
        boolean catalog = "SHOW".equals(first) || "DESCRIBE".equals(first) || "DESC".equals(first);
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            if (!catalog && WRITE_KEYWORDS.contains(w)) {
                return "read-only: the statement contains " + w + "; " + ALLOWED;
            }
            if ("INTO".equals(w)) {
                return "read-only: SELECT ... INTO writes a table; " + ALLOWED;
            }
            if ("FOR".equals(w) && LOCK_MODES.contains(next) || "LOCK".equals(w) && "IN".equals(next)) {
                return "read-only: the statement locks rows; " + ALLOWED;
            }
            if ("EXPLAIN".equals(first) && ("ANALYZE".equals(w) || "ANALYSE".equals(w))) {
                return "read-only: EXPLAIN ANALYZE runs the statement; " + ALLOWED;
            }
        }
        return null;
    }

    /**
     * The statement with comments and quoted literals or identifiers replaced by a space, read the standard way or the
     * MySQL way; null when one is not terminated, or for a comment that a database could read differently (nested, or
     * MySQL's executable {@code /*!}).
     */
    static String stripCommentsAndLiterals(String sql, boolean mysql) {
        StringBuilder sb = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : 0;
            if (c == '-' && next == '-' && (!mysql || i + 2 >= n || Character.isWhitespace(sql.charAt(i + 2)))
                    || mysql && c == '#') {
                // MySQL needs a space after --, otherwise 1--1 is arithmetic
                int eol = sql.indexOf('\n', i);
                i = eol < 0 ? n : eol;
                sb.append(' ');
            } else if (c == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0 || sql.charAt(i + 2) == '!' || sql.substring(i + 2, end).contains("/*")) {
                    return null;
                }
                i = end + 2;
                sb.append(' ');
            } else if (c == '\'' || c == '"' || c == '`') {
                int end = closingQuote(sql, i + 1, c, mysql && c != '`');
                if (end < 0) {
                    return null;
                }
                i = end + 1;
                sb.append(' ');
            } else if (c == '$' && !mysql && (i == 0 || !isIdentifierPart(sql.charAt(i - 1)))) {
                // PostgreSQL dollar quoting: $$...$$ or $tag$...$tag$
                int tagEnd = sql.indexOf('$', i + 1);
                String tag = tagEnd > 0 ? sql.substring(i, tagEnd + 1) : null;
                if (tag != null
                        && tag.substring(1, tag.length() - 1).chars().allMatch(SqlReadOnlyGuard::isIdentifierPart)) {
                    int end = sql.indexOf(tag, tagEnd + 1);
                    if (end < 0) {
                        return null;
                    }
                    i = end + tag.length();
                    sb.append(' ');
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean isIdentifierPart(int ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    /**
     * The index of the quote that closes a literal opened before {@code from}, or -1; a doubled quote is an escape, and
     * so is a backslash when asked for.
     */
    private static int closingQuote(String sql, int from, char quote, boolean backslashEscapes) {
        int i = from;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (backslashEscapes && c == '\\' && i + 1 < sql.length()) {
                i += 2;
            } else if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    private static List<String> words(String code) {
        List<String> words = new ArrayList<>();
        for (String w : code.split("[^A-Za-z0-9_]+")) {
            if (!w.isEmpty()) {
                words.add(w.toUpperCase(Locale.ROOT));
            }
        }
        return words;
    }
}
