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
import java.util.concurrent.Future;

import com.couchbase.client.CouchbaseClientIF;
import net.spy.memcached.PersistTo;
import net.spy.memcached.ReplicateTo;
import net.spy.memcached.internal.OperationFuture;
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
 * Couchbase producer generates various type of operations. PUT, GET, and DELETE
 * are currently supported
 */
public class CouchbaseProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseProducer.class);

    private CouchbaseEndpoint endpoint;
    private CouchbaseClientIF client;
    private long startId;
    private PersistTo persistTo;
    private ReplicateTo replicateTo;
    private int producerRetryAttempts;
    private int producerRetryPause;

    public CouchbaseProducer(CouchbaseEndpoint endpoint, CouchbaseClientIF client, int persistTo, int replicateTo) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
        if (endpoint.isAutoStartIdForInserts()) {
            this.startId = endpoint.getStartingIdForInsertsFrom();
        }
        this.producerRetryAttempts = endpoint.getProducerRetryAttempts();
        this.producerRetryPause = endpoint.getProducerRetryPause();

        switch (persistTo) {
            case 0:
                this.persistTo = PersistTo.ZERO;
                break;
            case 1:
                this.persistTo = PersistTo.MASTER;
                break;
            case 2:
                this.persistTo = PersistTo.TWO;
                break;
            case 3:
                this.persistTo = PersistTo.THREE;
                break;
            case 4:
                this.persistTo = PersistTo.FOUR;
                break;
            default:
                throw new IllegalArgumentException("Unsupported persistTo parameter. Supported values are 0 to 4. Currently provided: " + persistTo);
        }

        switch (replicateTo) {
            case 0:
                this.replicateTo = ReplicateTo.ZERO;
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
                throw new IllegalArgumentException("Unsupported replicateTo parameter. Supported values are 0 to 3. Currently provided: " + replicateTo);
        }

    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        String id = (headers.containsKey(HEADER_ID)) ? exchange.getIn().getHeader(HEADER_ID, String.class) : endpoint.getId();

        int ttl = (headers.containsKey(HEADER_TTL)) ? Integer.parseInt(exchange.getIn().getHeader(HEADER_TTL, String.class)) : DEFAULT_TTL;

        if (endpoint.isAutoStartIdForInserts()) {
            id = Long.toString(startId);
            startId++;
        } else if (id == null) {
            throw new CouchbaseException(HEADER_ID + " is not specified in message header or endpoint URL.", exchange);
        }

        if (endpoint.getOperation().equals(COUCHBASE_PUT)) {
            LOG.debug("Type of operation: PUT");
            Object obj = exchange.getIn().getBody();
            exchange.getOut().setBody(setDocument(id, ttl, obj, persistTo, replicateTo));
        } else if (endpoint.getOperation().equals(COUCHBASE_GET)) {
            LOG.debug("Type of operation: GET");
            Object result = client.get(id);
            exchange.getOut().setBody(result);
        } else if (endpoint.getOperation().equals(COUCHBASE_DELETE)) {
            LOG.debug("Type of operation: DELETE");
            Future<Boolean> result = client.delete(id);
            exchange.getOut().setBody(result.get());
        }

        // cleanup the cache headers
        exchange.getIn().removeHeader(HEADER_ID);

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (client != null) {
            client.shutdown();
        }
    }

    private Boolean setDocument(String id, int expiry, Object obj, PersistTo persistTo, ReplicateTo replicateTo) throws Exception {
        return setDocument(id, expiry, obj, producerRetryAttempts, persistTo, replicateTo);
    }

    private Boolean setDocument(String id, int expiry, Object obj, int retryAttempts, PersistTo persistTo, ReplicateTo replicateTo) throws Exception {

        OperationFuture<Boolean> result = client.set(id, expiry, obj, persistTo, replicateTo);
        try {
            if (!result.get()) {
                throw new Exception("Unable to save Document. " + id);
            }
            return true;
        } catch (Exception e) {
            if (retryAttempts <= 0) {
                throw e;
            } else {
                LOG.info("Unable to save Document, retrying in " + producerRetryPause + "ms (" + retryAttempts + ")");
                Thread.sleep(producerRetryPause);
                return setDocument(id, expiry, obj, retryAttempts - 1, persistTo, replicateTo);
            }
        }
    }

}
