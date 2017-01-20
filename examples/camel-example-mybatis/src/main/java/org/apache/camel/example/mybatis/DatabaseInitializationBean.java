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
package org.apache.camel.example.mybatis;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.derby.jdbc.EmbeddedDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Bean that creates the database table
 */
public class DatabaseInitializationBean {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializationBean.class);

    String url;

    Connection connection;

    public DatabaseInitializationBean() {
    }

    public void create() throws Exception {
        LOG.info("Creating database tables ...");
        if (connection == null) {
            EmbeddedDriver driver = new EmbeddedDriver();
            connection = driver.connect(url + ";create=true", null);
        }

        String sql = "create table ORDERS (\n"
                + "  ORD_ID integer primary key,\n"
                + "  ITEM varchar(10),\n"
                + "  ITEM_COUNT varchar(5),\n"
                + "  ITEM_DESC varchar(30),\n"
                + "  ORD_DELETED boolean\n"
                + ")";

        try {
            execute("drop table orders");
        } catch (Throwable e) {
            // ignore
        }

        execute(sql);

        LOG.info("Database tables created");
    }

    public void drop() throws Exception {
        LOG.info("Dropping database tables ...");

        try {
            execute("drop table orders");
        } catch (Throwable e) {
            // ignore
        }
        connection.close();

        LOG.info("Database tables dropped");
    }

    private void execute(String sql) throws SQLException {
        Statement stm = connection.createStatement();
        stm.execute(sql);
        // must commit connection
        connection.commit();
        stm.close();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
