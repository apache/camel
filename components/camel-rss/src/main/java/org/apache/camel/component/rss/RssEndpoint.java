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
package org.apache.camel.component.rss;

import java.util.Date;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.atom.AtomEntryPollingConsumer;
import org.apache.camel.component.atom.AtomPollingConsumer;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedConsumer;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.impl.DefaultPollingEndpoint;

/**
 * An <a href="http://activemq.apache.org/camel/rss.html">RSS Endpoint</a>.
 * 
 */
public class RssEndpoint extends FeedEndpoint {
    /**
     * Header key for the {@link com.sun.syndication.feed.synd.SyndFeed} object is stored on the in message on the exchange.
     */
    public static final String HEADER_RSS_FEED = "org.apache.camel.component.rss.feed";   

    public RssEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component, feedUri);
    }

    public RssEndpoint(String endpointUri, String feedUri) {
        super(endpointUri, feedUri);
    }

    public RssEndpoint(String endpointUri) {
        super(endpointUri);
    }      
    
    @Override
    public Exchange createExchange(Object feed) {
        Exchange exchange = createExchangeWithFeedHeader(feed, HEADER_RSS_FEED);
        exchange.getIn().setBody(((SyndFeed)feed).getEntries());
        return exchange;
    }

    @Override
    public Exchange createExchange(Object feed, Object entry) {
        Exchange exchange = createExchangeWithFeedHeader(feed, HEADER_RSS_FEED);
        exchange.getIn().setBody(entry);
        return exchange;
    }

    @Override
    protected FeedConsumer createEntryPollingConsumer(FeedEndpoint feedEndpoint, Processor processor, boolean filter, Date lastUpdate) {
        return new RssEntryPollingConsumer(this, processor, filter, lastUpdate);
    }  
    
    @Override
    protected FeedConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor) {
        return new RssPollingConsumer(this, processor); 
    }
}
