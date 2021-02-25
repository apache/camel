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
package org.apache.camel.spi;

import java.util.Collection;

import org.apache.camel.StaticService;

/**
 * Manages {@link ExchangeFactory}.
 */
public interface ExchangeFactoryManager extends StaticService {

    /**
     * Adds the {@link ExchangeFactory} to be managed.
     *
     * @param exchangeFactory the exchange factory
     */
    void addExchangeFactory(ExchangeFactory exchangeFactory);

    /**
     * Removes the {@link ExchangeFactory} from being managed (such as when a route is stopped/removed) or during
     * shutdown.
     *
     * @param exchangeFactory the exchange factory
     */
    void removeExchangeFactory(ExchangeFactory exchangeFactory);

    /**
     * Returns a read-only view of the managed factories.
     */
    Collection<ExchangeFactory> getExchangeFactories();

    /**
     * Number of consumers currently being managed
     */
    int getConsumerCounter();

    /**
     * The capacity the pool (for each consumer) uses for storing exchanges. The default capacity is 100.
     */
    int getCapacity();

    /**
     * Number of currently exchanges being pooled (if pooled is in use)
     */
    int getPooledCounter();

    /**
     * Whether statistics is enabled.
     */
    boolean isStatisticsEnabled();

    /**
     * Whether statistics is enabled.
     */
    void setStatisticsEnabled(boolean statisticsEnabled);

    /**
     * Reset the statistics
     */
    void resetStatistics();

    /**
     * Purges the internal caches (if pooled)
     */
    void purge();

    /**
     * Aggregated statistics for all the managed exchange factories
     */
    ExchangeFactory.Statistics getStatistics();

}
