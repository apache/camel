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

import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ExchangeFactory} that creates a new {@link Exchange} instance.
 */
public class DefaultExchangeFactory extends ServiceSupport implements ExchangeFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExchangeFactory.class);

    final UtilizationStatistics statistics = new UtilizationStatistics();
    final Consumer consumer;
    CamelContext camelContext;
    ExchangeFactoryManager exchangeFactoryManager;
    String routeId;

    public DefaultExchangeFactory() {
        this.consumer = null;
    }

    public DefaultExchangeFactory(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void doBuild() throws Exception {
        this.exchangeFactoryManager = camelContext.adapt(ExtendedCamelContext.class).getExchangeFactoryManager();
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
    public Consumer getConsumer() {
        return consumer;
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
    public ExchangeFactory newExchangeFactory(Consumer consumer) {
        DefaultExchangeFactory answer = new DefaultExchangeFactory(consumer);
        answer.setStatisticsEnabled(statistics.isStatisticsEnabled());
        answer.setCapacity(getCapacity());
        answer.setCamelContext(camelContext);
        return answer;
    }

    @Override
    public Exchange create(boolean autoRelease) {
        if (statistics.isStatisticsEnabled()) {
            statistics.created.increment();
        }
        return new DefaultExchange(camelContext);
    }

    @Override
    public Exchange create(Endpoint fromEndpoint, boolean autoRelease) {
        if (statistics.isStatisticsEnabled()) {
            statistics.created.increment();
        }
        return new DefaultExchange(fromEndpoint);
    }

    @Override
    public boolean release(Exchange exchange) {
        if (statistics.isStatisticsEnabled()) {
            statistics.released.increment();
        }
        return true;
    }

    @Override
    public boolean isStatisticsEnabled() {
        return statistics.isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        statistics.setStatisticsEnabled(statisticsEnabled);
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public void setCapacity(int capacity) {
        // not in use
    }

    @Override
    public void resetStatistics() {
        statistics.reset();
    }

    @Override
    public void purge() {
        // not in use
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    protected void doStart() throws Exception {
        exchangeFactoryManager.addExchangeFactory(this);
    }

    @Override
    protected void doStop() throws Exception {
        exchangeFactoryManager.removeExchangeFactory(this);
        logUsageSummary(LOG, "DefaultExchangeFactory", 0);
        statistics.reset();
    }

    void logUsageSummary(Logger log, String name, int pooled) {
        if (statistics.isStatisticsEnabled() && consumer != null) {
            // only log if there is any usage
            long created = statistics.getCreatedCounter();
            long acquired = statistics.getAcquiredCounter();
            long released = statistics.getReleasedCounter();
            long discarded = statistics.getDiscardedCounter();
            boolean shouldLog = pooled > 0 || created > 0 || acquired > 0 || released > 0 || discarded > 0;
            if (shouldLog) {
                String id = getRouteId();
                String uri = consumer.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);

                log.info("{} {} ({}) usage [pooled: {}, created: {}, acquired: {} released: {}, discarded: {}]",
                        name, id, uri, pooled, created, acquired, released, discarded);
            }
        }
    }

    /**
     * Represents utilization statistics
     */
    final class UtilizationStatistics implements ExchangeFactory.Statistics {

        boolean statisticsEnabled;
        final LongAdder created = new LongAdder();
        final LongAdder acquired = new LongAdder();
        final LongAdder released = new LongAdder();
        final LongAdder discarded = new LongAdder();

        @Override
        public void reset() {
            created.reset();
            acquired.reset();
            released.reset();
            discarded.reset();
        }

        @Override
        public long getCreatedCounter() {
            return created.longValue();
        }

        @Override
        public long getAcquiredCounter() {
            return acquired.longValue();
        }

        @Override
        public long getReleasedCounter() {
            return released.longValue();
        }

        @Override
        public long getDiscardedCounter() {
            return discarded.longValue();
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        @Override
        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }
    }

}
