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

import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.component.feed.FeedPollingConsumer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Poll Atom RSS feeds.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "atom", title = "Atom", syntax = "atom:feedUri", consumerOnly = true,
             category = { Category.DOCUMENT }, lenientProperties = true, headersClass = AtomConstants.class)
public class AtomEndpoint extends FeedEndpoint implements EndpointServiceLocation {

    @UriParam(label = "consumer,filter", defaultValue = "true", description = "Option to use the Idempotent "
                                                                              + "Consumer EIP pattern to let Camel skip already processed entries. Will by default use a memory based "
                                                                              + "LRUCache that holds 1000 entries. Only works when splitEntries = true.")
    protected boolean idempotent = true;
    @UriParam(label = "consumer,filter,advanced", defaultValue = "default", enums = "default,repository",
              description = "A pluggable strategy org.apache.camel.component.FeedIdempotentStrategy "
                            + "to use when checking idempotency. Camel provides two implementations out of the box: default and repository. The updated strategy is used as default if idempotent = true. "
                            + "The default strategy checks the Atom entrys updated or published date is newer than the previously read entry. "
                            + "You can provide your own implementation of the org.apache.camel.component.FeedIdempotentStrategy and refer to it using the # notation.")
    protected AtomIdempotentStrategy idempotentStrategy = new ItemUpdatedIdempotentStrategy();

    public AtomEndpoint() {
    }

    public AtomEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component, feedUri);
    }

    @Override
    public String getServiceUrl() {
        return feedUri;
    }

    @Override
    public String getServiceProtocol() {
        return "atom";
    }

    @Override
    public Exchange createExchange(Object feed) {
        Exchange exchange = createExchangeWithFeedHeader(feed, AtomConstants.ATOM_FEED);
        exchange.getIn().setBody(feed);
        return exchange;
    }

    @Override
    public Exchange createExchange(Object feed, Object entry) {
        Exchange exchange = createExchangeWithFeedHeader(feed, AtomConstants.ATOM_FEED);
        exchange.getIn().setBody(entry);
        return exchange;
    }

    @Override
    protected FeedPollingConsumer createEntryPollingConsumer(
            FeedEndpoint feedEndpoint, Processor processor, boolean throttleEntries)
            throws Exception {
        AtomEntryPollingConsumer answer = new AtomEntryPollingConsumer(this, processor, throttleEntries);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected FeedPollingConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor) throws Exception {
        AtomPollingConsumer answer = new AtomPollingConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public FeedEndpoint setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
        return this;
    }

    public AtomIdempotentStrategy getIdempotentStrategy() {
        return idempotentStrategy;
    }

    public FeedEndpoint setIdempotentStrategy(AtomIdempotentStrategy idempotentStrategy) {
        this.idempotentStrategy = idempotentStrategy;
        return this;
    }
}
