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

import javax.cache.Cache.Entry;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryUpdatedListener;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer that generates {@link Exchange}s for items received from a continuous query.
 */
public class IgniteCacheContinuousQueryConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteCacheContinuousQueryConsumer.class);

    private IgniteCacheEndpoint endpoint;

    private IgniteCache<Object, Object> cache;

    private QueryCursor<Entry<Object, Object>> cursor;

    public IgniteCacheContinuousQueryConsumer(IgniteCacheEndpoint endpoint, Processor processor, IgniteCache<Object, Object> cache) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.cache = cache;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        launchContinuousQuery();

        LOG.info("Started Ignite Cache Continuous Query consumer for cache {} with query: {}.", cache.getName(), endpoint.getQuery());

        maybeFireExistingQueryResults();
    }

    private void maybeFireExistingQueryResults() {
        if (!endpoint.isFireExistingQueryResults()) {
            LOG.info(String.format("Skipping existing cache results for cache name = %s.", endpoint.getCacheName()));
            return;
        }

        LOG.info(String.format("Processing existing cache results for cache name = %s.", endpoint.getCacheName()));

        for (Entry<Object, Object> entry : cursor) {
            Exchange exchange = createExchange(entry.getValue());
            exchange.getIn().setHeader(IgniteConstants.IGNITE_CACHE_KEY, entry.getKey());
            getAsyncProcessor().process(createExchange(entry), new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // do nothing
                }
            });
        }
    }

    private void launchContinuousQuery() {
        ContinuousQuery<Object, Object> continuousQuery = new ContinuousQuery<>();

        if (endpoint.getQuery() != null) {
            continuousQuery.setInitialQuery(endpoint.getQuery());
        }

        if (endpoint.getRemoteFilter() != null) {
            continuousQuery.setRemoteFilter(endpoint.getRemoteFilter());
        }

        continuousQuery.setLocalListener(new CacheEntryUpdatedListener<Object, Object>() {
            @Override
            public void onUpdated(Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) throws CacheEntryListenerException {
                if (LOG.isTraceEnabled()) {
                    LOG.info("Processing Continuous Query event(s): {}.", events);
                }

                if (!endpoint.isOneExchangePerUpdate()) {
                    fireGroupedExchange(events);
                    return;
                }

                for (CacheEntryEvent<? extends Object, ? extends Object> entry : events) {
                    fireSingleExchange(entry);
                }
            }
        });

        continuousQuery.setAutoUnsubscribe(endpoint.isAutoUnsubscribe());
        continuousQuery.setPageSize(endpoint.getPageSize());
        continuousQuery.setTimeInterval(endpoint.getTimeInterval());

        cursor = cache.query(continuousQuery);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        cursor.close();
        
        LOG.info("Stopped Ignite Cache Continuous Query consumer for cache {} with query: {}.", cache.getName(), endpoint.getQuery());
    }

    private void fireSingleExchange(CacheEntryEvent<? extends Object, ? extends Object> entry) {
        Exchange exchange = createExchange(entry.getValue());
        exchange.getIn().setHeader(IgniteConstants.IGNITE_CACHE_EVENT_TYPE, entry.getEventType());
        exchange.getIn().setHeader(IgniteConstants.IGNITE_CACHE_OLD_VALUE, entry.getOldValue());
        exchange.getIn().setHeader(IgniteConstants.IGNITE_CACHE_KEY, entry.getKey());
        getAsyncProcessor().process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                // do nothing
            }
        });
    }

    private void fireGroupedExchange(Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) {
        Exchange exchange = createExchange(events);
        getAsyncProcessor().process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                // do nothing
            }
        });
    }

    private Exchange createExchange(Object payload) {
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
        Message in = exchange.getIn();
        in.setBody(payload);
        in.setHeader(IgniteConstants.IGNITE_CACHE_NAME, endpoint.getCacheName());
        return exchange;
    }

}
