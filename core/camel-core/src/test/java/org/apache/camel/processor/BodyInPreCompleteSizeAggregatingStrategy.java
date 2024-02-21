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
package org.apache.camel.processor;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

public class BodyInPreCompleteSizeAggregatingStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        String oldBody = oldExchange.getIn().getBody(String.class);
        String newBody = newExchange.getIn().getBody(String.class);
        oldExchange.getIn().setBody(oldBody + "+" + newBody);
        return oldExchange;
    }

    @Override
    public boolean canPreComplete() {
        return true;
    }

    @Override
    public boolean preComplete(Exchange oldExchange, Exchange newExchange) {
        String key = newExchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, String.class);
        int size = newExchange.getProperty(Exchange.AGGREGATED_SIZE, int.class);

        if ("123".equals(key)) {
            return size > 2;
        } else if ("456".equals(key)) {
            return size > 3;
        } else {
            return true;
        }
    }
}
