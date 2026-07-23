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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.jspecify.annotations.Nullable;

/**
 * Factory used by {@link Consumer} (and {@link org.apache.camel.PollingConsumer}) to create the {@link Exchange} that
 * carries an incoming message into Camel.
 * <p/>
 * This factory governs only the exchanges that enter Camel from the outside world via a consumer. Sub-exchanges created
 * internally (e.g., by the Splitter EIP) are outside this contract and use {@link ProcessorExchangeFactory} instead.
 * <p/>
 * The factory is pluggable to support two exchange-lifecycle strategies:
 * <ul>
 * <li><b>default</b> — allocates a fresh {@link Exchange} for every inbound message; simple and GC-friendly for
 * low-volume routes.</li>
 * <li><b>pooled</b> — reuses {@link org.apache.camel.PooledExchange} instances from an object pool; reduces
 * garbage-collection pressure on high-throughput routes by recycling the exchange and its internal state after each
 * routing cycle completes.</li>
 * </ul>
 * Camel selects the strategy at startup based on the {@code exchangeFactory} configuration option on the
 * {@link org.apache.camel.CamelContext}.
 *
 * @see   ProcessorExchangeFactory
 * @see   org.apache.camel.PooledExchange
 * @since 3.9
 */
public interface ExchangeFactory extends PooledObjectFactory<Exchange>, NonManagedService, RouteIdAware {

    /**
     * Service factory key.
     */
    String FACTORY = "exchange-factory";

    /**
     * The consumer using this factory.
     */
    @Nullable
    Consumer getConsumer();

    /**
     * Creates a new {@link ExchangeFactory} that is private for the given consumer.
     *
     * @param  consumer the consumer that will use the created {@link ExchangeFactory}
     * @return          the created factory.
     */
    ExchangeFactory newExchangeFactory(Consumer consumer);

    /**
     * Gets a new {@link Exchange}
     *
     * @param autoRelease whether to auto release the exchange when routing is complete via {@link UnitOfWork}
     */
    Exchange create(boolean autoRelease);

    /**
     * Gets a new {@link Exchange}
     *
     * @param autoRelease  whether to auto release the exchange when routing is complete via {@link UnitOfWork}
     * @param fromEndpoint the from endpoint
     */
    Exchange create(Endpoint fromEndpoint, boolean autoRelease);

    /**
     * Releases the exchange back into the pool
     *
     * @param  exchange the exchange
     * @return          true if released into the pool, or false if something went wrong and the exchange was discarded
     */
    default boolean release(Exchange exchange) {
        return true;
    }

}
