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

import org.apache.camel.CamelExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MyBatisInsertNullBodyTest extends MyBatisTestSupport {

    @Test
    public void testInsertNullBodyFailsWhenStatementHasParameters() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("mybatis:insertAccount?statementType=Insert", null));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The message body is null, but the MyBatis statement insertAccount has parameters",
                cause.getMessage());
        // no row of nulls is inserted
        assertEquals(2, countRows());
    }

    @Test
    public void testInsertMissingInputHeaderFailsWhenStatementHasParameters() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("mybatis:insertAccount?statementType=Insert&inputHeader=account", "ignored"));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The header account is null, but the MyBatis statement insertAccount has parameters",
                cause.getMessage());
        assertEquals(2, countRows());
    }

    @Test
    public void testInsertNullBodyWithoutParameters() {
        template.sendBody("mybatis:insertDefaultAccount?statementType=Insert", null);

        assertEquals(3, countRows());
        Account account = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 999, Account.class);
        assertEquals("Default", account.getFirstName());
    }

    private int countRows() {
        return template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
    }
}
