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

/**
 * A specialized {@link org.apache.camel.spi.AggregationRepository} which also supports
 * recovery. This usually requires a repository which is persisted.
 */
public interface RecoverableAggregationRepository extends AggregationRepository {

    /**
     * Scans the repository for {@link Exchange}s to be recovered
     * 
     * @param camelContext   the current CamelContext
     * @return the exchange ids for to be recovered
     */
    Set<String> scan(CamelContext camelContext);

    /**
     * Recovers the exchange with the given exchange id
     *
     * @param camelContext   the current CamelContext
     * @param exchangeId     exchange id
     * @return the recovered exchange or <tt>null</tt> if not found
     */
    Exchange recover(CamelContext camelContext, String exchangeId);

    /**
     * Sets the interval between recovery scans
     *
     * @param interval  the interval
     * @param timeUnit  the time unit
     */
    void setRecoveryInterval(long interval, TimeUnit timeUnit);

    /**
     * Sets the interval between recovery scans
     *
     * @param interval  the interval in millis
     */
    void setRecoveryInterval(long interval);

    /**
     * Gets the interval between recovery scans in millis.
     *
     * @return the interval in millis
     */
    long getRecoveryIntervalInMillis();

    /**
     * Sets whether or not recovery is enabled
     *
     * @param useRecovery whether or not recovery is enabled
     */
    void setUseRecovery(boolean useRecovery);

    /**
     * Whether or not recovery is enabled or not
     *
     * @return <tt>true</tt> to use recovery, <tt>false</tt> otherwise.
     */
    boolean isUseRecovery();

    /**
     * Sets an optional dead letter channel which exhausted recovered {@link Exchange}
     * should be send to.
     * <p/>
     * By default this option is disabled
     *
     * @param deadLetterUri  the uri of the dead letter channel
     */
    void setDeadLetterUri(String deadLetterUri);

    /**
     * Gets the dead letter channel
     *
     * @return  the uri of the dead letter channel
     */
    String getDeadLetterUri();

    /**
     * Sets an optional limit of the number of redelivery attempt of recovered {@link Exchange}
     * should be attempted, before its exhausted.
     * <p/>
     * When this limit is hit, then the {@link Exchange} is moved to the dead letter channel.
     * <p/>
     * By default this option is disabled
     *
     * @param maximumRedeliveries the maximum redeliveries
     */
    void setMaximumRedeliveries(int maximumRedeliveries);

    /**
     * Gets the maximum redelivery attempts to do before a recovered {@link Exchange} is doomed
     * as exhausted and moved to the dead letter channel.
     *
     * @return the maximum redeliveries
     */
    int getMaximumRedeliveries();

}
