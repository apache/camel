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
import java.util.stream.StreamSupport;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQuery.QueryResultsOption;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.StreamListIterator;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for executing SQL queries against Google BigQuery. Supports both DML (INSERT, UPDATE, DELETE) and SELECT
 * queries with parameterized queries, pagination, and streaming results.
 */
public class GoogleBigQuerySQLProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBigQuerySQLProducer.class);

    private final GoogleBigQuerySQLConfiguration configuration;
    private final BigQuery bigquery;
    private String query;
    private Set<String> queryParameterNames;

    public GoogleBigQuerySQLProducer(
            BigQuery bigquery, GoogleBigQuerySQLEndpoint endpoint, GoogleBigQuerySQLConfiguration configuration) {
        super(endpoint);
        this.bigquery = bigquery;
        this.configuration = configuration;
    }

    /**
     * Processes the exchange by executing a SQL query against BigQuery.
     * <p>
     * Query parameters can be provided via:
     * <ul>
     * <li>Message body as {@code Map<String, Object>}</li>
     * <li>Message headers</li>
     * </ul>
     * <p>
     * For SELECT queries, results are returned based on {@link OutputType}:
     * <ul>
     * <li>SELECT_LIST - {@code List<Map<String, Object>>} with pagination headers</li>
     * <li>STREAM_LIST - {@code Iterator<Map<String, Object>>} for streaming</li>
     * </ul>
     * <p>
     * For DML queries, returns the number of affected rows as {@code Long}.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String translatedQuery = SqlHelper.translateQuery(query, exchange);
        Map<String, Object> queryParameters = extractParameters(exchange);

        Message message = exchange.getMessage();
        message.setHeader(GoogleBigQueryConstants.TRANSLATED_QUERY, translatedQuery);
        JobId jobId = message.getHeader(GoogleBigQueryConstants.JOB_ID, JobId.class);

        Job job = executeJob(jobId, translatedQuery, queryParameters);

        if (isSelectQueryJob(job)) {
            processSelectQueryJob(message, job);
        } else {
            long affectedRows =
                    job.<JobStatistics.QueryStatistics>getStatistics().getNumDmlAffectedRows();
            LOG.debug("The query {} affected {} rows", query, affectedRows);
            message.setBody(affectedRows);
        }
    }

    /**
     * Processes SELECT query results based on the configured output type. Both types use pageSize for fetching results
     * from BigQuery. For SELECT_LIST, loads current page into memory and sets pagination headers. For STREAM_LIST,
     * creates an iterator that automatically fetches pages using pageSize.
     */
    private void processSelectQueryJob(Message message, Job job) throws Exception {
        long pageSize = configuration.getPageSize();
        String pageToken =
                message.getHeader(GoogleBigQueryConstants.PAGE_TOKEN, configuration::getPageToken, String.class);

        TableResult result = getTableResult(job, pageSize, pageToken);
        Schema schema = result.getSchema();

        if (schema == null) {
            LOG.debug("Query result schema is null. Unable to process the result set.");
            message.setBody(result.getTotalRows());
            return;
        }

        switch (configuration.getOutputType()) {
            case SELECT_LIST -> {
                List<Map<String, Object>> rows = processSelectResult(result, schema);
                LOG.debug("The query {} returned {} rows", query, rows.size());
                message.setBody(rows);
                message.setHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN, result.getNextPageToken());
                message.setHeader(GoogleBigQueryConstants.JOB_ID, job.getJobId());
            }
            case STREAM_LIST -> {
                var iterator = new StreamListIterator<>(
                        new FieldValueListMapper(schema.getFields()),
                        result.iterateAll().iterator());
                message.setBody(iterator);
            }
        }
    }

    /**
     * Executes a BigQuery job, either by retrieving an existing job or creating a new one. If jobId is provided,
     * retrieves the existing job; otherwise creates a new query job. Waits for the job to complete before returning.
     */
    private Job executeJob(JobId jobId, String translatedQuery, Map<String, Object> queryParameters) throws Exception {
        QueryJobConfiguration.Builder builder =
                QueryJobConfiguration.newBuilder(translatedQuery).setUseLegacySql(false);

        setQueryParameters(queryParameters, builder);

        QueryJobConfiguration queryJobConfiguration = builder.build();

        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Sending query to bigquery standard sql: {}", translatedQuery);
            }

            var job = ObjectHelper.isNotEmpty(jobId)
                    ? bigquery.getJob(jobId)
                    : bigquery.create(getJobInfo(queryJobConfiguration));

            return job.waitFor();
        } catch (BigQueryException e) {
            throw new Exception("Query " + translatedQuery + " failed: " + e.getError(), e);
        }
    }

    /**
     * Creates JobInfo with a random job ID for the given query configuration.
     */
    private JobInfo getJobInfo(QueryJobConfiguration queryJobConfiguration) {
        return JobInfo.of(
                JobId.newBuilder()
                        .setRandomJob()
                        .setProject(configuration.getProjectId())
                        .build(),
                queryJobConfiguration);
    }

    /**
     * Retrieves query results from a completed job with optional pagination.
     */
    private TableResult getTableResult(Job job, long pageSize, String pageToken) throws Exception {
        String translatedQuery = job.<QueryJobConfiguration>getConfiguration().getQuery();
        try {
            QueryResultsOption[] queryResultsOptions = getQueryResultsOptions(pageSize, pageToken);
            return job.getQueryResults(queryResultsOptions);
        } catch (BigQueryException e) {
            throw new Exception("Query " + translatedQuery + " failed: " + e.getError(), e);
        }
    }

    /**
     * Builds query result options array from pageSize and pageToken. Only includes options with non-default values
     * (pageSize > 0, pageToken != null).
     */
    private static QueryResultsOption[] getQueryResultsOptions(long pageSize, String pageToken) {
        List<QueryResultsOption> options = new ArrayList<>();
        if (pageSize > 0) {
            options.add(QueryResultsOption.pageSize(pageSize));
        }
        if (pageToken != null) {
            options.add(QueryResultsOption.pageToken(pageToken));
        }
        return options.toArray(new QueryResultsOption[0]);
    }

    /**
     * Checks if the job is a SELECT query by examining its statement type.
     */
    private static boolean isSelectQueryJob(Job job) {
        JobStatistics.QueryStatistics statistics = job.getStatistics();
        return statistics.getStatementType().equals(JobStatistics.QueryStatistics.StatementType.SELECT);
    }

    /**
     * Extracts query parameters from exchange headers and body. Parameters are identified by names found in the query
     * (e.g., @paramName). Body values take precedence over header values.
     *
     * @throws RuntimeExchangeException if a required parameter is not found
     */
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
                // use String representation
                parameterValue = QueryParameterValue.of(value.toString(), StandardSQLTypeName.STRING);
            }
            builder.addNamedParameter(key, parameterValue);
        });
    }

    /**
     * Converts TableResult to a list of maps for SELECT_LIST output type. Each map represents one row with field names
     * as keys.
     */
    private List<Map<String, Object>> processSelectResult(TableResult result, Schema schema) {
        var mapper = new FieldValueListMapper(schema.getFields());

        return StreamSupport.stream(result.getValues().spliterator(), false)
                .map(mapper::map)
                .toList();
    }

    @Override
    public GoogleBigQuerySQLEndpoint getEndpoint() {
        return (GoogleBigQuerySQLEndpoint) super.getEndpoint();
    }

    /**
     * Initializes the producer by resolving the query string and extracting parameter names. Query resolution supports
     * file references and variable substitution.
     */
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String placeholder = ":#";
        query = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), configuration.getQueryString(), placeholder);
        queryParameterNames = SqlHelper.extractParameterNames(query);
    }
}
