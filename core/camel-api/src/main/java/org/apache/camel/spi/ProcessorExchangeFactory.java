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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NonManagedService;
import org.apache.camel.Processor;

/**
 * Factory used by {@link org.apache.camel.Processor} implementations (EIPs) when they create copies of the current
 * {@link Exchange} for sub-routing.
 * <p/>
 * EIPs such as WireTap, Multicast, and Splitter each create one or more derived exchanges (copies or correlated copies)
 * to fan out processing. This factory gives those EIPs the same exchange-pooling benefit as {@link ExchangeFactory}
 * gives to consumers, without conflating the two lifecycle boundaries.
 * <p/>
 * Like {@link ExchangeFactory}, this factory is pluggable:
 * <ul>
 * <li><b>default</b> — creates a new {@link Exchange} copy per sub-routing invocation.</li>
 * <li><b>pooled</b> — reuses {@link org.apache.camel.PooledExchange} instances, recycling them after the sub-exchange
 * completes its portion of the route.</li>
 * </ul>
 * Each stateful EIP receives its own private {@code ProcessorExchangeFactory} instance via
 * {@link #newProcessorExchangeFactory(org.apache.camel.Processor)}.
 *
 * @see   ExchangeFactory
 * @see   org.apache.camel.PooledExchange
 * @since 3.10
 */
public interface ProcessorExchangeFactory extends PooledObjectFactory<Exchange>, NonManagedService, RouteIdAware, IdAware {

    /**
     * Service factory key.
     */
    String FACTORY = "processor-exchange-factory";

    /**
     * The processor using this factory.
     */
    Processor getProcessor();

    /**
     * Creates a new {@link ProcessorExchangeFactory} that is private for the given consumer.
     *
     * @param  processor the processor that will use the created {@link ProcessorExchangeFactory}
     * @return           the created factory.
     */
    ProcessorExchangeFactory newProcessorExchangeFactory(Processor processor);

    /**
     * Gets a copy of the given {@link Exchange}
     *
     * @param exchange original exchange
     */
    Exchange createCopy(Exchange exchange);

    /**
     * Gets a copy of the given {@link Exchange} and the copy is correlated to the source
     *
     * @param exchange original exchange
     * @param handover whether the on completion callbacks should be handed over to the new copy.
     */
    Exchange createCorrelatedCopy(Exchange exchange, boolean handover);

    /**
     * Gets a new {@link Exchange}
     */
    Exchange create(Endpoint fromEndpoint, ExchangePattern exchangePattern);

    /**
     * Releases the exchange back into the pool
     *
     * @param  exchange the exchange
     * @return          true if released into the pool, or false if something went wrong and the exchange was discarded
     */
    boolean release(Exchange exchange);

}
