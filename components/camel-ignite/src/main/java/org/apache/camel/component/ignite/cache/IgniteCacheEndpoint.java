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

import java.net.URI;
import java.util.Map;

import javax.cache.Cache.Entry;

import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteComponent;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.IgniteComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheEntryEventSerializableFilter;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.Query;

/**
 * The Ignite Cache endpoint is one of camel-ignite endpoints which allows you to interact with
 * an <a href="https://apacheignite.readme.io/docs/data-grid">Ignite Cache</a>.
 * This offers both a Producer (to invoke cache operations on an Ignite cache) and
 * a Consumer (to consume changes from a continuous query).
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "ignite-cache", title = "Ignite Cache", syntax = "ignite-cache:cacheName", label = "nosql,cache,compute",
    consumerClass = IgniteCacheContinuousQueryConsumer.class)
public class IgniteCacheEndpoint extends AbstractIgniteEndpoint {

    @UriPath @Metadata(required = "true")
    private String cacheName;

    @UriParam(label = "producer")
    private IgniteCacheOperation operation;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean failIfInexistentCache;

    @UriParam(label = "producer", defaultValue = "ALL")
    private CachePeekMode cachePeekMode = CachePeekMode.ALL;

    @UriParam(label = "producer,consumer")
    private Query<Entry<Object, Object>> query;

    @UriParam(label = "consumer")
    private CacheEntryEventSerializableFilter<Object, Object> remoteFilter;

    @UriParam(label = "consumer", defaultValue = "true")
    private boolean oneExchangePerUpdate = true;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean fireExistingQueryResults;

    @UriParam(label = "consumer", defaultValue = "true", defaultValueNote = "ContinuousQuery.DFLT_AUTO_UNSUBSCRIBE")
    private boolean autoUnsubscribe = ContinuousQuery.DFLT_AUTO_UNSUBSCRIBE;

    @UriParam(label = "consumer", defaultValue = "1", defaultValueNote = "ContinuousQuery.DFLT_PAGE_SIZE")
    private int pageSize = ContinuousQuery.DFLT_PAGE_SIZE;

    @UriParam(label = "consumer", defaultValue = "0", defaultValueNote = "ContinuousQuery.DFLT_TIME_INTERVAL")
    private long timeInterval = ContinuousQuery.DFLT_TIME_INTERVAL;

    @Deprecated
    public IgniteCacheEndpoint(String endpointUri, URI remainingUri, Map<String, Object> parameters, IgniteComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        cacheName = remainingUri.getHost();
    }

    public IgniteCacheEndpoint(String endpointUri, String remaining, Map<String, Object> parameters, IgniteCacheComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        cacheName = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IgniteCacheProducer(this, obtainCache());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new IgniteCacheContinuousQueryConsumer(this, processor, obtainCache());
    }

    private IgniteCache<Object, Object> obtainCache() throws CamelException {
        IgniteCache<Object, Object> cache = ignite().cache(cacheName);
        if (cache == null) {
            if (failIfInexistentCache) {
                throw new CamelException(String.format("Ignite cache %s doesn't exist, and failIfInexistentCache is true", cacheName));
            }
            cache = ignite().createCache(cacheName);
        }

        return cache;
    }

    /**
     * Gets the cache name.
     * 
     * @return
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * The cache name.
     * 
     * @param cacheName cache name
     */
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * Gets the cache operation to invoke.
     * 
     * @return cache name
     */
    public IgniteCacheOperation getOperation() {
        return operation;
    }

    /**
     * The cache operation to invoke.
     * <p>Possible values: GET, PUT, REMOVE, SIZE, REBALANCE, QUERY, CLEAR.</p>
     * 
     * @param operation
     */
    public void setOperation(IgniteCacheOperation operation) {
        this.operation = operation;
    }

    /**
     * Whether to fail the initialization if the cache doesn't exist.
     * 
     * @return
     */
    public boolean isFailIfInexistentCache() {
        return failIfInexistentCache;
    }

    /**
     * Whether to fail the initialization if the cache doesn't exist.
     * 
     * @param failIfInexistentCache
     */
    public void setFailIfInexistentCache(boolean failIfInexistentCache) {
        this.failIfInexistentCache = failIfInexistentCache;
    }

    /**
     * Gets the {@link CachePeekMode}, only needed for operations that require it ({@link IgniteCacheOperation#SIZE}).
     * 
     * @return
     */
    public CachePeekMode getCachePeekMode() {
        return cachePeekMode;
    }

    /**
     * The {@link CachePeekMode}, only needed for operations that require it ({@link IgniteCacheOperation#SIZE}).
     * 
     * @param cachePeekMode
     */
    public void setCachePeekMode(CachePeekMode cachePeekMode) {
        this.cachePeekMode = cachePeekMode;
    }

    /**
     * Gets the query to execute, only needed for operations that require it, 
     * and for the Continuous Query Consumer.
     * 
     * @return
     */
    public Query<Entry<Object, Object>> getQuery() {
        return query;
    }

    /**
     * The {@link Query} to execute, only needed for operations that require it,
     * and for the Continuous Query Consumer.
     * 
     * @param query
     */
    public void setQuery(Query<Entry<Object, Object>> query) {
        this.query = query;
    }

    /**
     * Gets the remote filter, only used by the Continuous Query Consumer.
     * 
     * @return
     */
    public CacheEntryEventSerializableFilter<Object, Object> getRemoteFilter() {
        return remoteFilter;
    }

    /**
     * The remote filter, only used by the Continuous Query Consumer.
     * 
     * @param remoteFilter
     */
    public void setRemoteFilter(CacheEntryEventSerializableFilter<Object, Object> remoteFilter) {
        this.remoteFilter = remoteFilter;
    }

    /**
     * Gets whether to pack each update in an individual Exchange, even if multiple updates are
     * received in one batch. Only used by the Continuous Query Consumer.
     * 
     * @return
     */
    public boolean isOneExchangePerUpdate() {
        return oneExchangePerUpdate;
    }

    /**
     * Whether to pack each update in an individual Exchange, even if multiple updates are
     * received in one batch. Only used by the Continuous Query Consumer.
     * 
     * @param oneExchangePerUpdate
     */
    public void setOneExchangePerUpdate(boolean oneExchangePerUpdate) {
        this.oneExchangePerUpdate = oneExchangePerUpdate;
    }

    /**
     * Gets whether auto unsubscribe is enabled in the Continuous Query Consumer.
     * 
     * @return
     */
    public boolean isAutoUnsubscribe() {
        return autoUnsubscribe;
    }

    /**
     * Whether auto unsubscribe is enabled in the Continuous Query Consumer.
     * 
     * @param autoUnsubscribe
     */
    public void setAutoUnsubscribe(boolean autoUnsubscribe) {
        this.autoUnsubscribe = autoUnsubscribe;
    }

    /**
     * Gets the page size. Only used by the Continuous Query Consumer.
     * 
     * @return
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * The page size. Only used by the Continuous Query Consumer.
     * 
     * @param pageSize
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Gets whether to process existing results that match the query. Used on initialization of 
     * the Continuous Query Consumer.
     * 
     * @return
     */
    public boolean isFireExistingQueryResults() {
        return fireExistingQueryResults;
    }

    /**
     * Whether to process existing results that match the query. Used on initialization of 
     * the Continuous Query Consumer.
     * 
     * @param fireExistingQueryResults
     */
    public void setFireExistingQueryResults(boolean fireExistingQueryResults) {
        this.fireExistingQueryResults = fireExistingQueryResults;
    }

    /**
     * Gets the time interval for the Continuous Query Consumer.
     * 
     * @return
     */
    public long getTimeInterval() {
        return timeInterval;
    }

    /**
     * The time interval for the Continuous Query Consumer.
     * 
     * @param timeInterval
     */
    public void setTimeInterval(long timeInterval) {
        this.timeInterval = timeInterval;
    }

}
