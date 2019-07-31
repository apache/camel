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
package org.apache.camel.component.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultSetIterator implements Iterator<Map<String, Object>> {
    private static final Logger LOG = LoggerFactory.getLogger(ResultSetIterator.class);

    private final Connection connection;
    private final Statement statement;
    private final ResultSet resultSet;
    private final Column[] columns;
    private final boolean useGetBytes;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ResultSetIterator(Connection conn, ResultSet resultSet, boolean isJDBC4, boolean useGetBytes) throws SQLException {
        this.resultSet = resultSet;
        this.statement = this.resultSet.getStatement();
        this.connection = conn;
        this.useGetBytes = useGetBytes;

        ResultSetMetaData metaData = resultSet.getMetaData();
        columns = new Column[metaData.getColumnCount()];
        for (int i = 0; i < columns.length; i++) {
            int columnNumber = i + 1;
            String columnName = getColumnName(metaData, columnNumber, isJDBC4);
            int columnType = metaData.getColumnType(columnNumber);

            if (columnType == Types.CLOB) {
                columns[i] = new ClobColumn(columnName, columnNumber);
            } else if (columnType == Types.BLOB) {
                columns[i] = new BlobColumn(columnName, columnNumber);
            } else {
                columns[i] = new DefaultColumn(columnName, columnNumber);
            }
        }

        loadNext();
    }

    @Override
    public boolean hasNext() {
        return !closed.get();
    }

    @Override
    public Map<String, Object> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        try {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column column : columns) {
                if (useGetBytes && column instanceof BlobColumn) {
                    row.put(column.getName(), ((BlobColumn) column).getBytes(resultSet));
                } else {
                    row.put(column.getName(), column.getValue(resultSet));
                }
            }
            loadNext();
            return row;
        } catch (SQLException e) {
            close();
            throw new RuntimeCamelException("Cannot process result", e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove from a database result");
    }

    public Set<String> getColumnNames() {
        // New copy each time in order to ensure immutability
        Set<String> columnNames = new LinkedHashSet<>(columns.length);
        for (Column column : columns) {
            columnNames.add(column.getName());
        }
        return columnNames;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            safeCloseResultSet();
            safeCloseStatement();
        }
    }

    public void closeConnection() {
        safeCloseConnection();
    }

    private void loadNext() throws SQLException {
        boolean hasNext = resultSet.next();
        if (!hasNext) {
            close();
        }
    }

    private void safeCloseResultSet() {
        try {
            resultSet.close();
        } catch (SQLException e) {
            LOG.warn("Error by closing result set: {}", e, e);
        }
    }

    private void safeCloseStatement() {
        try {
            statement.close();
        } catch (SQLException e) {
            LOG.warn("Error by closing statement: {}", e, e);
        }
    }

    private void safeCloseConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.warn("Error by closing connection: {}", e, e);
        }
    }

    private static String getColumnName(ResultSetMetaData metaData, int columnNumber, boolean isJDBC4) throws SQLException {
        if (isJDBC4) {
            // jdbc 4 should use label to get the name
            return metaData.getColumnLabel(columnNumber);
        } else {
            // jdbc 3 uses the label or name to get the name
            try {
                return metaData.getColumnLabel(columnNumber);
            } catch (SQLException e) {
                return metaData.getColumnName(columnNumber);
            }
        }
    }

    private interface Column {

        String getName();

        Object getValue(ResultSet resultSet) throws SQLException;
    }

    private static final class DefaultColumn implements Column {
        private final int columnNumber;
        private final String name;

        private DefaultColumn(String name, int columnNumber) {
            this.name = name;
            this.columnNumber = columnNumber;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getValue(ResultSet resultSet) throws SQLException {
            return resultSet.getObject(columnNumber);
        }
    }

    private static final class BlobColumn implements Column {
        private final int columnNumber;
        private final String name;

        private BlobColumn(String name, int columnNumber) {
            this.name = name;
            this.columnNumber = columnNumber;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getValue(ResultSet resultSet) throws SQLException {
            return resultSet.getBlob(columnNumber);
        }

        public Object getBytes(ResultSet resultSet) throws SQLException {
            return resultSet.getBytes(columnNumber);
        }
    }

    private static final class ClobColumn implements Column {
        private final int columnNumber;
        private final String name;

        private ClobColumn(String name, int columnNumber) {
            this.name = name;
            this.columnNumber = columnNumber;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getValue(ResultSet resultSet) throws SQLException {
            return resultSet.getClob(columnNumber);
        }
    }
}
