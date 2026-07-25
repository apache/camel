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
package org.apache.camel.component.duckdb;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuckDbProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbProducer.class);

    private final DuckDbEndpoint endpoint;

    public DuckDbProducer(DuckDbEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DuckDbOperation operation = resolveOperation(exchange);
        String databasePathOverride = exchange.getIn().getHeader(DuckDbConstants.DATABASE_PATH, String.class);
        try (DuckDbConnectionScope scope = endpoint.openConnectionScope(databasePathOverride)) {
            Connection connection = scope.connection();
            switch (operation) {
                case EXECUTE -> doExecute(exchange, connection);
                case QUERY -> doQuery(exchange, connection);
                case INSERT -> doInsert(exchange, connection);
                case COPY -> doCopy(exchange, connection);
                case PING -> doPing(exchange, connection);
                default -> throw new DuckDbException("Unsupported operation: " + operation);
            }
        }
    }

    private void doExecute(Exchange exchange, Connection connection) throws Exception {
        String sql = resolveSql(exchange);
        if (ObjectHelper.isEmpty(sql)) {
            throw new DuckDbException("SQL is required for execute (message body or query option)");
        }
        long updated;
        try (Statement statement = connection.createStatement()) {
            updated = statement.executeUpdate(sql);
        }
        setRowsWritten(exchange, updated);
        exchange.getMessage().setBody(updated);
    }

    private void doQuery(Exchange exchange, Connection connection) throws Exception {
        String sql = resolveSql(exchange);
        if (ObjectHelper.isEmpty(sql)) {
            throw new DuckDbException("SQL is required for query (message body, query option or CamelDuckDbQuery header)");
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<Map<String, Object>> rows = DuckDbResultSetMapper.toListMap(resultSet);
            Message message = exchange.getMessage();
            message.setHeader(DuckDbConstants.ROWS_READ, (long) rows.size());
            if (endpoint.getResultFormat() == DuckDbResultFormat.JSON) {
                message.setBody(DuckDbResultSetMapper.toJson(rows));
            } else {
                message.setBody(rows);
            }
        }
    }

    private void doInsert(Exchange exchange, Connection connection) throws Exception {
        String table = resolveTable(exchange);
        if (ObjectHelper.isEmpty(table)) {
            throw new DuckDbException(
                    "A table is required for insert. Provide the table option or the "
                                      + DuckDbConstants.TABLE + " header.");
        }
        List<?> body = exchange.getIn().getBody(List.class);
        if (body == null) {
            throw new DuckDbException("Insert operation expects the message body to be a List of rows");
        }
        long written = DuckDbInsertSupport.insertMaps(connection, table, body, endpoint.getBatchSize());
        LOG.debug("Inserted {} rows into {}", written, table);
        setRowsWritten(exchange, written);
    }

    private void doCopy(Exchange exchange, Connection connection) throws Exception {
        String table = resolveTable(exchange);
        if (ObjectHelper.isEmpty(table)) {
            throw new DuckDbException("A table is required for copy operations");
        }
        String path = resolveFilePath(exchange);
        String format = resolveFormat(path);
        String sql = buildCopySql(table, path, format);
        long updated;
        try (Statement statement = connection.createStatement()) {
            updated = statement.executeUpdate(sql);
        }
        setRowsWritten(exchange, updated);
    }

    private void doPing(Exchange exchange, Connection connection) throws Exception {
        boolean ok;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            ok = rs.next() && rs.getInt(1) == 1;
        }
        Message message = exchange.getMessage();
        message.setHeader(DuckDbConstants.PING_OK, ok);
        message.setBody(ok);
    }

    private String buildCopySql(String table, String path, String format) {
        String escapedPath = DuckDbJdbcSupport.escapeSqlLiteral(path);
        String quotedTable = DuckDbJdbcSupport.quoteQualifiedIdentifier(table);
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> "INSERT INTO " + quotedTable + " SELECT * FROM read_csv('" + escapedPath + "')";
            case "parquet" -> "INSERT INTO " + quotedTable + " SELECT * FROM read_parquet('" + escapedPath + "')";
            case "json" -> "INSERT INTO " + quotedTable + " SELECT * FROM read_json('" + escapedPath + "')";
            default -> throw new DuckDbException("Unsupported copy format: " + format);
        };
    }

    private String resolveFormat(String path) {
        String configured = endpoint.getFormat();
        if (ObjectHelper.isNotEmpty(configured) && !"auto".equalsIgnoreCase(configured)) {
            return configured;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        if (lower.endsWith(".parquet")) {
            return "parquet";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        throw new DuckDbException("Could not infer copy format from file name: " + path);
    }

    private static String resolveFilePath(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile<?> wrappedFile) {
            body = wrappedFile.getBody();
        }
        if (body instanceof File file) {
            return file.getAbsolutePath();
        }
        if (body instanceof String str && ObjectHelper.isNotEmpty(str)) {
            return str.trim();
        }
        throw new DuckDbException("Copy operation expects a file path in the message body (File or String)");
    }

    private void setRowsWritten(Exchange exchange, long rows) {
        exchange.getMessage().setHeader(DuckDbConstants.ROWS_WRITTEN, rows);
        exchange.getMessage().setBody(rows);
    }

    private DuckDbOperation resolveOperation(Exchange exchange) {
        String header = exchange.getIn().getHeader(DuckDbConstants.OPERATION, String.class);
        if (ObjectHelper.isNotEmpty(header)) {
            return DuckDbOperation.valueOf(header.trim().toUpperCase(Locale.ROOT));
        }
        return endpoint.getOperation();
    }

    private String resolveSql(Exchange exchange) {
        String header = exchange.getIn().getHeader(DuckDbConstants.QUERY, String.class);
        if (ObjectHelper.isNotEmpty(header)) {
            return header;
        }
        if (ObjectHelper.isNotEmpty(endpoint.getQuery())) {
            return endpoint.getQuery();
        }
        return exchange.getIn().getBody(String.class);
    }

    private String resolveTable(Exchange exchange) {
        String header = exchange.getIn().getHeader(DuckDbConstants.TABLE, String.class);
        if (ObjectHelper.isNotEmpty(header)) {
            return header;
        }
        return endpoint.getTable();
    }
}
