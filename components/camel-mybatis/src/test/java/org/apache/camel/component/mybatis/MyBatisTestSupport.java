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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;

public abstract class MyBatisTestSupport extends CamelTestSupport {

    protected boolean createTestData() {
        return true;
    }

    /**
     * Gets the name of the database table handling the test data.
     * 
     * @return The name of the database table handling the test data.
     */
    protected String getTableName() {
        return "ACCOUNT";
    }

    /**
     * Gets the SQL query dropping the test data table.
     * 
     * @return The SQL query dropping the test data table.
     */
    protected String getDropStatement() {
        return "drop table ACCOUNT";
    }

    /**
     * Gets the SQL query creating the test data table.
     * 
     * @return The SQL query creating the test data table.
     */
    protected String getCreateStatement() {
        return "create table ACCOUNT (ACC_ID INTEGER, ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255))";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        try (Connection connection = createConnection();
             ResultSet checkTableExistResultSet = connection.getMetaData().getTables(null, null, getTableName(), null);
             Statement deletePreExistingTableStatement = connection.createStatement();
             Statement createTableStatement = connection.createStatement()) {

            // delete any pre-existing ACCOUNT table
            if (checkTableExistResultSet.next()) {
                deletePreExistingTableStatement.execute(getDropStatement());
            }

            // lets create the table...
            createTableStatement.execute(getCreateStatement());
            connection.commit();
        }

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

            template.sendBody("mybatis:insertAccount?statementType=Insert", new Account[] { account1, account2 });
        }
    }

    protected Connection createConnection() throws Exception {
        MyBatisComponent component = context.getComponent("mybatis", MyBatisComponent.class);
        return component.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource().getConnection();
    }

}
