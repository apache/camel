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
package org.apache.camel.processor.aggregate;

import org.apache.camel.Exchange;

/**
 * A strategy for aggregating two exchanges together into a single exchange.
 * Possible implementations include performing some kind of combining or delta
 * processing, such as adding line items together into an invoice or just using
 * the newest exchange and removing old exchanges such as for state tracking or
 * market data prices; where old values are of little use.
 * 
 * @version $Revision: 1.1 $
 */
public interface AggregationStrategy {

    /**
     * Aggregates an old and new exchange together to create a single combined
     * exchange
     *
     * @param oldExchange the oldest exchange
     * @param newExchange the newest exchange
     * @return a combined composite of the two exchanges
     */
    Exchange aggregate(Exchange oldExchange, Exchange newExchange);
}
