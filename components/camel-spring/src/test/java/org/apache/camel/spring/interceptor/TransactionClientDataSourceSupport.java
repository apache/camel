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
package org.apache.camel.spring.interceptor;

import javax.sql.DataSource;

import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class TransactionClientDataSourceSupport extends SpringTestSupport {
    protected JdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/spring/interceptor/transactionalClientDataSource.xml");
    }

    @Override
    protected void setUp() throws Exception {
        disableJMX();
        super.setUp();

        // START SNIPPET: e5
        // create database (dummy data already inserted by Spring)
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
        // END SNIPPET: e5
    }

    public boolean isUseTransactionErrorHandler() {
        return useTransactionErrorHandler;
    }

}