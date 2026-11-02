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
package org.apache.camel.component.google.bigquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.client.util.Strings;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic BigQuery Producer
 */
public class GoogleBigQueryProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBigQueryProducer.class);

    private final GoogleBigQueryConfiguration configuration;
    private BigQuery bigquery;

    public GoogleBigQueryProducer(BigQuery bigquery, GoogleBigQueryEndpoint endpoint,
                                  GoogleBigQueryConfiguration configuration) {
        super(endpoint);
        this.bigquery = bigquery;
        this.configuration = configuration;
    }

    /**
     * The method converts a single incoming message into a List
     */
    private static List<Exchange> prepareExchangeList(Exchange exchange) {
        List<Exchange> entryList;

        if (null == exchange.getProperty(ExchangePropertyKey.GROUPED_EXCHANGE)) {
            entryList = new ArrayList<>();
            entryList.add(exchange);
        } else {
            entryList = (List<Exchange>) exchange.getProperty(ExchangePropertyKey.GROUPED_EXCHANGE);
        }

        return entryList;
    }

    /**
     * Process the exchange
     *
     * The incoming exchange can be a grouped exchange in which case all the exchanges will be combined. When the body
     * of one of them is not a row, the exchange fails and no row is inserted.
     *
     * The incoming can be
     * <ul>
     * <li>A map where all map keys will map to field records. One map object maps to one bigquery row</li>
     * <li>A list of maps. Each entry in the list will map to one bigquery row</li>
     * </ul>
     * The incoming message is expected to be a List of Maps The assumptions: - All incoming records go into the same
     * table - Incoming records sorted by the timestamp
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        List<Exchange> exchanges = prepareExchangeList(exchange);

        // a body that is not a row fails the exchange being processed before any row is sent: the exception of one
        // exchange of a grouped exchange would not be seen, and the other rows would be inserted without it
        IllegalArgumentException invalidBody = checkBodies(exchange, exchanges);
        if (invalidBody != null) {
            exchange.setException(invalidBody);
            return;
        }
        // the rows go in one request per table, suffix and partition, so an exchange without a table must fail the
        // exchange before the rows of the tables before it are sent
        checkTableIds(exchange, exchanges);

        List<Exchange> processGroup = new ArrayList<>();

        String partitionDecorator = "";
        String suffix = "";
        String tableId = configuration.getTableId() == null ? "" : configuration.getTableId();
        int totalProcessed = 0;

        for (Exchange ex : exchanges) {
            String tmpPartitionDecorator = ex.getIn().getHeader(GoogleBigQueryConstants.PARTITION_DECORATOR, "", String.class);
            String tmpSuffix = ex.getIn().getHeader(GoogleBigQueryConstants.TABLE_SUFFIX, "", String.class);
            // never empty: checkTableIds has failed the exchange for that
            String tmpTableId = ex.getIn().getHeader(GoogleBigQueryConstants.TABLE_ID, tableId, String.class);

            // Ensure all rows of same request goes to same table and suffix
            if (!tmpPartitionDecorator.equals(partitionDecorator) || !tmpSuffix.equals(suffix) || !tmpTableId.equals(tableId)) {
                if (!processGroup.isEmpty()) {
                    totalProcessed += process(tableId, partitionDecorator, suffix, processGroup, exchange.getExchangeId());
                }
                processGroup.clear();
                partitionDecorator = tmpPartitionDecorator;
                suffix = tmpSuffix;
                tableId = tmpTableId;
            }
            processGroup.add(ex);
        }
        if (!processGroup.isEmpty()) {
            totalProcessed += process(tableId, partitionDecorator, suffix, processGroup, exchange.getExchangeId());
        }

        if (totalProcessed == 0) {
            LOG.debug("The incoming message is either null or empty for exchange {}", exchange.getExchangeId());
        }
    }

    /**
     * The failure for the first body that is not a Map or a List of Maps, or null when all the bodies are rows.
     */
    private static IllegalArgumentException checkBodies(Exchange exchange, List<Exchange> exchanges) {
        for (int i = 0; i < exchanges.size(); i++) {
            Exchange ex = exchanges.get(i);
            Object body = ex.getIn().getBody();
            String problem = null;
            if (body instanceof List<?> rows) {
                // each element is cast to a Map when the request of its table is built, which may be after the
                // requests of other tables were sent
                for (Object row : rows) {
                    if (!(row instanceof Map)) {
                        problem = "Cannot handle a List element of type " + (row == null ? "null" : row.getClass().getName());
                        break;
                    }
                }
            } else if (!(body instanceof Map)) {
                problem = "Cannot handle body type " + (body == null ? "null" : body.getClass().getName());
            }
            if (problem != null) {
                return new IllegalArgumentException(problem + inGroupedExchange(exchange, ex, i, exchanges.size()));
            }
        }
        return null;
    }

    /**
     * Fails when an exchange has no table: neither the endpoint nor the table id header gives one. An exchange without
     * the header goes to the table of the exchange before it, as in process.
     */
    private void checkTableIds(Exchange exchange, List<Exchange> exchanges) {
        String tableId = configuration.getTableId() == null ? "" : configuration.getTableId();
        for (int i = 0; i < exchanges.size(); i++) {
            Exchange ex = exchanges.get(i);
            tableId = ex.getIn().getHeader(GoogleBigQueryConstants.TABLE_ID, tableId, String.class);
            if (tableId.isEmpty()) {
                throw new IllegalArgumentException(
                        "tableId need to be specified in one of endpoint configuration or exchange header"
                                                   + inGroupedExchange(exchange, ex, i, exchanges.size()));
            }
        }
    }

    /**
     * Which exchange of a grouped exchange failed, and that no row of the group is inserted; empty for an exchange that
     * is not grouped.
     */
    private static String inGroupedExchange(Exchange exchange, Exchange failed, int index, int size) {
        return failed != exchange
                ? " (exchange " + (index + 1) + " of " + size + " of the grouped exchange, so no row of the group is inserted)"
                : "";
    }

    private int process(String tableId, String partitionDecorator, String suffix, List<Exchange> exchanges, String exchangeId)
            throws Exception {
        String tableIdWithPartition = Strings.isNullOrEmpty(partitionDecorator)
                ? tableId
                : (tableId + "$" + partitionDecorator);

        List<InsertAllRequest.RowToInsert> apiRequestRows = new ArrayList<>();
        for (Exchange ex : exchanges) {
            Object entryObject = ex.getIn().getBody();
            if (entryObject instanceof List) {
                // the insert id header identifies one row, so it is not applied to the rows of a list: BigQuery
                // would treat them as duplicates of each other and keep only one. Use useAsInsertId for those
                for (Map<String, Object> entry : (List<Map<String, Object>>) entryObject) {
                    apiRequestRows.add(createRowRequest(null, entry));
                }
            } else {
                // a Map: checkBodies has failed the exchange for any other body
                apiRequestRows.add(createRowRequest(ex, (Map<String, Object>) entryObject));
            }
        }

        if (apiRequestRows.isEmpty()) {
            return 0;
        }

        InsertAllRequest.Builder builder = InsertAllRequest.newBuilder(configuration.getDatasetId(), tableIdWithPartition)
                .setRows(apiRequestRows);

        if (ObjectHelper.isNotEmpty(suffix)) {
            builder.setTemplateSuffix(suffix);
        }

        InsertAllRequest insertAllRequest = builder.build();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending {} messages to bigquery table {}, suffix {}, partition {}",
                    apiRequestRows.size(), tableId, suffix, partitionDecorator);
        }

        InsertAllResponse apiResponse = bigquery.insertAll(insertAllRequest);

        if (apiResponse.getInsertErrors() != null && !apiResponse.getInsertErrors().isEmpty()) {
            throw new RuntimeCamelException("InsertAll into " + tableId + " failed: " + apiResponse.getInsertErrors());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Sent {} messages to bigquery table {}, suffix {}, partition {}",
                    apiRequestRows.size(), tableId, suffix, partitionDecorator);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("uploader thread/id: {} / {} . api call completed.", Thread.currentThread().threadId(), exchangeId);
        }
        return insertAllRequest.getRows().size();
    }

    private InsertAllRequest.RowToInsert createRowRequest(Exchange exchange, Map<String, Object> object) {
        String insertId = null;
        if (configuration.getUseAsInsertId() != null) {
            insertId = (String) (object.get(configuration.getUseAsInsertId()));
        } else {
            if (exchange != null) {
                insertId = exchange.getIn().getHeader(GoogleBigQueryConstants.INSERT_ID, String.class);
            }
        }
        if (insertId != null) {
            return InsertAllRequest.RowToInsert.of(insertId, object);
        }
        return InsertAllRequest.RowToInsert.of(object);
    }

    @Override
    public GoogleBigQueryEndpoint getEndpoint() {
        return (GoogleBigQueryEndpoint) super.getEndpoint();
    }

}
