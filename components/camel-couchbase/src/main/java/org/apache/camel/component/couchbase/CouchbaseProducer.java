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

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.client.core.error.InvalidArgumentException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.codec.RawJsonTranscoder;
import com.couchbase.client.java.codec.Transcoder;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
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
    private Collection collection;
    private final PersistTo persistTo;
    private final ReplicateTo replicateTo;
    private final int producerRetryPause;
    private final long queryTimeout;
    private final long writeQueryTimeout;

    public CouchbaseProducer(CouchbaseEndpoint endpoint, Bucket client, int persistTo, int replicateTo) {
        super(endpoint);
        this.endpoint = endpoint;
        this.collection = resolveCollection(client);

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

    private Collection resolveCollection(Bucket client) {
        Scope scope;
        if (endpoint.getScope() != null) {
            scope = client.scope(endpoint.getScope());
        } else {
            scope = client.defaultScope();
        }

        if (endpoint.getCollection() != null) {
            return scope.collection(endpoint.getCollection());
        }
        return client.defaultCollection();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        String operation = endpoint.getOperation();
        if (!COUCHBASE_PUT.equals(operation) && !COUCHBASE_GET.equals(operation) && !COUCHBASE_DELETE.equals(operation)) {
            throw unknownOperation(operation);
        }
    }

    private static IllegalArgumentException unknownOperation(String operation) {
        return new IllegalArgumentException(
                "Unknown operation " + operation + ": use one of " + COUCHBASE_PUT + ", " + COUCHBASE_GET + ", "
                                            + COUCHBASE_DELETE);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Take the collection again on every start. The endpoint owns the cluster and disconnects it when it
        // stops, so a handle kept from construction would point at a dead cluster after a restart in place
        this.collection = resolveCollection(endpoint.createClient());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> headers = exchange.getIn().getHeaders();

        // the header, else the key option. The header is left on the message, so that a later couchbase endpoint in
        // the route, such as a CCB_GET after a CCB_PUT, works on the same document
        String id = exchange.getIn().getHeader(HEADER_ID, String.class);
        if (id == null) {
            id = endpoint.getKey();
        }

        int ttl = (headers.containsKey(HEADER_TTL))
                ? Integer.parseInt(exchange.getIn().getHeader(HEADER_TTL, String.class)) : DEFAULT_TTL;

        if (endpoint.isAutoStartIdForInserts()) {
            id = Long.toString(startId.getAndIncrement());
        } else if (id == null) {
            throw new CouchbaseException(
                    "No document id: set the " + HEADER_ID + " header or the key option of the endpoint", exchange);
        }

        switch (endpoint.getOperation()) {
            case COUCHBASE_PUT -> {
                LOG.trace("Type of operation: PUT");
                Object content = exchange.getIn().getBody();
                if (content instanceof InputStream || content instanceof StreamCache) {
                    content = exchange.getIn().getMandatoryBody(byte[].class);
                }
                Boolean result = CouchbaseCollectionOperation.setDocument(collection, id, ttl, content,
                        transcoderFor(content), persistTo, replicateTo, writeQueryTimeout, producerRetryPause);
                exchange.getMessage().setBody(result);
            }
            case COUCHBASE_GET -> {
                LOG.trace("Type of operation: GET");
                GetResult result = CouchbaseCollectionOperation.getDocument(collection, id, queryTimeout, producerRetryPause);
                exchange.getMessage().setBody(result);
            }
            case COUCHBASE_DELETE -> {
                LOG.trace("Type of operation: DELETE");
                MutationResult result
                        = CouchbaseCollectionOperation.removeDocument(collection, id, writeQueryTimeout, producerRetryPause);
                exchange.getMessage().setBody(result.toString());
            }
            default -> throw unknownOperation(endpoint.getOperation());
        }
    }

    /**
     * The transcoder that stores the body of a CCB_PUT, or null for the JSON transcoder of the cluster environment.
     * <p/>
     * Text or bytes that hold a JSON object or array are stored as they are, as a JSON document; the JSON transcoder
     * would store the text as a JSON string, and fails on bytes. Other bytes are stored as a binary document. Any other
     * body, such as other text, a Map, a List, a JsonObject or a POJO, is serialized to JSON as before.
     */
    private static Transcoder transcoderFor(Object content) {
        if (content instanceof String text) {
            return isJson(text) ? RawJsonTranscoder.INSTANCE : null;
        } else if (content instanceof byte[] bytes) {
            return isJson(bytes) ? RawJsonTranscoder.INSTANCE : RawBinaryTranscoder.INSTANCE;
        }
        return null;
    }

    private static boolean isJson(String text) {
        String trimmed = text.strip();
        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                JsonObject.fromJson(trimmed);
                return true;
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                JsonArray.fromJson(trimmed);
                return true;
            }
        } catch (InvalidArgumentException e) {
            // not JSON
        }
        return false;
    }

    private static boolean isJson(byte[] bytes) {
        int first = 0;
        int last = bytes.length - 1;
        while (first <= last && isJsonWhitespace(bytes[first])) {
            first++;
        }
        while (last >= first && isJsonWhitespace(bytes[last])) {
            last--;
        }
        if (first > last) {
            return false;
        }
        boolean object = bytes[first] == '{' && bytes[last] == '}';
        boolean array = bytes[first] == '[' && bytes[last] == ']';
        if (!object && !array) {
            return false;
        }
        try {
            if (object) {
                JsonObject.fromJson(bytes);
            } else {
                JsonArray.fromJson(bytes);
            }
            return true;
        } catch (InvalidArgumentException e) {
            // not JSON
            return false;
        }
    }

    private static boolean isJsonWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

}
