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
package org.apache.camel.component.drill;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * A drill producer
 */
public class DrillProducer extends DefaultProducer {

    private DrillEndpoint endpoint;

    private Connection connection;

    public DrillProducer(DrillEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        createJDBCConnection();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            connection.close();
        } catch (Exception e) {
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final String query = exchange.getIn().getHeader(DrillConstants.DRILL_QUERY, String.class);

        // check query
        Statement st = null;
        ResultSet rs = null;
        try {
            st = connection.createStatement();
            rs = st.executeQuery(query);

            exchange.getIn().setBody(endpoint.queryForList(rs));
        } finally {
            try {
                rs.close();
            } catch (Exception e) {
            }
            try {
                st.close();
            } catch (Exception e) {
            }
        }
    } // end process

    private void createJDBCConnection() throws ClassNotFoundException, SQLException {
        Class.forName(DrillConstants.DRILL_DRIVER);

        // if(log.isDebugEnabled()) {
        log.info("connection url: {}", endpoint.toJDBCUri());
        // }

        this.connection = DriverManager.getConnection(endpoint.toJDBCUri());
    }
}
