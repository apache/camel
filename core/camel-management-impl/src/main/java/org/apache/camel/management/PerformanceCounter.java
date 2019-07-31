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
package org.apache.camel.management;

import org.apache.camel.Exchange;

/**
 * A counter that gathers performance metrics when an {@link org.apache.camel.Exchange} is routed in Camel.
 */
public interface PerformanceCounter {

    /**
     * Executed when an {@link org.apache.camel.Exchange} is about to be processed.
     *
     * @param exchange the exchange
     */
    void processExchange(Exchange exchange);

    /**
     * Executed when an {@link org.apache.camel.Exchange} is complete.
     *
     * @param exchange the exchange
     * @param time the time it took in millis to complete
     */
    void completedExchange(Exchange exchange, long time);

    /**
     * Executed when an {@link org.apache.camel.Exchange} failed.
     *
     * @param exchange the exchange
     */
    void failedExchange(Exchange exchange);

    /**
     * Are statistics enabled?
     * <p/>
     * They can be enabled and disabled at runtime
     *
     * @return whether statistics are enabled or not
     */
    boolean isStatisticsEnabled();

    /**
     * Sets whether statistics are enabled.
     * <p/>
     * They can be enabled and disabled at runtime
     *
     * @param statisticsEnabled whether statistics are enabled or not
     */
    void setStatisticsEnabled(boolean statisticsEnabled);

}
