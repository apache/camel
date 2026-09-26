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
package org.apache.camel.component.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CAMEL-25039: a query that spells a named parameter the way other tools do, without the placeholder, is passed to the
 * database as it stands and rejected by the database's own parser, which mentions neither Camel nor the query as it was
 * written. The component says so instead.
 */
public class SqlNamedParameterWithoutPlaceholderTest {

    private static String find(String query) {
        return SqlHelper.findParameterMissingPlaceholder(query, "#");
    }

    @Test
    public void testAPlainNameIsFound() {
        assertEquals(":customer", find("INSERT INTO customers (id) VALUES (:customer)"));
    }

    @Test
    public void testASimpleExpressionWithoutThePlaceholderIsFound() {
        assertEquals(":${body[customer]}", find("INSERT INTO customers (id) VALUES (:${body[customer]})"));
    }

    @Test
    public void testTheFirstOneIsReported() {
        assertEquals(":customer", find("INSERT INTO customers (id, country) VALUES (:customer, :country)"));
    }

    @Test
    public void testTheSupportedFormsAreNotFlagged() {
        assertNull(find("select * from t where id = :#myId"));
        assertNull(find("select * from t where id = :#${exchangeProperty.myId}"));
        assertNull(find("select * from t where id = :#$simple{exchangeProperty.myId}"));
        assertNull(find("insert into t (a, b) values (:#${body.firstName}, :#${body.lastName})"));
    }

    @Test
    public void testTheInFormIsNotFlagged() {
        // the name after the second colon of :#in:myList is part of the parameter, not a mistake
        assertNull(find("select * from t where id in (:#in:myList)"));
        assertNull(find("select * from t where id in (:#in:${body.ids})"));
    }

    @Test
    public void testAPostgresCastIsNotFlagged() {
        assertNull(find("select id::text from t where id = :#myId"));
        assertNull(find("select count(*)::int from t"));
    }

    @Test
    public void testATimeLiteralIsNotFlagged() {
        assertNull(find("select * from t where created > '2026-09-26 12:30:00'"));
        assertNull(find("select * from t where hour = 12:30"));
    }

    @Test
    public void testAColonInsideAQuotedLiteralIsNotFlagged() {
        // data, not SQL: what follows the colon here is never a parameter
        assertNull(find("update t set note = ':customer not known' where id = :#myId"));
        assertNull(find("select * from t where note = 'it''s :mine'"));
    }

    @Test
    public void testASimpleExpressionWithoutBracesIsFound() {
        assertEquals(":$myId", find("select * from t where id = :$myId"));
    }

    @Test
    public void testAnotherPlaceholderIsHonoured() {
        // the endpoint's placeholder option: with it set to $, :$myId is the supported form and is not reported
        assertNull(SqlHelper.findParameterMissingPlaceholder("select * from t where id = :$myId", "$"));
        assertNull(SqlHelper.findParameterMissingPlaceholder("select * from t where id in (:$in:myList)", "$"));
        // :#myId is a mistake under that placeholder, but a colon followed by punctuation is not a shape the check
        // looks for: widening it that far would catch SQL more often than a mistake
        assertNull(SqlHelper.findParameterMissingPlaceholder("select * from t where id = :#myId", "$"));
    }

    @Test
    public void testAQueryWithNoParametersAtAllIsNotFlagged() {
        assertNull(find("select * from t order by id"));
        assertNull(find("CREATE TABLE IF NOT EXISTS customers (id varchar(10) PRIMARY KEY, orders integer)"));
    }
}
