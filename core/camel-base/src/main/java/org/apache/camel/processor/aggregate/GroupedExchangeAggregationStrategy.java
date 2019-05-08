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
package org.apache.camel.processor.aggregate;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;

/**
 * Aggregate all exchanges into a single combined Exchange holding all the aggregated exchanges
 * in a {@link List} of {@link Exchange} as the message body.
 * <p/>
 * <b>Important:</b> This strategy is not to be used with the <a href="http://camel.apache.org/content-enricher.html">Content Enricher</a> EIP
 * which is enrich or pollEnrich.
 */
public class GroupedExchangeAggregationStrategy extends AbstractListAggregationStrategy<Exchange> {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            // for the first time we must create a new empty exchange as the holder, as the outgoing exchange
            // must not be one of the grouped exchanges, as that causes a endless circular reference
            oldExchange = new DefaultExchange(newExchange);
        }
        return super.aggregate(oldExchange, newExchange);
    }

    @Override
    public Exchange getValue(Exchange exchange) {
        return exchange;
    }

}

