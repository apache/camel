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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.jspecify.annotations.Nullable;

/**
 * A specialized {@link org.apache.camel.spi.AggregationRepository} which also supports recovery. This usually requires
 * a repository which is persisted.
 */
public interface RecoverableAggregationRepository extends AggregationRepository {

    /**
     * Scans the repository for {@link Exchange}s to be recovered
     *
     * @param  camelContext the current CamelContext
     * @return              the exchange ids for to be recovered
     */
    Set<String> scan(CamelContext camelContext);

    /**
     * Recovers the exchange with the given exchange id
     *
     * @param  camelContext the current CamelContext
     * @param  exchangeId   exchange id
     * @return              the recovered exchange or <code>null</code> if not found
     */
    @Nullable
    Exchange recover(CamelContext camelContext, String exchangeId);

    /**
     * Sets the interval between recovery scans
     *
     * @param      interval the interval
     * @param      timeUnit the time unit
     * @deprecated          use setRecoveryInterval
     */
    @Deprecated(since = "4.5.0")
    void setRecoveryInterval(long interval, TimeUnit timeUnit);

    /**
     * Sets the interval between recovery scans
     *
     * @param interval the interval in millis
     */
    void setRecoveryInterval(long interval);

    /**
     * Gets the interval between recovery scans in millis.
     *
     * @return the interval in millis
     */
    long getRecoveryInterval();

    /**
     * Gets the interval between recovery scans in millis.
     *
     * @return     the interval in millis
     * @deprecated use getRecoveryInterval
     */
    @Deprecated(since = "3.5.0")
    default long getRecoveryIntervalInMillis() {
        return getRecoveryInterval();
    }

    /**
     * Sets whether or not recovery is enabled
     *
     * @param useRecovery whether or not recovery is enabled
     */
    void setUseRecovery(boolean useRecovery);

    /**
     * Whether or not recovery is enabled
     *
     * @return <code>true</code> to use recovery, <code>false</code> otherwise.
     */
    boolean isUseRecovery();

    /**
     * Sets an optional dead letter channel which exhausted recovered {@link Exchange} should be sent to.
     * <p>
     * By default this option is disabled
     *
     * @param deadLetterUri the uri of the dead letter channel
     */
    void setDeadLetterUri(@Nullable String deadLetterUri);

    /**
     * Gets the dead letter channel
     *
     * @return the uri of the dead letter channel
     */
    @Nullable
    String getDeadLetterUri();

    /**
     * Sets an optional limit of the number of redelivery attempts of recovered {@link Exchange} should be attempted,
     * before it's exhausted.
     * <p>
     * When this limit is hit, then the {@link Exchange} is moved to the dead letter channel.
     * <p>
     * By default this option is disabled
     *
     * @param maximumRedeliveries the maximum redeliveries
     */
    void setMaximumRedeliveries(int maximumRedeliveries);

    /**
     * Gets the maximum redelivery attempts to do before a recovered {@link Exchange} is doomed as exhausted and moved
     * to the dead letter channel.
     *
     * @return the maximum redeliveries
     */
    int getMaximumRedeliveries();

    /**
     * Confirms the completion of the {@link Exchange} with a result.
     * <p>
     * This method is invoked instead of confirm() if the repository is recoverable. This allows possible recovery of
     * non-confirmed completed exchanges.
     * <p>
     * It is not invoked for a group that was completed by its first exchange (for example with a completion size of 1),
     * as such a group was never added to the repository, so there is nothing to confirm. It is also invoked for a
     * recovered exchange that was moved to the dead letter channel, and for an exchange that was discarded (by
     * discardOnCompletionTimeout, discardOnAggregationFailure or force discarding with an AggregateController).
     * <p>
     * When it returns false or throws an exception, the exchange (which was processed, moved to the dead letter
     * channel, or discarded) is kept as not confirmed: the recover task does not recover it, and invokes this method
     * again for it, as long as {@link #scan(CamelContext)} returns its exchange id.
     *
     * @param  camelContext the current CamelContext
     * @param  exchangeId   exchange id to confirm
     * @return              true if the exchange was successfully removed, else false.
     */
    default boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
        confirm(camelContext, exchangeId);
        return true;
    }

}
