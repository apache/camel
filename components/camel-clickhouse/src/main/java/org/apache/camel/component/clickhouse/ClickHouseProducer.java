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
package org.apache.camel.component.clickhouse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickHouseProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseProducer.class);

    private final ClickHouseEndpoint endpoint;

    public ClickHouseProducer(ClickHouseEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ClickHouseOperation operation = resolveOperation(exchange);
        switch (operation) {
            case INSERT -> doInsert(exchange);
            case QUERY -> doQuery(exchange);
            case PING -> doPing(exchange);
            default -> throw new ClickHouseException("Unsupported operation: " + operation);
        }
    }

    private void doInsert(Exchange exchange) throws Exception {
        Client client = endpoint.getClient();
        String target = resolveTable(exchange);
        if (ObjectHelper.isEmpty(target)) {
            throw new ClickHouseException(
                    "A table is required for insert operations. Provide it via the URI path (database.table),"
                                          + " the table option, or the " + ClickHouseConstants.TABLE + " header.");
        }

        InsertSettings settings = new InsertSettings();
        String database = resolveDatabase(exchange);
        if (ObjectHelper.isNotEmpty(database)) {
            settings.setDatabase(database);
        }
        if (endpoint.isAsyncInsert()) {
            settings.serverSetting("async_insert", "1");
            settings.serverSetting("wait_for_async_insert", endpoint.isWaitForAsyncInsert() ? "1" : "0");
        }

        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile<?> wrappedFile) {
            body = wrappedFile.getBody();
        }
        long writtenRows;
        if (body instanceof List) {
            List<?> data = exchange.getIn().getBody(List.class);
            writtenRows = insertList(client, target, data, settings);
        } else {
            InputStream stream = null;
            boolean createdStream = false;
            try {
                if (body instanceof InputStream is) {
                    stream = is;
                } else if (body instanceof byte[] bytes) {
                    stream = new ByteArrayInputStream(bytes);
                    createdStream = true;
                } else if (body instanceof File file) {
                    stream = new FileInputStream(file);
                    createdStream = true;
                } else if (body instanceof String str) {
                    stream = new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
                    createdStream = true;
                } else {
                    stream = exchange.getIn().getMandatoryBody(InputStream.class);
                }

                ClickHouseFormat format = resolveFormat(exchange);
                try (InsertResponse response = client.insert(target, stream, format, settings).get()) {
                    writtenRows = response.getWrittenRows();
                }
            } finally {
                if (createdStream) {
                    IOHelper.close(stream);
                }
            }
        }

        LOG.debug("Inserted {} rows into {}", writtenRows, target);
        exchange.getMessage().setHeader(ClickHouseConstants.WRITTEN_ROWS, writtenRows);
    }

    private long insertList(Client client, String target, List<?> data, InsertSettings settings) throws Exception {
        int batchSize = endpoint.getBatchSize();
        if (batchSize <= 0 || data.size() <= batchSize) {
            try (InsertResponse response = client.insert(target, data, settings).get()) {
                return response.getWrittenRows();
            }
        }

        // split the list into batches of batchSize, inserting each batch separately
        long writtenRows = 0;
        for (int start = 0; start < data.size(); start += batchSize) {
            List<?> batch = data.subList(start, Math.min(start + batchSize, data.size()));
            try (InsertResponse response = client.insert(target, batch, settings).get()) {
                writtenRows += response.getWrittenRows();
            }
        }
        return writtenRows;
    }

    private void doQuery(Exchange exchange) throws Exception {
        Client client = endpoint.getClient();
        String sql = exchange.getIn().getBody(String.class);
        if (ObjectHelper.isEmpty(sql)) {
            throw new ClickHouseException("The SQL query must be provided in the message body for the query operation");
        }

        QuerySettings settings = new QuerySettings();
        settings.setFormat(resolveFormat(exchange));
        String database = resolveDatabase(exchange);
        if (ObjectHelper.isNotEmpty(database)) {
            settings.setDatabase(database);
        }

        try (QueryResponse response = client.query(sql, settings).get()) {
            String result;
            try (InputStream is = response.getInputStream()) {
                result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            Message message = exchange.getMessage();
            message.setHeader(ClickHouseConstants.READ_ROWS, response.getReadRows());
            message.setBody(result);
        }
    }

    private void doPing(Exchange exchange) {
        boolean ok = endpoint.getClient().ping();
        Message message = exchange.getMessage();
        message.setHeader(ClickHouseConstants.PING_OK, ok);
        message.setBody(ok);
    }

    private ClickHouseOperation resolveOperation(Exchange exchange) {
        String header = exchange.getIn().getHeader(ClickHouseConstants.OPERATION, String.class);
        if (ObjectHelper.isNotEmpty(header)) {
            return ClickHouseOperation.valueOf(header.trim().toUpperCase());
        }
        return endpoint.getOperation();
    }

    private ClickHouseFormat resolveFormat(Exchange exchange) {
        String format = exchange.getIn().getHeader(ClickHouseConstants.FORMAT, String.class);
        if (ObjectHelper.isEmpty(format)) {
            format = endpoint.getFormat();
        }
        return ClickHouseFormat.valueOf(format);
    }

    private String resolveDatabase(Exchange exchange) {
        String database = exchange.getIn().getHeader(ClickHouseConstants.DATABASE, String.class);
        if (ObjectHelper.isNotEmpty(database)) {
            return database;
        }
        return endpoint.getDatabase();
    }

    private String resolveTable(Exchange exchange) {
        String table = exchange.getIn().getHeader(ClickHouseConstants.TABLE, String.class);
        if (ObjectHelper.isNotEmpty(table)) {
            return table;
        }
        return endpoint.getTable();
    }
}
