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
package org.apache.camel.impl.engine;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PooledExchange;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultPooledExchange;
import org.apache.camel.support.ResetableClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pooled {@link ExchangeFactory} that reuses {@link Exchange} instance from a pool.
 */
public final class PooledExchangeFactory extends PrototypeExchangeFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PooledExchangeFactory.class);

    private final ReleaseOnDoneTask onDone = new ReleaseOnDoneTask();

    public PooledExchangeFactory() {
    }

    public PooledExchangeFactory(Consumer consumer) {
        super(consumer);
    }

    @Override
    public ExchangeFactory newExchangeFactory(Consumer consumer) {
        PooledExchangeFactory answer = new PooledExchangeFactory(consumer);
        answer.setCamelContext(camelContext);
        answer.setCapacity(capacity);
        answer.setStatisticsEnabled(statisticsEnabled);
        return answer;
    }

    @Override
    public Exchange create(boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            // create a new exchange as there was no free from the pool
            exchange = createPooledExchange(null, autoRelease);
            if (statisticsEnabled) {
                statistics.created.increment();
            }
        } else {
            if (statisticsEnabled) {
                statistics.acquired.increment();
            }
        }

        // reset exchange for reuse
        ((ResetableClock) exchange.getClock()).reset();

        return exchange;
    }

    @Override
    public Exchange create(Endpoint fromEndpoint, boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            // create a new exchange as there was no free from the pool
            exchange = createPooledExchange(fromEndpoint, autoRelease);
            if (statisticsEnabled) {
                statistics.created.increment();
            }
        } else {
            if (statisticsEnabled) {
                statistics.acquired.increment();
            }
        }

        // reset exchange for reuse
        ((ResetableClock) exchange.getClock()).reset();

        return exchange;
    }

    @Override
    public boolean release(Exchange exchange) {
        try {
            // done exchange before returning to pool
            PooledExchange ee = (PooledExchange) exchange;
            ee.done();

            // only release back in pool if reset was success
            boolean inserted = pool.offer(exchange);

            if (statisticsEnabled) {
                if (inserted) {
                    statistics.released.increment();
                } else {
                    statistics.discarded.increment();
                }
            }
            return inserted;
        } catch (Exception e) {
            if (statisticsEnabled) {
                statistics.discarded.increment();
            }
            LOG.debug("Error resetting exchange: {}. This exchange is discarded.", exchange);
            return false;
        }
    }

    private PooledExchange createPooledExchange(Endpoint fromEndpoint, boolean autoRelease) {
        PooledExchange answer;
        if (fromEndpoint != null) {
            answer = DefaultPooledExchange.newFromEndpoint(fromEndpoint);
        } else {
            answer = new DefaultPooledExchange(camelContext);
        }
        answer.setAutoRelease(autoRelease);
        if (autoRelease) {
            // the consumer will either always be in auto release mode or not, so its safe to initialize the task only once when the exchange is created
            answer.onDone(onDone);
        }
        return answer;
    }

    @Override
    public boolean isPooled() {
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        if (exchangeFactoryManager != null) {
            exchangeFactoryManager.removeExchangeFactory(this);
        }
        if (pool != null) {
            logUsageSummary(LOG, "PooledExchangeFactory", pool.size());
            pool.clear();
        }

        // do not call super
    }

    private final class ReleaseOnDoneTask implements PooledExchange.OnDoneTask {

        @Override
        public void onDone(Exchange exchange) {
            release(exchange);
        }
    }

}
