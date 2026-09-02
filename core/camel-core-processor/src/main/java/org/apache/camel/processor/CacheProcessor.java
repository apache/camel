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
package org.apache.camel.processor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the Cache EIP pattern that provides a read-through cache for a block of processing steps.
 * <p/>
 * On cache hit (key found in the repository), the cached value is set as the message body and the nested processing
 * steps are skipped entirely. On cache miss, the nested steps execute normally and the resulting message body is stored
 * in the cache for subsequent requests with the same key.
 * <p/>
 * Cache errors are handled gracefully — they are logged but never propagate to the exchange. Failed exchanges (those
 * with an exception set) are never cached.
 *
 * @since 4.23
 */
public class CacheProcessor extends BaseProcessorSupport
        implements CamelContextAware, Navigate<Processor>, IdAware, RouteIdAware, StepIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(CacheProcessor.class);

    private CamelContext camelContext;
    private String id;
    private String routeId;
    private String stepId;
    private final Expression keyExpression;
    private final AsyncProcessor processor;
    private final KeyValueRepository keyValueRepository;
    private final Duration ttl;
    private final boolean cacheNull;

    public CacheProcessor(
                          Expression keyExpression, KeyValueRepository keyValueRepository,
                          Duration ttl, boolean cacheNull, Processor processor) {
        this.keyExpression = keyExpression;
        this.keyValueRepository = keyValueRepository;
        this.ttl = ttl;
        this.cacheNull = cacheNull;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // Evaluate the cache key expression
        final String key;
        try {
            key = keyExpression.evaluate(exchange, String.class);
        } catch (Exception e) {
            // Expression evaluation failure — execute child without caching
            LOG.warn("Cache key expression evaluation failed, proceeding without cache: {}", e.getMessage());
            return processor.process(exchange, callback);
        }

        if (key == null) {
            // Null key — cannot cache, execute child unconditionally
            LOG.debug("Cache key is null, executing block without caching");
            return processor.process(exchange, callback);
        }

        // Try to read from cache
        try {
            if (cacheNull) {
                // When cacheNull is enabled, we must distinguish "not in cache" from "cached null"
                if (keyValueRepository.contains(key)) {
                    Object cached = keyValueRepository.get(key);
                    LOG.debug("Cache hit for key: {}", key);
                    exchange.getMessage().setBody(cached);
                    callback.done(true);
                    return true;
                }
            } else {
                Object cached = keyValueRepository.get(key);
                if (cached != null) {
                    LOG.debug("Cache hit for key: {}", key);
                    exchange.getMessage().setBody(cached);
                    callback.done(true);
                    return true;
                }
            }
        } catch (Exception e) {
            // Cache read failure — degrade gracefully, execute the block
            LOG.warn("Cache read failed for key '{}', executing block without cache: {}", key, e.getMessage());
        }

        // Cache miss — execute child processor, then cache the result on success
        LOG.debug("Cache miss for key: {}", key);
        return processor.process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                try {
                    if (!exchange.isFailed() && exchange.getException() == null) {
                        Object result = exchange.getMessage().getBody();
                        if (result != null || cacheNull) {
                            keyValueRepository.put(key, result, ttl);
                            LOG.debug("Cached result for key: {}", key);
                        }
                    }
                } catch (Exception ex) {
                    // Cache write failure — log but do NOT fail the exchange
                    LOG.warn("Cache write failed for key '{}': {}", key, ex.getMessage());
                } finally {
                    callback.done(doneSync);
                }
            }
        });
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean hasNext() {
        return processor != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(processor);
        return answer;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        keyExpression.init(camelContext);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(keyValueRepository, processor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(processor, keyValueRepository);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        ServiceHelper.stopAndShutdownServices(processor, keyValueRepository);
    }

    public Expression getKeyExpression() {
        return keyExpression;
    }

    public KeyValueRepository getKeyValueRepository() {
        return keyValueRepository;
    }

    public Duration getTtl() {
        return ttl;
    }

    public boolean isCacheNull() {
        return cacheNull;
    }

}
