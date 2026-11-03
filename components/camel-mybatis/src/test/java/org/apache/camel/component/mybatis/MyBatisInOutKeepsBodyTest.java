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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Insert, Update and Delete store the result in a header, so the body is kept, with InOut as well as with InOnly.
 */
public class MyBatisInOutKeepsBodyTest extends MyBatisTestSupport {

    @Test
    public void testInsertKeepsBody() {
        Account account = createAccount(444, "Willem");

        Exchange out = request("mybatis:insertAccount?statementType=Insert", account);

        assertSame(account, out.getMessage().getBody());
        assertEquals(1, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
        assertEquals(3, countRows());
    }

    @Test
    public void testInsertListKeepsBody() {
        List<Account> accounts = Arrays.asList(createAccount(444, "Willem"), createAccount(555, "Donald"));

        Exchange out = request("mybatis:batchInsertAccount?statementType=InsertList", accounts);

        assertSame(accounts, out.getMessage().getBody());
        assertEquals(2, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
        assertEquals(4, countRows());
    }

    @Test
    public void testUpdateKeepsBody() {
        Account account = createAccount(123, "Jim");

        Exchange out = request("mybatis:updateAccount?statementType=Update", account);

        assertSame(account, out.getMessage().getBody());
        assertEquals(1, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
    }

    @Test
    public void testUpdateListKeepsBody() {
        Map<String, Object> params = new HashMap<>();
        params.put("list", Arrays.asList(createAccount(123, "James"), createAccount(456, "Claus")));
        params.put("emailAddress", "Other@gmail.com");

        Exchange out = request("mybatis:batchUpdateAccount?statementType=UpdateList", params);

        assertSame(params, out.getMessage().getBody());
        assertEquals(2, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
    }

    @Test
    public void testDeleteKeepsBody() {
        Exchange out = request("mybatis:deleteAccountById?statementType=Delete", 456);

        assertEquals(456, out.getMessage().getBody());
        assertEquals(1, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
        assertEquals(1, countRows());
    }

    @Test
    public void testDeleteListKeepsBody() {
        List<Integer> ids = Arrays.asList(123, 456);

        Exchange out = request("mybatis:batchDeleteAccountById?statementType=DeleteList", ids);

        assertSame(ids, out.getMessage().getBody());
        assertEquals(2, out.getMessage().getHeader(MyBatisConstants.MYBATIS_RESULT));
        assertEquals(0, countRows());
    }

    @Test
    public void testSelectOneSetsResultAsBody() {
        Exchange out = request("mybatis:selectAccountById?statementType=SelectOne", 456);

        Account account = out.getMessage().getBody(Account.class);
        assertEquals("Claus", account.getFirstName());
    }

    private Exchange request(String uri, Object body) {
        Exchange out = template.send(uri, ExchangePattern.InOut, e -> e.getIn().setBody(body));
        assertNull(out.getException());
        return out;
    }

    private int countRows() {
        return template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
    }

    private static Account createAccount(int id, String firstName) {
        Account account = new Account();
        account.setId(id);
        account.setFirstName(firstName);
        account.setLastName("Last");
        account.setEmailAddress(firstName + "@example.com");
        return account;
    }
}
