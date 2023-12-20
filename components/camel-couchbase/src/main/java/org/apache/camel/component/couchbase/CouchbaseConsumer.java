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

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JsonNode;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.view.ViewOptions;
import com.couchbase.client.java.view.ViewOrdering;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.DefaultScheduledPollConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_RESUME_ACTION;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_DESIGN_DOCUMENT_NAME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_KEY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_VIEWNAME;

public class CouchbaseConsumer extends DefaultScheduledPollConsumer implements ResumeAware<ResumeStrategy> {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseConsumer.class);

    private final CouchbaseEndpoint endpoint;
    private final Bucket bucket;
    private final Collection collection;
    private ViewOptions viewOptions;

    private ResumeStrategy resumeStrategy;

    public CouchbaseConsumer(CouchbaseEndpoint endpoint, Bucket client, Processor processor) {
        super(endpoint, processor);
        this.bucket = client;
        this.endpoint = endpoint;
        Scope scope;
        if (endpoint.getScope() != null) {
            scope = client.scope(endpoint.getScope());
        } else {
            scope = client.defaultScope();
        }

        if (endpoint.getCollection() != null) {
            this.collection = scope.collection(endpoint.getCollection());
        } else {
            this.collection = client.defaultCollection();
        }
    }

    @Override
    protected void doInit() {
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
    protected synchronized int poll() throws Exception {
        ViewResult result = bucket.viewQuery(endpoint.getDesignDocumentName(), endpoint.getViewName(), this.viewOptions);

        // okay we have some response from CouchBase so lets mark the consumer as ready
        forceConsumerAsReady();

        if (LOG.isTraceEnabled()) {
            LOG.trace("ViewResponse =  {}", result);
        }

        String consumerProcessedStrategy = endpoint.getConsumerProcessedStrategy();
        for (ViewRow row : result.rows()) {
            Object doc;
            String id = row.id().get();
            if (endpoint.isFullDocument()) {
                doc = CouchbaseCollectionOperation.getDocument(collection, id, endpoint.getQueryTimeout());
            } else {
                doc = row.valueAs(Object.class);
            }

            String key = row.keyAs(JsonNode.class).get().asText();
            String designDocumentName = endpoint.getDesignDocumentName();
            String viewName = endpoint.getViewName();

            Exchange exchange = createExchange(false);
            try {
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
                            endpoint.getProducerRetryPause());
                } else if ("filter".equalsIgnoreCase(consumerProcessedStrategy)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Filtering out ID {}", id);
                    }
                    // add filter for already processed docs
                } else {
                    LOG.trace("No strategy set for already processed docs, beware of duplicates!");
                }

                logDetails(id, doc, key, designDocumentName, viewName, exchange);

                getProcessor().process(exchange);
            } catch (Exception e) {
                this.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
            } finally {
                releaseExchange(exchange, false);
            }
        }

        return result.rows().size();
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
