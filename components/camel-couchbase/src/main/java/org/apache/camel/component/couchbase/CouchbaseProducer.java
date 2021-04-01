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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_DELETE;
import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_GET;
import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_PUT;
import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_TTL;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_TTL;

/**
 * Couchbase producer generates various type of operations. PUT, GET, and DELETE are currently supported
 */
public class CouchbaseProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseProducer.class);

    private final AtomicLong startId = new AtomicLong();
    private final CouchbaseEndpoint endpoint;
    private final Bucket client;
    private final Collection collection;
    private final PersistTo persistTo;
    private final ReplicateTo replicateTo;
    private final int producerRetryPause;
    private final long queryTimeout;
    private final long writeQueryTimeout;

    public CouchbaseProducer(CouchbaseEndpoint endpoint, Bucket client, int persistTo, int replicateTo) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
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

        if (endpoint.isAutoStartIdForInserts()) {
            this.startId.set(endpoint.getStartingIdForInsertsFrom());
        }

        // timeout and retry strategy
        this.producerRetryPause = endpoint.getProducerRetryPause();
        this.writeQueryTimeout = endpoint.getWriteQueryTimeout();
        this.queryTimeout = endpoint.getQueryTimeout();

        switch (persistTo) {
            case 0:
                this.persistTo = PersistTo.NONE;
                break;
            case 1:
                this.persistTo = PersistTo.ACTIVE;
                break;
            case 3:
                this.persistTo = PersistTo.THREE;
                break;
            case 4:
                this.persistTo = PersistTo.FOUR;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported persistTo parameter. Supported values are 0 to 4. Currently provided: " + persistTo);
        }

        switch (replicateTo) {
            case 0:
                this.replicateTo = ReplicateTo.NONE;
                break;
            case 1:
                this.replicateTo = ReplicateTo.ONE;
                break;
            case 2:
                this.replicateTo = ReplicateTo.TWO;
                break;
            case 3:
                this.replicateTo = ReplicateTo.THREE;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported replicateTo parameter. Supported values are 0 to 3. Currently provided: " + replicateTo);
        }

    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> headers = exchange.getIn().getHeaders();

        String id = (headers.containsKey(HEADER_ID)) ? exchange.getIn().getHeader(HEADER_ID, String.class) : endpoint.getId();

        int ttl = (headers.containsKey(HEADER_TTL))
                ? Integer.parseInt(exchange.getIn().getHeader(HEADER_TTL, String.class)) : DEFAULT_TTL;

        if (endpoint.isAutoStartIdForInserts()) {
            id = Long.toString(startId.getAndIncrement());
        } else if (id == null) {
            throw new CouchbaseException(HEADER_ID + " is not specified in message header or endpoint URL.", exchange);
        }

        if (endpoint.getOperation().equals(COUCHBASE_PUT)) {
            LOG.trace("Type of operation: PUT");
            Object obj = exchange.getIn().getBody();
            Boolean result = CouchbaseCollectionOperation.setDocument(collection, id, ttl, obj, persistTo, replicateTo,
                    writeQueryTimeout, producerRetryPause);
            exchange.getMessage().setBody(result);
        } else if (endpoint.getOperation().equals(COUCHBASE_GET)) {
            LOG.trace("Type of operation: GET");
            GetResult result = CouchbaseCollectionOperation.getDocument(collection, id, queryTimeout);
            exchange.getMessage().setBody(result);
        } else if (endpoint.getOperation().equals(COUCHBASE_DELETE)) {
            LOG.trace("Type of operation: DELETE");
            MutationResult result
                    = CouchbaseCollectionOperation.removeDocument(collection, id, writeQueryTimeout, producerRetryPause);
            exchange.getMessage().setBody(result.toString());
        }
        // cleanup the cache headers
        exchange.getIn().removeHeader(HEADER_ID);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (client != null) {
            client.core().shutdown();
        }
    }

}
