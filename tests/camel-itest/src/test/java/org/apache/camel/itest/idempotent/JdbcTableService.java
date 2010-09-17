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
package org.apache.camel.itest.idempotent;

import javax.sql.DataSource;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @version $Revision$
 */
public class JdbcTableService implements InitializingBean, DisposableBean {

    private JdbcTemplate jdbc;

    public void setDataSource(DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    public void createTable() {
        try {
            jdbc.execute("drop table ProcessedPayments");
        } catch (Exception e) {
            // ignore
        }
        jdbc.execute("create table ProcessedPayments (paymentIdentifier varchar)");
    }

    public void dropTable() {
        jdbc.execute("drop table ProcessedPayments");
    }

    public void destroy() throws Exception {
        dropTable();
    }

    public void afterPropertiesSet() throws Exception {
        createTable();
    }
}
