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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PooledExchange;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultPooledExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pooled {@link ExchangeFactory} that reuses {@link Exchange} instance from a pool.
 */
public final class PooledExchangeFactory extends DefaultExchangeFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PooledExchangeFactory.class);

    private final ReleaseOnDoneTask onDone = new ReleaseOnDoneTask();
    private final Consumer consumer;
    private BlockingQueue<Exchange> pool;
    private int capacity = 100;

    public PooledExchangeFactory() {
        this.consumer = null;
    }

    public PooledExchangeFactory(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        this.pool = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public Consumer getConsumer() {
        return consumer;
    }

    @Override
    public ExchangeFactory newExchangeFactory(Consumer consumer) {
        PooledExchangeFactory answer = new PooledExchangeFactory(consumer);
        answer.setCamelContext(camelContext);
        answer.setCapacity(capacity);
        answer.setStatisticsEnabled(isStatisticsEnabled());
        return answer;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getSize() {
        if (pool != null) {
            return pool.size();
        } else {
            return 0;
        }
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public Exchange create(boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            // create a new exchange as there was no free from the pool
            exchange = createPooledExchange(null, autoRelease);
            if (statistics.isStatisticsEnabled()) {
                statistics.created.increment();
            }
        } else {
            if (statistics.isStatisticsEnabled()) {
                statistics.acquired.increment();
            }
            // reset exchange for reuse
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            ee.reset(System.currentTimeMillis());
        }
        return exchange;
    }

    @Override
    public Exchange create(Endpoint fromEndpoint, boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            // create a new exchange as there was no free from the pool
            exchange = new DefaultPooledExchange(fromEndpoint);
            if (statistics.isStatisticsEnabled()) {
                statistics.created.increment();
            }
        } else {
            if (statistics.isStatisticsEnabled()) {
                statistics.acquired.increment();
            }
            // reset exchange for reuse
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            ee.reset(System.currentTimeMillis());
        }
        return exchange;
    }

    @Override
    public boolean release(Exchange exchange) {
        try {
            // done exchange before returning back to pool
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            boolean force = !ee.isAutoRelease();
            ee.done(force);
            ee.onDone(null);

            // only release back in pool if reset was success
            boolean inserted = pool.offer(exchange);

            if (statistics.isStatisticsEnabled()) {
                if (inserted) {
                    statistics.released.increment();
                } else {
                    statistics.discarded.increment();
                }
            }
            return inserted;
        } catch (Exception e) {
            if (statistics.isStatisticsEnabled()) {
                statistics.discarded.increment();
            }
            LOG.debug("Error resetting exchange: {}. This exchange is discarded.", exchange);
            return false;
        }
    }

    protected PooledExchange createPooledExchange(Endpoint fromEndpoint, boolean autoRelease) {
        PooledExchange answer;
        if (fromEndpoint != null) {
            answer = new DefaultPooledExchange(fromEndpoint);
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
    public void purge() {
        pool.clear();
    }

    @Override
    protected void doStop() throws Exception {
        exchangeFactoryManager.removeExchangeFactory(this);
        logUsageSummary(LOG, "PooledExchangeFactory", pool.size());
        statistics.reset();
        pool.clear();

        // do not call super
    }

    private final class ReleaseOnDoneTask implements PooledExchange.OnDoneTask {

        @Override
        public void onDone(Exchange exchange) {
            release(exchange);
        }
    }

}
