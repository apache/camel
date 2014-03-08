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
package org.apache.camel.component.mybatis;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;

public abstract class MyBatisTestSupport extends CamelTestSupport {

    protected boolean createTestData() {
        return true;
    }
    
    protected String createStatement() {
        return "create table ACCOUNT (ACC_ID INTEGER, ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255))";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // lets create the table...
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute(createStatement());
        connection.commit();
        statement.close();
        connection.close();

        if (createTestData()) {
            Account account1 = new Account();
            account1.setId(123);
            account1.setFirstName("James");
            account1.setLastName("Strachan");
            account1.setEmailAddress("TryGuessing@gmail.com");

            Account account2 = new Account();
            account2.setId(456);
            account2.setFirstName("Claus");
            account2.setLastName("Ibsen");
            account2.setEmailAddress("Noname@gmail.com");
            
            template.sendBody("mybatis:insertAccount?statementType=Insert", new Account[]{account1, account2});
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // should drop the table properly to avoid any side effects while running all the tests together under maven
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("drop table ACCOUNT");
        connection.commit();
        statement.close();
        connection.close();

        super.tearDown();
    }

    private Connection createConnection() throws Exception {
        MyBatisComponent component = context.getComponent("mybatis", MyBatisComponent.class);
        return component.createSqlSessionFactory().getConfiguration().getEnvironment().getDataSource().getConnection();
    }

}
