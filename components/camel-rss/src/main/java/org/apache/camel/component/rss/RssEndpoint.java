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
package org.apache.camel.component.rss;

import java.util.Arrays;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.component.feed.FeedPollingConsumer;
import org.apache.camel.spi.UriEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poll RSS feeds.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "rss", extendsScheme = "atom", title = "RSS",
             syntax = "rss:feedUri", consumerOnly = true, category = { Category.DOCUMENT }, lenientProperties = true,
             headersClass = RssConstants.class)
public class RssEndpoint extends FeedEndpoint {
    protected static final Logger LOG = LoggerFactory.getLogger(RssEndpoint.class);

    public RssEndpoint() {
    }

    public RssEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component, feedUri);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("RssProducer is not implemented");
    }

    @Override
    public Exchange createExchange(Object feed) {
        Exchange exchange = createExchangeWithFeedHeader(feed, RssConstants.RSS_FEED);
        exchange.getIn().setBody(feed);
        return exchange;
    }

    @Override
    public Exchange createExchange(Object feed, Object entry) {
        Exchange exchange = createExchangeWithFeedHeader(feed, RssConstants.RSS_FEED);
        SyndFeed newFeed;
        try {
            newFeed = (SyndFeed) ((SyndFeed) feed).clone();
            newFeed.setEntries(Arrays.asList((SyndEntry) entry));
        } catch (CloneNotSupportedException e) {
            LOG.debug("Could not create a new feed. This exception will be ignored.", e);
            newFeed = null;
        }
        exchange.getIn().setBody(newFeed);
        return exchange;
    }

    @Override
    protected FeedPollingConsumer createEntryPollingConsumer(
            FeedEndpoint feedEndpoint, Processor processor, boolean throttleEntries)
            throws Exception {
        RssEntryPollingConsumer answer = new RssEntryPollingConsumer(this, processor, throttleEntries);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected FeedPollingConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor) throws Exception {
        RssPollingConsumer answer = new RssPollingConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }
}
