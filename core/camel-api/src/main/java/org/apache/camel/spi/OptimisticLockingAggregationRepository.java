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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;

/**
 * A specialized {@link org.apache.camel.spi.AggregationRepository} which also supports
 * optimistic locking.
 *
 * If the underlying implementation cannot perform optimistic locking, it should
 * not implement this interface.
 *
 * @see org.apache.camel.processor.aggregate.MemoryAggregationRepository
 */
public interface OptimisticLockingAggregationRepository extends AggregationRepository {

    /**
     * {@link Exception} used by an {@code AggregationRepository} to indicate that an optimistic
     * update error has occurred and that the operation should be retried by the caller.
     * <p/>
     */
    class OptimisticLockingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Add the given {@link org.apache.camel.Exchange} under the correlation key.
     * <p/>
     * Will perform optimistic locking to replace expected existing exchange with the new supplied exchange.
     * <p/>
     * If the {@code oldExchange} is null the underlying implementation is to assume this is the very first Exchange for the
     * supplied correlation key. When the implementation comes to store to the Exchange if there is already an existing
     * Exchange present for this correlation key the implementation should throw an OptimisticLockingException.
     * <p/>
     * If the {@code oldExchange} is not null the underlying implementation should use it to compare with the existing exchange
     * when doing an atomic compare-and-set/swap operation.
     * <p/>
     * The implementation may achieve this by storing a version identifier in the Exchange as a parameter. Set before
     * returning from {@link AggregationRepository#get(org.apache.camel.CamelContext, String)}} and retrieved from the
     * exchange when passed to {@link AggregationRepository#add(org.apache.camel.CamelContext, String, org.apache.camel.Exchange)}.
     * <p/>
     * Note: The {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository} is an exception to this recommendation.
     * It uses the {@code oldExchange}'s Object identify to perform it's compare-and-set/swap operation, instead of a version
     * parameter. This is not the recommended approach, and should be avoided.
     * <p/>
     * The {@link org.apache.camel.processor.aggregate.AggregateProcessor} will ensure that the exchange received from
     * {@link OptimisticLockingAggregationRepository#get(org.apache.camel.CamelContext, String)} is passed as {@code oldExchange},
     * and that the aggregated exchange received from the {@link org.apache.camel.processor.aggregate.AggregationStrategy}
     * is passed as the {@code newExchange}.
     *
     * @param camelContext   the current CamelContext
     * @param key            the correlation key
     * @param oldExchange    the old exchange that is expected to exist when replacing with the new exchange
     * @param newExchange    the new aggregated exchange, to replace old exchange
     * @return the old exchange if any existed
     * @throws OptimisticLockingException This should be thrown when the currently stored exchange differs from the supplied oldExchange.
     */
    Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) throws OptimisticLockingException;

    /**
     * Removes the given Exchange when both the supplied key and Exchange are present in the repository. If the supplied Exchange
     * does not match the Exchange actually stored against the key this method should throw an OptimisticLockingException
     * to indicate that the value of the correlation key has changed from the expected value.
     * <p/>
     * @param camelContext   the current CamelContext
     * @param key            the correlation key
     * @param exchange       the exchange to remove
     * @throws OptimisticLockingException This should be thrown when the exchange has already been deleted, or otherwise modified.
     */
    @Override
    void remove(CamelContext camelContext, String key, Exchange exchange) throws OptimisticLockingException;
}
