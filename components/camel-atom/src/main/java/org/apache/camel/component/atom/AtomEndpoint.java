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
package org.apache.camel.component.atom;

import java.util.Date;

import org.apache.abdera.model.Feed;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.component.feed.FeedPollingConsumer;
import org.apache.camel.spi.UriEndpoint;

/**
 * The atom component is used for consuming Atom RSS feeds.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "atom", title = "Atom", syntax = "atom:feedUri", consumerOnly = true,
        label = "rss", lenientProperties = true)
public class AtomEndpoint extends FeedEndpoint {

    public AtomEndpoint() {
    }

    public AtomEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component, feedUri);
    }

    @Override
    public Exchange createExchange(Object feed) {
        Exchange exchange = createExchangeWithFeedHeader(feed, AtomConstants.ATOM_FEED);
        exchange.getIn().setBody(((Feed)feed).getEntries());
        return exchange;
    }

    @Override
    public Exchange createExchange(Object feed, Object entry) {
        Exchange exchange = createExchangeWithFeedHeader(feed, AtomConstants.ATOM_FEED);
        exchange.getIn().setBody(entry);
        return exchange;
    }

    @Override
    protected FeedPollingConsumer createEntryPollingConsumer(FeedEndpoint feedEndpoint, Processor processor, boolean filter, Date lastUpdate, boolean throttleEntries) throws Exception {
        AtomEntryPollingConsumer answer = new AtomEntryPollingConsumer(this, processor, filter, lastUpdate, throttleEntries);
        configureConsumer(answer);
        return answer;
    }  
    
    @Override
    protected FeedPollingConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor) throws Exception {
        AtomPollingConsumer answer = new AtomPollingConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }
}
