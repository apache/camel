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
package org.apache.camel.component.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A String body is one parameter, also when it contains a comma or is blank; only collections, iterators and object
 * arrays run the statement once per element.
 */
public class MyBatisStringParameterTest extends MyBatisTestSupport {

    @Test
    public void testInsertStringWithComma() {
        template.sendBody("mybatis:insertAccountWithLastName?statementType=Insert", "Doe, Jr.");

        assertEquals(3, rowCount(), "There should be 3 rows");
        Account account = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 789, Account.class);
        assertEquals("Doe, Jr.", account.getLastName());
    }

    @Test
    public void testUpdateStringWithComma() {
        template.sendBody("mybatis:updateLastNameOfAccount123?statementType=Update", "Strachan, Jr.");

        Account account = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 123, Account.class);
        assertEquals("Strachan, Jr.", account.getLastName());
    }

    @Test
    public void testDeleteStringWithComma() {
        template.sendBody("mybatis:updateLastNameOfAccount123?statementType=Update", "Ibsen, Strachan");

        // deletes account 123 only, not the account 456 with the last name Ibsen
        template.sendBody("mybatis:deleteAccountByLastName?statementType=Delete", "Ibsen, Strachan");

        assertEquals(1, rowCount(), "There should be 1 row");
        Account account = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 456, Account.class);
        assertEquals("Ibsen", account.getLastName());
    }

    @Test
    public void testInsertBlankString() {
        template.sendBody("mybatis:insertAccountWithLastName?statementType=Insert", "");

        assertEquals(3, rowCount(), "There should be 3 rows");
        Account account = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 789, Account.class);
        assertEquals("", account.getLastName());
    }

    private int rowCount() {
        return template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
    }
}
