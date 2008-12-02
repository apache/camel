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
package org.apache.camel.component.atom;

import java.util.Date;

import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.feed.FeedComponent;
import org.apache.camel.component.feed.FeedConsumer;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * An <a href="http://activemq.apache.org/camel/atom.html">Atom Endpoint</a>.
 *
 * @version $Revision$
 */
public class AtomEndpoint extends FeedEndpoint {
    /**
     * Header key for the {@link org.apache.abdera.model.Feed} object is stored on the in message on the exchange.
     */
    public static final String HEADER_ATOM_FEED = "org.apache.camel.component.atom.feed";
    
    public AtomEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component, feedUri);
    }

    public AtomEndpoint(String endpointUri, String feedUri) {
        super(endpointUri, feedUri);
    }

    public AtomEndpoint(String endpointUri) {
        super(endpointUri);
    }   

    @Override
    public Exchange createExchange(Object feed) {
        Exchange exchange = createExchangeWithFeedHeader(feed, HEADER_ATOM_FEED);
        exchange.getIn().setBody(((Feed)feed).getEntries());
        return exchange;
    }

    @Override
    public Exchange createExchange(Object feed, Object entry) {
        Exchange exchange = createExchangeWithFeedHeader(feed, HEADER_ATOM_FEED);
        exchange.getIn().setBody(entry);
        return exchange;
    }

    @Override
    protected FeedConsumer createEntryPollingConsumer(FeedEndpoint feedEndpoint, Processor processor, boolean filter, Date lastUpdate) {
        return new AtomEntryPollingConsumer(this, processor, filter, lastUpdate);
    }  
    
    @Override
    protected FeedConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor) {
        return new AtomPollingConsumer(this, processor); 
    }
}
