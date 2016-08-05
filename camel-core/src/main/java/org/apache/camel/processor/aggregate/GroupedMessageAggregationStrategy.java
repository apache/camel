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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;

/**
 * Aggregate all {@link Message} into a single combined Exchange holding all the
 * aggregated messages in a {@link List} of {@link Message} as the message body.
 * 
 * This aggregation strategy can used in combination with {@link org.apache.camel.processor.Splitter} to batch messages
 * 
 * @version
 */
public class GroupedMessageAggregationStrategy extends AbstractListAggregationStrategy<Message> {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            // for the first time we must create a new empty exchange as the
            // holder, as the outgoing exchange
            // must not be one of the grouped exchanges, as that causes a
            // endless circular reference
            oldExchange = new DefaultExchange(newExchange);
        }
        return super.aggregate(oldExchange, newExchange);
    }

    @Override
    public Message getValue(Exchange exchange) {
        return exchange.getIn();
    }
}
