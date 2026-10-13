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

    /**
     * Releases the exchange back into the pool.
     * <p/>
     * An exchange created with auto release is put into the pool when it is done. If it is not done yet (it was given
     * back without being routed) this method does that and returns whether the pool took it, so false when the pool was
     * full and the exchange was discarded. If it is already done, its unit of work has handed it to the pool before,
     * and this method does nothing and returns true; whether the pool took it at that time is not known here.
     */
    @Override
    public boolean release(Exchange exchange) {
        try {
            // done exchange before returning to pool
            PooledExchange ee = (PooledExchange) exchange;

            if (ee.isAutoRelease()) {
                // an auto-release exchange is offered to the pool once: by its on-done task when the unit of work is
                // done, or here when the exchange was given back without being routed. Offering it in both places
                // would put it in the pool twice.
                if (ee.getClock().getCreated() <= 0) {
                    // already done (the clock is unset by done), so the on-done task has offered it
                    return true;
                }
                // done() would run the on-done task and offer the exchange, but the result of that offer cannot be
                // read from here. The caller owns the exchange until it is offered, so take the task off for this
                // call, offer the exchange here, and put the task back before the offer makes the exchange visible
                // to other threads.
                ee.onDone(null);
                try {
                    ee.done();
                } finally {
                    ee.onDone(onDone);
                }
            } else {
                ee.done();
            }
        } catch (Exception e) {
            return discard(exchange);
        }
        return doRelease(exchange);
    }

    private boolean doRelease(Exchange exchange) {
        try {
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
            return discard(exchange);
        }
    }

    private boolean discard(Exchange exchange) {
        if (statisticsEnabled) {
            statistics.discarded.increment();
        }
        LOG.debug("Error resetting exchange: {}. This exchange is discarded.", exchange);
        return false;
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
            // the exchange is done (reset) when this is called
            doRelease(exchange);
        }
    }

}
