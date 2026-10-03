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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlReadOnlyGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM orders",
            "select id, name from orders where id = 1;",
            "  SELECT 1 ;  ",
            "(SELECT 1) UNION (SELECT 2)",
            "WITH recent AS (SELECT * FROM orders WHERE ts > now()) SELECT count(*) FROM recent",
            "VALUES (1, 'a')",
            "TABLE orders",
            "SHOW TABLES",
            "SHOW CREATE TABLE orders",
            "EXPLAIN SELECT * FROM orders",
            "DESCRIBE orders",
            "DESC orders",
            "SELECT * FROM orders ORDER BY id DESC",
            "-- the orders\nSELECT * FROM orders",
            "/* all of them */ SELECT * FROM orders",
            "SELECT 'DELETE FROM orders; DROP TABLE x' AS text FROM orders",
            "SELECT 'a;b' FROM orders WHERE note = 'it''s; fine'",
            "SELECT \"update\" FROM \"insert\"",
            "SELECT replace(name, 'a', 'b') FROM orders",
            "SELECT * FROM orders WHERE id = $1",
            "SELECT 1 # a MySQL comment\n"
    })
    void allowsReads(String sql) {
        assertNull(SqlReadOnlyGuard.check(sql), sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "INSERT INTO orders VALUES (1)",
            "update orders set name = 'x'",
            "DELETE FROM orders",
            "DROP TABLE orders",
            "MERGE INTO orders USING x ON (1=1) WHEN MATCHED THEN DELETE",
            "SELECT 1; DELETE FROM orders",
            "SELECT 1; SELECT 2",
            "WITH gone AS (DELETE FROM orders RETURNING *) SELECT * FROM gone",
            "WITH x AS (INSERT INTO orders VALUES (1) RETURNING id) SELECT id FROM x",
            "SELECT * INTO backup FROM orders",
            "SELECT * FROM orders FOR UPDATE",
            "SELECT * FROM orders FOR SHARE",
            "SELECT * FROM orders FOR NO KEY UPDATE",
            "SELECT * FROM orders LOCK IN SHARE MODE",
            "EXPLAIN ANALYZE DELETE FROM orders",
            "EXPLAIN (ANALYZE) SELECT * FROM orders",
            "CALL cleanup()",
            "SELECT 1 /* unterminated",
            "SELECT 'unterminated",
            // a comment hides the second statement in one reading only
            "SELECT 1 -- \n; DELETE FROM orders",
            "SELECT 1 /* /* */ ' */ ; DELETE FROM orders; -- '",
            "SELECT 1 /*! ; DELETE FROM orders */",
            "SELECT 'a\\' ; DELETE FROM orders; -- '",
            "SELECT 1 # '\n; DELETE FROM orders; -- '",
            "SELECT 1 --x' \n '; DELETE FROM orders; -- '",
            // PostgreSQL dollar quoting is no quoting in MySQL
            "SELECT $$; DELETE FROM orders$$"
    })
    void refusesWrites(String sql) {
        String answer = SqlReadOnlyGuard.check(sql);
        assertNotNull(answer, sql);
        assertTrue(answer.startsWith("read-only: "), answer);
        assertTrue(answer.contains("ask the user to enable SQL writes"), answer);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT 1; SELECT 2" })
    void saysWhy(String sql) {
        assertTrue(SqlReadOnlyGuard.check(sql).contains("one statement at a time"));
        assertTrue(SqlReadOnlyGuard.check("DELETE FROM orders").contains("DELETE is not a read"));
    }
}
