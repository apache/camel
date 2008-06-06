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
package org.apache.camel.loanbroker.queue.version;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

//START SNIPPET: aggregation
public class BankResponseAggregationStrategy implements AggregationStrategy {

    // Here we put the bank response together
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Integer old = (Integer) oldExchange.getProperty("aggregated");
        Double oldRate = (Double) oldExchange.getIn().getHeader(Constants.PROPERTY_RATE);
        Double newRate = (Double) newExchange.getIn().getHeader(Constants.PROPERTY_RATE);
        Exchange result = null;
        if (old == null) {
            old = 1;
        }
        if (newRate >= oldRate) {
            result = oldExchange;
        } else {
            result = newExchange;
        }
        // Set the property for the completeness condition
        result.setProperty("aggregated", old + 1);
        return result;

    }

}
// END SNIPPET: aggregation