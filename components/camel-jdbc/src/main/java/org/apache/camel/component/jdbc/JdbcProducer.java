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
package org.apache.camel.component.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class JdbcProducer extends DefaultProducer<DefaultExchange> {

    private static final transient Log LOG = LogFactory.getLog(JdbcProducer.class);
    private DataSource source;

    /** The maximum size for reading a result set <code>readSize</code> */
    private int readSize = 2000;

    public JdbcProducer(JdbcEndpoint endpoint, String remaining, int readSize) throws Exception {
        super(endpoint);
        this.readSize = readSize;
        source = (DataSource) getEndpoint().getCamelContext().getRegistry().lookup(remaining);
    }

    /**
     * Execute sql of exchange and set results on output
     *
     * @see org.apache.camel.Processor#process(org.apache.camel.Exchange)
     */
    public void process(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = source.getConnection();
            stmt = conn.createStatement();
            if (stmt.execute(sql)) {
                rs = stmt.getResultSet();
                setResultSet(exchange, rs);
            } else {
                int updateCount = stmt.getUpdateCount();
                exchange.getOut().setHeader("jdbc.updateCount", updateCount);
            }
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                LOG.warn("Error closing JDBC resource: " + e, e);
            }
        }
    }

    public int getReadSize() {
        return this.readSize;
    }

    public void setReadSize(int readSize) {
        this.readSize = readSize;
    }

    public void setResultSet(Exchange exchange, ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();

        HashMap<String, Object> props = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(meta, props, "jdbc.");
        exchange.getOut().setHeaders(props);

        int count = meta.getColumnCount();
        List<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();
        int rowNumber = 0;
        while (rs.next() && rowNumber < readSize) {
            HashMap<String, Object> row = new HashMap<String, Object>();
            for (int i = 0; i < count; i++) {
                int columnNumber = i + 1;
                String columnName = meta.getColumnName(columnNumber);
                row.put(columnName, rs.getObject(columnName));
            }
            data.add(row);
            rowNumber++;
        }
        exchange.getOut().setBody(data);
    }

}
