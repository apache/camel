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
package org.apache.camel.example;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

/**
 * Aggregate two numbers
 */
// START SNIPPET: e1
public class NumberAggregationStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        // check for stop command
        String input = newExchange.getIn().getBody(String.class);
        if ("STOP".equalsIgnoreCase(input)) {
            return oldExchange;
        }

        Integer num1 = oldExchange.getIn().getBody(Integer.class);
        Integer num2 = newExchange.getIn().getBody(Integer.class);

        // just avoid bad inputs by assuming its a 0 value
        Integer num3 = (num1 != null ? num1 : 0) + (num2 != null ? num2 : 0);
        oldExchange.getIn().setBody(num3);

        return oldExchange;
    }

}
// END SNIPPET: e1
