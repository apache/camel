/**
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

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.protocol.views.Query;
import com.couchbase.client.protocol.views.View;
import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewRow;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultScheduledPollConsumer;

import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_DESIGN_DOCUMENT_NAME;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_KEY;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_VIEWNAME;

public class CouchbaseConsumer extends DefaultScheduledPollConsumer {

    private final CouchbaseEndpoint endpoint;
    private final CouchbaseClient client;
    private final View view;
    private final Query query;

    public CouchbaseConsumer(CouchbaseEndpoint endpoint, CouchbaseClient client, Processor processor) {

        super(endpoint, processor);
        this.client = client;
        this.endpoint = endpoint;
        this.view = client.getView(endpoint.getDesignDocumentName(), endpoint.getViewName());
        this.query = new Query();
        init();

    }

    private void init() {

        query.setIncludeDocs(true);

        int limit = endpoint.getLimit();
        if (limit > 0) {
            query.setLimit(limit);
        }

        int skip = endpoint.getSkip();
        if (skip > 0) {
            query.setSkip(skip);
        }

        query.setDescending(endpoint.isDescending());

        String rangeStartKey = endpoint.getRangeStartKey();
        String rangeEndKey = endpoint.getRangeEndKey();
        if ("".equals(rangeStartKey) || "".equals(rangeEndKey)) {
            return;
        }
        query.setRange(rangeStartKey, rangeEndKey);

    }

    @Override
    protected void doStart() throws Exception {
        log.info("Starting Couchbase consumer");
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.info("Stopping Couchbase consumer");
        super.doStop();
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    protected synchronized int poll() throws Exception {
        ViewResponse result = client.query(view, query);
        log.info("Received result set from Couchbase");

        if (log.isTraceEnabled()) {
            log.trace("ViewResponse = {}", result);
        }

        String consumerProcessedStrategy = endpoint.getConsumerProcessedStrategy();

        for (ViewRow row : result) {

            String id = row.getId();
            Object doc = row.getDocument();

            String key = row.getKey();
            String designDocumentName = endpoint.getDesignDocumentName();
            String viewName = endpoint.getViewName();

            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(doc);
            exchange.getIn().setHeader(HEADER_ID, id);
            exchange.getIn().setHeader(HEADER_KEY, key);
            exchange.getIn().setHeader(HEADER_DESIGN_DOCUMENT_NAME, designDocumentName);
            exchange.getIn().setHeader(HEADER_VIEWNAME, viewName);

            if ("delete".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (log.isTraceEnabled()) {
                    log.trace("Deleting doc with ID {}", id);
                }
                client.delete(id);
            } else if ("filter".equalsIgnoreCase(consumerProcessedStrategy)) {
                if (log.isTraceEnabled()) {
                    log.trace("Filtering out ID {}", id);
                }
                // add filter for already processed docs
            } else {
                log.trace("No strategy set for already processed docs, beware of duplicates!");
            }

            logDetails(id, doc, key, designDocumentName, viewName, exchange);

            try {
                this.getProcessor().process(exchange);
            } catch (Exception e) {
                this.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
            }
        }

        return result.size();
    }

    private void logDetails(String id, Object doc, String key, String designDocumentName, String viewName, Exchange exchange) {

        if (log.isTraceEnabled()) {
            log.trace("Created exchange = {}", exchange);
            log.trace("Added Document in body = {}", doc);
            log.trace("Adding to Header");
            log.trace("ID = {}", id);
            log.trace("Key = {}", key);
            log.trace("Design Document Name = {}", designDocumentName);
            log.trace("View Name = {}", viewName);
        }

    }
}
