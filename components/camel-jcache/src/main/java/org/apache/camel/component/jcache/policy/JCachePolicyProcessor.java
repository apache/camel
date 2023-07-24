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
package org.apache.camel.component.jcache.policy;

import javax.cache.Cache;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCachePolicyProcessor extends DelegateAsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(JCachePolicyProcessor.class);

    private final CamelContext camelContext;
    private Cache cache;
    private Expression keyExpression;
    private Expression bypassExpression;

    public JCachePolicyProcessor(CamelContext camelContext, Cache cache, Expression keyExpression, Expression bypassExpression,
                                 Processor processor) {
        super(processor);
        this.camelContext = camelContext;
        this.cache = cache;
        this.keyExpression = keyExpression;
        this.bypassExpression = bypassExpression;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        LOG.debug("JCachePolicy process started - cache:{}, exchange:{}", cache.getName(), exchange.getExchangeId());

        //If cache is closed, just continue
        if (cache.isClosed()) {
            return super.process(exchange, callback);
        }

        try {
            //Get key by the expression or use message body
            Object key
                    = keyExpression != null ? keyExpression.evaluate(exchange, Object.class) : exchange.getMessage().getBody();

            if (key == null) {
                return super.process(exchange, callback);
            }

            Boolean bypass = bypassExpression != null ? bypassExpression.evaluate(exchange, Boolean.class) : Boolean.FALSE;
            if (!Boolean.TRUE.equals(bypass)) {
                //Check if cache contains the key
                Object value = cache.get(key);
                if (value != null) {
                    // use the cached object in the Exchange without calling the rest of the route
                    LOG.debug("Cached object is found, skipping the route - key:{}, exchange:{}", key,
                            exchange.getExchangeId());

                    exchange.getMessage().setBody(value);

                    callback.done(true);
                    return true;
                }
                //Not found in cache. Continue route.
                LOG.debug("No cached object is found, continue route - key:{}, exchange:{}", key, exchange.getExchangeId());
            } else {
                LOG.debug("Bypassing cache - key:{}, exchange:{}", key, exchange.getExchangeId());
            }

            return super.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    try {
                        if (!exchange.isFailed()) {
                            //Save body in cache after successfully executing the route
                            Object value = exchange.getMessage().getBody();

                            if (value != null) {
                                LOG.debug("Saving in cache - key:{}, value:{}, exchange:{}", key, value,
                                        exchange.getExchangeId());
                                cache.put(key, value);
                            }
                        }
                    } catch (Exception ex) {
                        //Log exception, but a problem with caching should not fail the exchange
                        LOG.error("Error storing in cache. - key:{}, exchange:{}", key, exchange.getExchangeId(), ex);
                    } finally {
                        callback.done(doneSync);
                    }
                }
            });

        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (keyExpression != null) {
            keyExpression.init(camelContext);
        }
    }

    @Override
    protected void doStop() throws Exception {
        //Clear cache if stopping.
        if (!cache.isClosed()) {
            cache.clear();
        }
        super.doStop();
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Expression getKeyExpression() {
        return keyExpression;
    }

    public void setKeyExpression(Expression keyExpression) {
        this.keyExpression = keyExpression;
    }

    public Expression getBypassExpression() {
        return bypassExpression;
    }

    public void setBypassExpression(Expression bypassExpression) {
        this.bypassExpression = bypassExpression;
    }
}
