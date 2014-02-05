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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.couchbase.CouchbaseConstants.*;

public class CouchbaseReceiver implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseReceiver.class);

    private volatile boolean stopped;
    private final CouchbaseEndpoint endpoint;
    private final CouchbaseClient client;
    private final CouchbaseConsumer consumer;
    private final View view;
    private final Query query;

    public CouchbaseReceiver(CouchbaseEndpoint endpoint, CouchbaseConsumer consumer, CouchbaseClient client) {

        this.endpoint = endpoint;
        this.consumer = consumer;
        this.client = client;
        this.view = client.getView(endpoint.getDesignDocumentName(), endpoint.getViewName());
        this.query = new Query();
        init();

    }

    private void init() {

        query.setIncludeDocs(true);

        int limit = endpoint.getLimit();
        if (limit > 0) { query.setLimit(limit); }

        int skip = endpoint.getSkip();
        if (skip > 0) { query.setSkip(skip); }

        query.setDescending(endpoint.isDescending());

        String rangeStartKey = endpoint.getRangeStartKey();
        String rangeEndKey = endpoint.getRangeEndKey();
        if (rangeStartKey.equals("") || rangeEndKey.equals("")) {
            return;
        }
        query.setRange(rangeStartKey, rangeEndKey);

    }

    @Override
    public void run() {

        ViewResponse result = client.query(view, query);
        if (LOG.isInfoEnabled()) {
            LOG.info("Received result set from Couchbase");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("ViewResponse = {}", result);
        }

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

            if (LOG.isTraceEnabled()) {
                logDetails(id, doc, key, designDocumentName, viewName, exchange);
            }

            try {
                consumer.getProcessor().process(exchange);
            } catch (Exception e) {
                consumer.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
            }
        }

        stopped = true;
    }

    private void logDetails(String id, Object doc, String key, String designDocumentName, String viewName, Exchange exchange) {

        LOG.trace("Created exchange = {}", exchange);
        LOG.trace("Added Document in body = {}", doc);
        LOG.trace("Adding to Header");
        LOG.trace("ID = {}", id);
        LOG.trace("Key = {}", key);
        LOG.trace("Design Document Name = {}", designDocumentName);
        LOG.trace("View Name = {}", viewName);

    }

    public void stop() {
        client.shutdown();
    }

    public boolean isStopped() {
        return stopped;
    }

}