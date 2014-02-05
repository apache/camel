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
import net.spy.memcached.internal.OperationFuture;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import java.util.Map;

import static org.apache.camel.component.couchbase.CouchbaseConstants.*;

public class CouchbaseProducer extends DefaultProducer {

    private CouchbaseEndpoint endpoint;
    private CouchbaseClient client;
    private long startId;

    public CouchbaseProducer(CouchbaseEndpoint endpoint, CouchbaseClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
        if (endpoint.isAutoStartIdForInserts()) {
            this.startId = endpoint.getStartingIdForInsertsFrom();
        }

    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        String id = (headers.containsKey(HEADER_ID))
                ? exchange.getIn().getHeader(HEADER_ID, String.class)
                : endpoint.getId();

        if (endpoint.isAutoStartIdForInserts()) {
            id = Long.toString(startId);
            startId++;
        } else if (id == null) {
            throw new CouchbaseException(HEADER_ID + " is not specified in message header or endpoint URL.", exchange);
        }

        if (endpoint.getOperation().equals(COUCHBASE_PUT)) {
            log.info("Type of operation: PUT");
            Object obj = exchange.getIn().getBody();
            OperationFuture<Boolean> result = client.set(id, obj);
            exchange.getOut().setBody(result.get());
        } else if (endpoint.getOperation().equals(COUCHBASE_GET)) {
            log.info("Type of operation: GET");
            Object result = client.get(id);
            exchange.getOut().setBody(result);
        } else if (endpoint.getOperation().equals(COUCHBASE_DELETE)) {
            log.info("Type of operation: DELETE");
            OperationFuture<Boolean> result = client.delete(id);
            exchange.getOut().setBody(result.get());
        }

        //cleanup the cache headers
        exchange.getIn().removeHeader(HEADER_ID);

    }

}
