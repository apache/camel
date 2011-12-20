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

package org.apache.camel.itest.osgi.sql;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseManager {

    private DataSource dataSource;

    public DatabaseManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table projects (id integer primary key,"
                + "project varchar(10), license varchar(5))");
        jdbcTemplate.execute("insert into projects values (1, 'Camel', 'ASF')");
        jdbcTemplate.execute("insert into projects values (2, 'AMQ', 'ASF')");
        jdbcTemplate.execute("insert into projects values (3, 'Linux', 'XXX')");
    }

    public void destroy() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table projects");
    }
}
