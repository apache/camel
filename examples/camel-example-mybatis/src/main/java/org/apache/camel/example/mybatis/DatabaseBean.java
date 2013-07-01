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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.mybatis.MyBatisComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bean that creates the database table
 */
public class DatabaseBean implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBean.class);
    private CamelContext camelContext;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void create() throws Exception {
        String sql = "create table ORDERS (\n"
              + "  ORD_ID integer primary key,\n"
              + "  ITEM varchar(10),\n"
              + "  ITEM_COUNT varchar(5),\n"
              + "  ITEM_DESC varchar(30),\n"
              + "  ORD_DELETED boolean\n"
              + ")";

        LOG.info("Creating table orders ...");

        try {
            execute("drop table orders");
        } catch (Throwable e) {
            // ignore
        }

        execute(sql);

        LOG.info("... created table orders");
    }

    public void destroy() throws Exception {
        try {
            execute("drop table orders");
        } catch (Throwable e) {
            // ignore
        }
    }

    private void execute(String sql) throws SQLException {
        MyBatisComponent component = camelContext.getComponent("mybatis", MyBatisComponent.class);
        Connection con = component.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource().getConnection();
        Statement stm = con.createStatement();
        stm.execute(sql);
        // must commit connection
        con.commit();
        stm.close();
        con.close();
    }

}
