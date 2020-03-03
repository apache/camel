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
package org.apache.camel.component.google.bigquery.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.QueryParameter;
import com.google.api.services.bigquery.model.QueryParameterType;
import com.google.api.services.bigquery.model.QueryParameterValue;
import com.google.api.services.bigquery.model.QueryRequest;
import com.google.api.services.bigquery.model.QueryResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic BigQuery Producer
 */
public class GoogleBigQuerySQLProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBigQuerySQLProducer.class);

    private final GoogleBigQuerySQLConfiguration configuration;
    private Bigquery bigquery;
    private String query;
    private Set<String> queryParameterNames;

    public GoogleBigQuerySQLProducer(Bigquery bigquery, GoogleBigQuerySQLEndpoint endpoint, GoogleBigQuerySQLConfiguration configuration) {
        super(endpoint);
        this.bigquery = bigquery;
        this.configuration = configuration;
    }

    /**
     * Process the exchange The incoming exchange can be a grouped exchange in
     * which case all the exchanges will be combined. The incoming can be
     * <ul>
     * <li>A map where all map keys will map to field records. One map object
     * maps to one bigquery row</li>
     * <li>A list of maps. Each entry in the list will map to one bigquery
     * row</li>
     * </ul>
     * The incoming message is expected to be a List of Maps The assumptions: -
     * All incoming records go into the same table - Incoming records sorted by
     * the timestamp
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String translatedQuery = SqlHelper.translateQuery(query, exchange);
        Map<String, Object> queryParameters = extractParameters(exchange);
        exchange.getMessage().setHeader(GoogleBigQueryConstants.TRANSLATED_QUERY, translatedQuery);
        Long affectedRows = executeSQL(translatedQuery, queryParameters);
        LOG.debug("The query {} affected {} rows", query, affectedRows);
        exchange.getMessage().setBody(affectedRows);
    }

    private Long executeSQL(String translatedQuery, Map<String, Object> queryParameters) throws Exception {
        QueryRequest apiQueryRequest = new QueryRequest().setQuery(translatedQuery).setUseLegacySql(false);

        Bigquery.Jobs.Query apiQuery = bigquery.jobs().query(configuration.getProjectId(), apiQueryRequest);

        setQueryParameters(queryParameters, apiQueryRequest);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending query to bigquery standard sql: {}", translatedQuery);
        }

        QueryResponse apiResponse = apiQuery.execute();

        if (apiResponse.getErrors() != null && !apiResponse.getErrors().isEmpty()) {
            throw new Exception("Query " + translatedQuery + " failed: " + apiResponse.getErrors());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Result of query {} is {}", translatedQuery, apiResponse.toPrettyString());
        }
        return apiResponse.getNumDmlAffectedRows();
    }

    private Map<String, Object> extractParameters(Exchange exchange) {
        if (queryParameterNames == null || queryParameterNames.size() == 0) {
            return null;
        }

        Message message = exchange.getMessage();

        HashMap<String, Object> headers = new HashMap<>(message.getHeaders());
        if (message.getBody() instanceof Map) {
            try {
                headers.putAll(message.getBody(Map.class));
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        }

        HashMap<String, Object> result = new HashMap<>(queryParameterNames.size());
        queryParameterNames.forEach(s -> {
            Object value = headers.get(s);
            if (value == null) {
                throw new RuntimeExchangeException("SQL parameter with name '" + s + "' not found in the message headers", exchange);
            }

            result.put(s, headers.get(s));
        });

        return result;
    }

    private void setQueryParameters(Map<String, Object> params, QueryRequest apiQueryRequest) {
        if (params == null) {
            return;
        }

        List<QueryParameter> list = new ArrayList<>();
        params.forEach((key, value) -> {
            QueryParameter param = new QueryParameter();
            param.setName(key).setParameterType(new QueryParameterType().setType("STRING")).setParameterValue(new QueryParameterValue().setValue(value.toString()));
            list.add(param);
        });
        apiQueryRequest.setQueryParameters(list);
    }

    @Override
    public GoogleBigQuerySQLEndpoint getEndpoint() {
        return (GoogleBigQuerySQLEndpoint)super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String placeholder = ":#"; // TODO
        query = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), configuration.getQuery(), placeholder);
        queryParameterNames = SqlHelper.extractParameterNames(query);
    }
}
