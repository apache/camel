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
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic BigQuery Producer
 */
public class GoogleBigQueryProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBigQueryProducer.class);

    private final GoogleBigQueryConfiguration configuration;
    private Bigquery bigquery;

    public GoogleBigQueryProducer(Bigquery bigquery, GoogleBigQueryEndpoint endpoint, GoogleBigQueryConfiguration configuration) {
        super(endpoint);
        this.bigquery = bigquery;
        this.configuration = configuration;
    }

    /**
     * The method converts a single incoming message into a List
     */
    private static List<Exchange> prepareExchangeList(Exchange exchange) {
        List<Exchange> entryList;

        if (null == exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
            entryList = new ArrayList<>();
            entryList.add(exchange);
        } else {
            entryList = (List<Exchange>) exchange.getProperty(Exchange.GROUPED_EXCHANGE);
        }

        return entryList;
    }

    /**
     * Process the exchange
     *
     * The incoming exchange can be a grouped exchange in which case all the exchanges will be combined.
     *
     * The incoming can be
     * <ul>
     *     <li>A map where all map keys will map to field records. One map object maps to one bigquery row</li>
     *     <li>A list of maps. Each entry in the list will map to one bigquery row</li>
     * </ul>
     * The incoming message is expected to be a List of Maps
     * The assumptions:
     * - All incoming records go into the same table
     * - Incoming records sorted by the timestamp
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        List<Exchange> exchanges = prepareExchangeList(exchange);

        List<Exchange> processGroup = new ArrayList<>();

        String partitionDecorator = "";
        String suffix = "";
        String tableId = configuration.getTableId() == null ? "" : configuration.getTableId();
        int totalProcessed = 0;

        for (Exchange ex: exchanges) {
            String tmpPartitionDecorator = ex.getIn().getHeader(GoogleBigQueryConstants.PARTITION_DECORATOR, "", String.class);
            String tmpSuffix = ex.getIn().getHeader(GoogleBigQueryConstants.TABLE_SUFFIX, "", String.class);
            String tmpTableId = ex.getIn().getHeader(GoogleBigQueryConstants.TABLE_ID, tableId, String.class);

            if (tmpTableId.isEmpty()) {
                throw new IllegalArgumentException("tableId need to be specified in one of endpoint configuration or exchange header");
            }

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

    private int process(String tableId, String partitionDecorator, String suffix, List<Exchange> exchanges, String exchangeId) throws Exception {
        String tableIdWithPartition = Strings.isNullOrEmpty(partitionDecorator)
                ? tableId
                : (tableId + "$" + partitionDecorator);

        List<TableDataInsertAllRequest.Rows> apiRequestRows = new ArrayList<>();
        for (Exchange ex: exchanges) {
            Object entryObject = ex.getIn().getBody();
            if (entryObject instanceof List) {
                for (Map<String, Object> entry: (List<Map<String, Object>>) entryObject) {
                    apiRequestRows.add(createRowRequest(null, entry));
                }
            } else if (entryObject instanceof Map) {
                apiRequestRows.add(createRowRequest(ex, (Map<String, Object>) entryObject));
            } else {
                ex.setException(new IllegalArgumentException("Cannot handle body type " + entryObject.getClass()));
            }
        }

        if (apiRequestRows.isEmpty()) {
            return 0;
        }

        TableDataInsertAllRequest apiRequestData = new TableDataInsertAllRequest().setRows(apiRequestRows);

        Bigquery.Tabledata.InsertAll apiRequest = bigquery
                .tabledata()
                .insertAll(configuration.getProjectId(),
                        configuration.getDatasetId(),
                        tableIdWithPartition,
                        apiRequestData);
        if (suffix != null) {
            apiRequest.set("template_suffix", suffix);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending {} messages to bigquery table {}, suffix {}, partition {}",
                    apiRequestRows.size(), tableId, suffix, partitionDecorator);
        }

        TableDataInsertAllResponse apiResponse = apiRequest.execute();

        if (apiResponse.getInsertErrors() != null && !apiResponse.getInsertErrors().isEmpty()) {
            throw new Exception("InsertAll into " + tableId + " failed: " + apiResponse.getInsertErrors());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Sent {} messages to bigquery table {}, suffix {}, partition {}",
                apiRequestRows.size(), tableId, suffix, partitionDecorator);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("uploader thread/id: {} / {} . api call completed.", Thread.currentThread().getId(), exchangeId);
        }
        return apiRequestData.size();
    }

    private TableDataInsertAllRequest.Rows createRowRequest(Exchange exchange, Map<String, Object> object) {
        TableRow tableRow = new TableRow();
        tableRow.putAll(object);
        String insertId = null;
        if (configuration.getUseAsInsertId() != null) {
            insertId = (String)(object.get(configuration.getUseAsInsertId()));
        } else {
            if (exchange != null) {
                insertId = exchange.getIn().getHeader(GoogleBigQueryConstants.INSERT_ID, String.class);
            }
        }
        TableDataInsertAllRequest.Rows rows = new TableDataInsertAllRequest.Rows();
        rows.setInsertId(insertId);
        rows.setJson(tableRow);
        return rows;
    }

    @Override
    public GoogleBigQueryEndpoint getEndpoint() {
        return (GoogleBigQueryEndpoint) super.getEndpoint();
    }

}
