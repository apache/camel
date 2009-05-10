/**
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
package org.apache.camel.component.ibatis;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.camel.ContextTestSupport;

public class IBatisTestSupport extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // lets create the database...
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255)  )");
        connection.close();

        Account account = new Account();
        account.setId(123);
        account.setFirstName("James");
        account.setLastName("Strachan");
        account.setEmailAddress("TryGuessing@gmail.com");
        template.sendBody("ibatis:insertAccount", account);

        account = new Account();
        account.setId(456);
        account.setFirstName("Claus");
        account.setLastName("Ibsen");
        account.setEmailAddress("Noname@gmail.com");

        template.sendBody("ibatis:insertAccount", account);
    }

    @Override
    protected void tearDown() throws Exception {
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("drop table ACCOUNT");
        connection.close();

        super.tearDown();
    }

    private Connection createConnection() throws Exception {
        IBatisEndpoint endpoint = resolveMandatoryEndpoint("ibatis:Account", IBatisEndpoint.class);
        return endpoint.getSqlMapClient().getDataSource().getConnection();
    }
}