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
package org.apache.camel.component.couchbase;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.couchbase.client.java.view.ViewOptions;
import com.couchbase.client.java.view.ViewOrdering;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_RESUME_ACTION;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_DESIGN_DOCUMENT_NAME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_KEY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_VIEWNAME;

public class CouchbaseConsumer extends ScheduledBatchPollingConsumer implements ResumeAware<ResumeStrategy> {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseConsumer.class);

    static final String SQL_DOCUMENT_ID_ALIAS = "__id";

    private final Lock lock = new ReentrantLock();
    private final CouchbaseEndpoint endpoint;
    private Bucket bucket;
    private Scope scope;
    private Collection collection;
    private ViewOptions viewOptions;
    private boolean useSqlQuery;
    private String sqlStatement;

    private ResumeStrategy resumeStrategy;

    public CouchbaseConsumer(CouchbaseEndpoint endpoint, Bucket client, Processor processor) {
        super(endpoint, processor);
        this.bucket = client;
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (endpoint.getScope() != null) {
            this.scope = bucket.scope(endpoint.getScope());
        } else {
            this.scope = bucket.defaultScope();
        }

        if (endpoint.getCollection() != null) {
            this.collection = scope.collection(endpoint.getCollection());
        } else {
            this.collection = bucket.defaultCollection();
        }

        // Determine query mode
        if (endpoint.getStatement() != null) {
            // Explicit SQL++ statement provided
            useSqlQuery = true;
            sqlStatement = endpoint.getStatement();
        } else if (!endpoint.isUseView()) {
            // Auto-generate SQL++ from endpoint options
            useSqlQuery = true;
            sqlStatement = endpoint.buildSqlQuery();
            LOG.info("Auto-generated SQL++ query: {}", sqlStatement);
        } else {
            // Use deprecated MapReduce views
            useSqlQuery = false;
            initViewOptions();
        }
    }

    @SuppressWarnings("deprecation")
    private void initViewOptions() {
        this.viewOptions = ViewOptions.viewOptions();
        int limit = endpoint.getLimit();
        if (limit > 0) {
            viewOptions.limit(limit);
        }

        int skip = endpoint.getSkip();
        if (skip > 0) {
            viewOptions.skip(skip);
        }

        if (endpoint.isDescending()) {
            viewOptions.order(ViewOrdering.DESCENDING);
        }

        String rangeStartKey = endpoint.getRangeStartKey();
        String rangeEndKey = endpoint.getRangeEndKey();
        if (rangeStartKey == null || rangeStartKey.isEmpty() || rangeEndKey == null || rangeEndKey.isEmpty()) {
            return;
        }
        viewOptions.startKey(rangeStartKey).endKey(rangeEndKey);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ResumeStrategyHelper.resume(getEndpoint().getCamelContext(), this, resumeStrategy, COUCHBASE_RESUME_ACTION);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (bucket != null) {
            bucket.core().shutdown();
        }
    }

    @Override
    protected int poll() throws Exception {
        lock.lock();
        try {
            if (useSqlQuery) {
                return pollWithSqlQuery();
            } else {
                return pollWithView();
            }
        } finally {
            lock.unlock();
        }
    }

    private int pollWithSqlQuery() throws Exception {
        QueryOptions queryOptions = QueryOptions.queryOptions()
                .scanConsistency(QueryScanConsistency.REQUEST_PLUS);

        QueryResult result = scope.query(sqlStatement, queryOptions);

        forceConsumerAsReady();

        if (LOG.isTraceEnabled()) {
            LOG.trace("QueryResult: {}", result);
        }

        String consumerProcessedStrategy = endpoint.getConsumerProcessedStrategy();
        Queue<Object> exchanges = new ArrayDeque<>();

        for (JsonObject row : result.rowsAsObject()) {
            String id = row.getString(SQL_DOCUMENT_ID_ALIAS);
            if (id == null) {
                LOG.warn("Row does not contain '{}' field. "
                         + "Ensure your SQL++ query includes META().id AS {} in the SELECT clause. Skipping row.",
                        SQL_DOCUMENT_ID_ALIAS, SQL_DOCUMENT_ID_ALIAS);
                continue;
            }

            Object doc;
            if (endpoint.isFullDocument()) {
                doc = CouchbaseCollectionOperation.getDocument(collection, id, endpoint.getQueryTimeout(),
                        endpoint.getConsumerRetryPause());
            } else {
                doc = row.toString();
            }

            Exchange exchange = createExchange(true);
            exchange.getIn().setBody(doc);
            exchange.getIn().setHeader(HEADER_ID, id);

            if ("delete".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting doc with ID {}", id);
                }
                CouchbaseCollectionOperation.removeDocument(collection, id, endpoint.getWriteQueryTimeout(),
                        endpoint.getConsumerRetryPause());
            } else if ("filter".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Filtering out ID {}", id);
                }
            } else {
                LOG.trace("No strategy set for already processed docs, beware of duplicates!");
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Created exchange = {}", exchange);
                LOG.trace("Added Document in body = {}", doc);
                LOG.trace("ID = {}", id);
            }

            exchanges.add(exchange);
        }

        return processBatch(exchanges);
    }

    @SuppressWarnings("deprecation")
    private int pollWithView() throws Exception {
        ViewResult result = bucket.viewQuery(endpoint.getDesignDocumentName(), endpoint.getViewName(), this.viewOptions);

        // okay we have some response from CouchBase so lets mark the consumer as ready
        forceConsumerAsReady();

        if (LOG.isTraceEnabled()) {
            LOG.trace("ViewResponse: {}", result);
        }

        String consumerProcessedStrategy = endpoint.getConsumerProcessedStrategy();

        Queue<Object> exchanges = new ArrayDeque<>();
        for (ViewRow row : result.rows()) {
            Object doc;
            String id = row.id().get();
            if (endpoint.isFullDocument()) {
                doc = CouchbaseCollectionOperation.getDocument(collection, id, endpoint.getQueryTimeout(),
                        endpoint.getConsumerRetryPause());
            } else {
                doc = row.valueAs(Object.class);
            }

            // Use String.class instead of the shaded JsonNode class to avoid conflicts
            // when Jackson is on the classpath (CAMEL-22090). The Couchbase SDK's
            // auto-detection of non-shaded Jackson would otherwise cause deserialization
            // failures when trying to deserialize into the shaded JsonNode class.
            String key = row.keyAs(String.class).orElse(null);
            String designDocumentName = endpoint.getDesignDocumentName();
            String viewName = endpoint.getViewName();

            Exchange exchange = createExchange(true);
            exchange.getIn().setBody(doc);
            exchange.getIn().setHeader(HEADER_ID, id);
            exchange.getIn().setHeader(HEADER_KEY, key);
            exchange.getIn().setHeader(HEADER_DESIGN_DOCUMENT_NAME, designDocumentName);
            exchange.getIn().setHeader(HEADER_VIEWNAME, viewName);

            if ("delete".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting doc with ID {}", id);
                }
                CouchbaseCollectionOperation.removeDocument(collection, id, endpoint.getWriteQueryTimeout(),
                        endpoint.getConsumerRetryPause());
            } else if ("filter".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Filtering out ID {}", id);
                }
                // add filter for already processed docs
            } else {
                LOG.trace("No strategy set for already processed docs, beware of duplicates!");
            }

            logDetails(id, doc, key, designDocumentName, viewName, exchange);
            exchanges.add(exchange);
        }

        return processBatch(exchanges);
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        int answer = total;
        if (this.maxMessagesPerPoll > 0 && total > this.maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll {} as there were {} messages in this poll.",
                    this.maxMessagesPerPoll, total);
            total = this.maxMessagesPerPoll;
        }

        for (int index = 0; index < total && this.isBatchAllowed(); ++index) {
            Exchange exchange = (Exchange) exchanges.poll();
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);
            this.pendingExchanges = total - index - 1;
            getProcessor().process(exchange);
        }

        return answer;
    }

    private void logDetails(String id, Object doc, String key, String designDocumentName, String viewName, Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Created exchange = {}", exchange);
            LOG.trace("Added Document in body = {}", doc);
            LOG.trace("Adding to Header");
            LOG.trace("ID = {}", id);
            LOG.trace("Key = {}", key);
            LOG.trace("Design Document Name = {}", designDocumentName);
            LOG.trace("View Name = {}", viewName);
        }
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }
}
