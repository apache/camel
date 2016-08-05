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
package org.apache.camel.component.ignite.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.component.ignite.IgniteHelper;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.MessageHelper;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.Query;
import org.apache.ignite.cache.query.QueryCursor;

/**
 * Ignite Cache producer.
 */
public class IgniteCacheProducer extends DefaultAsyncProducer {

    private IgniteCache<Object, Object> cache;
    private IgniteCacheEndpoint endpoint;

    public IgniteCacheProducer(IgniteCacheEndpoint endpoint, IgniteCache<Object, Object> igniteCache) {
        super(endpoint);
        this.endpoint = endpoint;
        this.cache = igniteCache;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message in = exchange.getIn();
        Message out = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), out, true);

        switch (cacheOperationFor(exchange)) {

        case GET:
            doGet(in, out);
            break;

        case PUT:
            doPut(in, out);
            break;

        case QUERY:
            doQuery(in, out, exchange);
            break;

        case REMOVE:
            doRemove(in, out);
            break;

        case CLEAR:
            doClear(in, out);
            break;

        case SIZE:
            doSize(in, out);
            break;

        case REBALANCE:
            doRebalance(in, out);
            break;

        default:
            break;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void doGet(Message in, Message out) {
        Object cacheKey = cacheKey(in);

        if (cacheKey instanceof Set && !endpoint.isTreatCollectionsAsCacheObjects()) {
            out.setBody(cache.getAll((Set<Object>) cacheKey));
        } else {
            out.setBody(cache.get(cacheKey));
        }
    }

    @SuppressWarnings("unchecked")
    private void doPut(Message in, Message out) {
        Map<Object, Object> map = in.getBody(Map.class);

        if (map != null) {
            cache.putAll(map);
            return;
        }

        Object cacheKey = in.getHeader(IgniteConstants.IGNITE_CACHE_KEY);

        if (cacheKey == null) {
            throw new RuntimeCamelException("Cache PUT operation requires the cache key in the CamelIgniteCacheKey header, " + "or a payload of type Map.");
        }

        cache.put(cacheKey, in.getBody());

        IgniteHelper.maybePropagateIncomingBody(endpoint, in, out);
    }

    @SuppressWarnings("unchecked")
    private void doQuery(Message in, Message out, Exchange exchange) {
        Query<Object> query = in.getHeader(IgniteConstants.IGNITE_CACHE_QUERY, Query.class);

        if (query == null) {
            try {
                query = in.getMandatoryBody(Query.class);
            } catch (InvalidPayloadException e) {
                exchange.setException(e);
                return;
            }
        }

        final QueryCursor<Object> cursor = cache.query(query);

        out.setBody(cursor.iterator());

        exchange.addOnCompletion(new Synchronization() {
            @Override
            public void onFailure(Exchange exchange) {
                cursor.close();
            }

            @Override
            public void onComplete(Exchange exchange) {
                cursor.close();
            }
        });

    }

    @SuppressWarnings("unchecked")
    private void doRemove(Message in, Message out) {
        Object cacheKey = cacheKey(in);

        if (cacheKey instanceof Set && !endpoint.isTreatCollectionsAsCacheObjects()) {
            cache.removeAll((Set<Object>) cacheKey);
        } else {
            cache.remove(cacheKey);
        }

        IgniteHelper.maybePropagateIncomingBody(endpoint, in, out);
    }

    private void doClear(Message in, Message out) {
        cache.removeAll();

        IgniteHelper.maybePropagateIncomingBody(endpoint, in, out);
    }

    private void doRebalance(Message in, Message out) {
        cache.rebalance().get();

        IgniteHelper.maybePropagateIncomingBody(endpoint, in, out);
    }

    @SuppressWarnings("unchecked")
    private void doSize(Message in, Message out) {
        Object peekMode = in.getHeader(IgniteConstants.IGNITE_CACHE_PEEK_MODE, endpoint.getCachePeekMode());

        Integer result = null;
        if (peekMode instanceof Collection) {
            result = cache.size(((Collection<Object>) peekMode).toArray(new CachePeekMode[0]));
        } else if (peekMode instanceof CachePeekMode) {
            result = cache.size((CachePeekMode) peekMode);
        }

        out.setBody(result);
    }

    private Object cacheKey(Message msg) {
        Object cacheKey = msg.getHeader(IgniteConstants.IGNITE_CACHE_KEY);
        if (cacheKey == null) {
            cacheKey = msg.getBody();
        }
        return cacheKey;
    }

    private IgniteCacheOperation cacheOperationFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_CACHE_OPERATION, endpoint.getOperation(), IgniteCacheOperation.class);
    }

}