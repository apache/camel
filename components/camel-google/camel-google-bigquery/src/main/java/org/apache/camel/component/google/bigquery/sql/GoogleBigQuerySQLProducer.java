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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobException;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic BigQuery Producer
 */
public class GoogleBigQuerySQLProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBigQuerySQLProducer.class);

    private final GoogleBigQuerySQLConfiguration configuration;
    private BigQuery bigquery;
    private String query;
    private Set<String> queryParameterNames;

    public GoogleBigQuerySQLProducer(BigQuery bigquery, GoogleBigQuerySQLEndpoint endpoint,
                                     GoogleBigQuerySQLConfiguration configuration) {
        super(endpoint);
        this.bigquery = bigquery;
        this.configuration = configuration;
    }

    /**
     * Process the exchange The incoming exchange can be a grouped exchange in which case all the exchanges will be
     * combined. The incoming can be
     * <ul>
     * <li>A map where all map keys will map to field records. One map object maps to one bigquery row</li>
     * <li>A list of maps. Each entry in the list will map to one bigquery row</li>
     * </ul>
     * The incoming message is expected to be a List of Maps The assumptions: - All incoming records go into the same
     * table - Incoming records sorted by the timestamp
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String translatedQuery = SqlHelper.translateQuery(query, exchange);
        Map<String, Object> queryParameters = extractParameters(exchange);

        Message message = exchange.getMessage();
        message.setHeader(GoogleBigQueryConstants.TRANSLATED_QUERY, translatedQuery);
        JobId jobId = message.getHeader(GoogleBigQueryConstants.JOB_ID, JobId.class);

        Long affectedRows = executeSQL(jobId, translatedQuery, queryParameters);

        LOG.debug("The query {} affected {} rows", query, affectedRows);
        message.setBody(affectedRows);
    }

    private Long executeSQL(JobId jobId, String translatedQuery, Map<String, Object> queryParameters) throws Exception {
        QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(translatedQuery)
                .setUseLegacySql(false);

        setQueryParameters(queryParameters, builder);

        QueryJobConfiguration queryJobConfiguration = builder.build();

        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Sending query to bigquery standard sql: {}", translatedQuery);
            }

            JobId queryJobId;
            if (ObjectHelper.isNotEmpty(jobId)) {
                queryJobId = jobId;
            } else {
                queryJobId = JobId.of(configuration.getProjectId(), UUID.randomUUID().toString());
            }

            Job job = bigquery.create(JobInfo.of(queryJobId, queryJobConfiguration)).waitFor();
            JobStatistics.QueryStatistics statistics = job.getStatistics();
            TableResult result = job.getQueryResults();
            Long numAffectedRows = statistics.getNumDmlAffectedRows();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Query {} - Affected rows {} - Result {}", translatedQuery, numAffectedRows, result);
            }

            //numAffectedRows is present only for DML statements INSERT, UPDATE or DELETE.
            if (numAffectedRows != null) {
                return numAffectedRows;
            }
            //in other cases (SELECT), the number of affected rows is returned
            return result.getTotalRows();
        } catch (JobException e) {
            throw new Exception("Query " + translatedQuery + " failed: " + e.getErrors(), e);
        } catch (BigQueryException e) {
            throw new Exception("Query " + translatedQuery + " failed: " + e.getError(), e);
        }
    }

    private Map<String, Object> extractParameters(Exchange exchange) {
        if (queryParameterNames == null || queryParameterNames.isEmpty()) {
            return null;
        }

        Message message = exchange.getMessage();

        HashMap<String, Object> headers = new HashMap<>(message.getHeaders());
        if (message.getBody() instanceof Map) {
            try {
                headers.putAll(message.getBody(Map.class));
            } catch (ClassCastException e) {
                LOG.warn("Unable to perform cast while extracting header parameters: {}", e.getMessage(), e);
            }
        }

        HashMap<String, Object> result = new HashMap<>(queryParameterNames.size());
        queryParameterNames.forEach(s -> {
            Object value = headers.get(s);
            if (value == null) {
                throw new RuntimeExchangeException(
                        "SQL parameter with name '" + s + "' not found in the message headers", exchange);
            }

            result.put(s, headers.get(s));
        });

        return result;
    }

    private void setQueryParameters(Map<String, Object> params, QueryJobConfiguration.Builder builder) {
        if (params == null) {
            return;
        }

        params.forEach((key, value) -> {
            QueryParameterValue parameterValue;

            try {
                parameterValue = QueryParameterValue.of(value, (Class<Object>) value.getClass());
            } catch (IllegalArgumentException e) {
                LOG.warn("{} Fallback to *.toString() value.", e.getMessage());
                //use String representation
                parameterValue = QueryParameterValue.of(value.toString(), StandardSQLTypeName.STRING);
            }
            builder.addNamedParameter(key, parameterValue);
        });
    }

    @Override
    public GoogleBigQuerySQLEndpoint getEndpoint() {
        return (GoogleBigQuerySQLEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String placeholder = ":#";
        query = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), configuration.getQueryString(), placeholder);
        queryParameterNames = SqlHelper.extractParameterNames(query);
    }
}
