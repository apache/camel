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
package org.apache.camel.component.pgvector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pgvector.PGvector;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;

public class PgVectorProducer extends DefaultProducer {

    public PgVectorProducer(PgVectorEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public PgVectorEndpoint getEndpoint() {
        return (PgVectorEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) {
        final Message in = exchange.getMessage();
        final PgVectorAction action = in.getHeader(PgVectorHeaders.ACTION, PgVectorAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, PgVectorHeaders.ACTION);
            }

            switch (action) {
                case CREATE_TABLE:
                    createTable(exchange);
                    break;
                case CREATE_INDEX:
                    createIndex(exchange);
                    break;
                case DROP_TABLE:
                    dropTable(exchange);
                    break;
                case UPSERT:
                    upsert(exchange);
                    break;
                case DELETE:
                    delete(exchange);
                    break;
                case SIMILARITY_SEARCH:
                    similaritySearch(exchange);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    // ***************************************
    //
    // Actions
    //
    // ***************************************

    private void createTable(Exchange exchange) throws SQLException {
        String tableName = getEndpoint().getCollection();
        int dimension = getEndpoint().getConfiguration().getDimension();

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            }

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s ("
                                       + "id VARCHAR(36) PRIMARY KEY, "
                                       + "text_content TEXT, "
                                       + "metadata TEXT, "
                                       + "embedding vector(%d))",
                    sanitizeIdentifier(tableName), dimension);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql); // NOSONAR S2077 - table name validated by sanitizeIdentifier()
            }

            exchange.getMessage().setBody(true);
        }
    }

    private void createIndex(Exchange exchange) throws SQLException {
        String tableName = getEndpoint().getCollection();
        PgVectorDistanceType distanceType = getEndpoint().getConfiguration().getDistanceType();

        String sql = String.format(
                "CREATE INDEX IF NOT EXISTS %s_embedding_idx ON %s USING hnsw (embedding %s)",
                sanitizeIdentifier(tableName), sanitizeIdentifier(tableName), distanceType.getIndexOpsClass());

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql); // NOSONAR S2077 - table name validated by sanitizeIdentifier()
            }

            exchange.getMessage().setBody(true);
        }
    }

    private void dropTable(Exchange exchange) throws SQLException {
        String tableName = getEndpoint().getCollection();

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            String sql = String.format("DROP TABLE IF EXISTS %s", sanitizeIdentifier(tableName));
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql); // NOSONAR S2077 - table name validated by sanitizeIdentifier()
            }

            exchange.getMessage().setBody(true);
        }
    }

    @SuppressWarnings("unchecked")
    private void upsert(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String tableName = getEndpoint().getCollection();
        String id = in.getHeader(PgVectorHeaders.RECORD_ID, () -> UUID.randomUUID().toString(), String.class);

        List<Float> vector = in.getMandatoryBody(List.class);
        float[] vectorArray = toFloatArray(vector);

        String textContent = in.getHeader(PgVectorHeaders.TEXT_CONTENT, String.class);
        String metadata = in.getHeader(PgVectorHeaders.METADATA, String.class);

        String sql = String.format(
                "INSERT INTO %s (id, text_content, metadata, embedding) VALUES (?, ?, ?, ?) "
                                   + "ON CONFLICT (id) DO UPDATE SET text_content = EXCLUDED.text_content, "
                                   + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                sanitizeIdentifier(tableName));

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            PGvector.addVectorType(conn);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) { // NOSONAR S2077 - table name validated by sanitizeIdentifier()
                pstmt.setString(1, id);
                pstmt.setString(2, textContent);
                pstmt.setString(3, metadata);
                pstmt.setObject(4, new PGvector(vectorArray));
                pstmt.executeUpdate();
            }
        }

        exchange.getMessage().setBody(id);
    }

    private void delete(Exchange exchange) throws Exception {
        String tableName = getEndpoint().getCollection();
        String id = ExchangeHelper.getMandatoryHeader(exchange, PgVectorHeaders.RECORD_ID, String.class);

        String sql = String.format("DELETE FROM %s WHERE id = ?", sanitizeIdentifier(tableName));

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) { // NOSONAR S2077 - table name validated by sanitizeIdentifier()
                pstmt.setString(1, id);
                int deleted = pstmt.executeUpdate();
                exchange.getMessage().setBody(deleted > 0);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void similaritySearch(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        String tableName = getEndpoint().getCollection();
        int topK = in.getHeader(PgVectorHeaders.QUERY_TOP_K, 3, Integer.class);
        PgVectorDistanceType distanceType = getEndpoint().getConfiguration().getDistanceType();
        String filter = in.getHeader(PgVectorHeaders.FILTER, String.class);
        List<?> filterParams = in.getHeader(PgVectorHeaders.FILTER_PARAMS, List.class);

        List<Float> queryVector = in.getMandatoryBody(List.class);
        float[] vectorArray = toFloatArray(queryVector);

        String distanceOp = distanceType.getOperator();
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(String.format(
                "SELECT id, text_content, metadata, embedding %s ? AS distance FROM %s",
                distanceOp, sanitizeIdentifier(tableName)));

        if (filter != null && !filter.isBlank()) {
            sqlBuilder.append(" WHERE ").append(filter);
        }

        sqlBuilder.append(String.format(" ORDER BY embedding %s ? LIMIT ?", distanceOp));

        try (Connection conn = getEndpoint().getDataSource().getConnection()) {
            PGvector.addVectorType(conn);
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {
                int paramIdx = 1;
                PGvector pgVector = new PGvector(vectorArray);
                pstmt.setObject(paramIdx++, pgVector);

                // Bind filter parameters if provided
                if (filter != null && !filter.isBlank() && filterParams != null) {
                    for (Object param : filterParams) {
                        pstmt.setObject(paramIdx++, param);
                    }
                }

                pstmt.setObject(paramIdx++, pgVector);
                pstmt.setInt(paramIdx, topK);

                try (ResultSet rs = pstmt.executeQuery()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("text_content", rs.getString("text_content"));
                        row.put("metadata", rs.getString("metadata"));
                        row.put("distance", rs.getDouble("distance"));
                        results.add(row);
                    }
                    exchange.getMessage().setBody(results);
                }
            }
        }
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private static float[] toFloatArray(List<Float> vector) {
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            Float value = vector.get(i);
            if (value == null) {
                throw new IllegalArgumentException("Vector contains null value at index " + i);
            }
            result[i] = value;
        }
        return result;
    }

    private static String sanitizeIdentifier(String identifier) {
        if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }
}
