/**
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
 * Access to a repository to store aggregated exchanges to support pluggable implementations.
 *  
 * @version $Revision$
 */
public interface AggregationRepository<K> {

    /**
     * Add the given {@link Exchange} under the correlation key.
     * <p/>
     * Will replace any existing exchange.
     *
     * @param camelContext the current CamelContext
     * @param key  the correlation key
     * @param exchange the aggregated exchange
     * @return the old exchange if any existed
     */
    Exchange add(CamelContext camelContext, K key, Exchange exchange);

    /**
     * Gets the given exchange with the correlation key
     *
     * @param camelContext the current CamelContext
     * @param key the correlation key
     * @return the exchange, or <tt>null</tt> if no exchange was previously added
     */
    Exchange get(CamelContext camelContext, K key);

    /**
     * Removes the exchange with the given correlation key
     *
     * @param camelContext the current CamelContext
     * @param key the correlation key
     */
    void remove(CamelContext camelContext, K key);

}
