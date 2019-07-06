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

import org.apache.camel.Exchange;

/**
 * Allows to be used in combination with <tt>NotifyBuilder</tt> as external predicate implementations to compute
 * if the exchange matches.
 * <p/>
 * This is used by the mock endpoint, for example.
 */
public interface NotifyBuilderMatcher {

    /**
     * When an exchange was received
     *
     * @param exchange the exchange
     */
    void notifyBuilderOnExchange(Exchange exchange);

    /**
     * Whether the predicate matches
     */
    boolean notifyBuilderMatches();

    /**
     * Reset state
     */
    void notifyBuilderReset();
    
}
