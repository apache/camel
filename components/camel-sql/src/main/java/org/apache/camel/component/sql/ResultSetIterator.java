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
package org.apache.camel.component.sql;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

public class ResultSetIterator implements Iterator<Object>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ResultSetIterator.class);

    private final Connection connection;
    private final Statement statement;
    private final ResultSet resultSet;
    private final RowMapper<?> rowMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private int rowNum;

    public ResultSetIterator(Connection connection, Statement statement, ResultSet resultSet,
                             RowMapper<?> rowMapper) throws SQLException {
        this.connection = connection;
        this.statement = statement;
        this.resultSet = resultSet;
        this.rowMapper = rowMapper;

        loadNext();
    }

    @Override
    public boolean hasNext() {
        return !closed.get();
    }

    @Override
    public Object next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        try {
            Object next = rowMapper.mapRow(resultSet, rowNum++);
            loadNext();
            return next;
        } catch (SQLException e) {
            close();
            throw new RuntimeCamelException("Cannot process result", e);
        }
    }

    private void loadNext() throws SQLException {
        boolean hasNext = resultSet.next();
        if (!hasNext) {
            close();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            safeCloseResultSet();
            safeCloseStatement();
            safeCloseConnection();
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

}
