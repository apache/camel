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
package org.apache.camel.component.feed;

import java.util.Date;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * A base class for feed (atom, RSS) endpoints.
 */
public abstract class FeedEndpoint extends DefaultPollingEndpoint {

    protected String feedUri;
    protected boolean splitEntries = true;
    protected Date lastUpdate;
    protected boolean filter = true;
    private boolean feedHeader = true;
    private boolean sortEntries;

    public FeedEndpoint(String endpointUri, FeedComponent component, String feedUri) {
        super(endpointUri, component);
        this.feedUri = feedUri;

        ObjectHelper.notNull(feedUri, "feedUri property");
    }

    public FeedEndpoint(String endpointUri, String feedUri) {
        this(endpointUri);
        this.feedUri = feedUri;

        ObjectHelper.notNull(feedUri, "feedUri property");
    }

    public FeedEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("FeedProducer is not implemented");
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        FeedPollingConsumer answer;
        if (isSplitEntries()) {
            answer = createEntryPollingConsumer(this, processor, filter, lastUpdate);
        } else {
            answer = createPollingConsumer(this, processor);
        }
        
        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a feed,
        // so we override with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(FeedPollingConsumer.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);
        return answer;
    }

    protected abstract FeedPollingConsumer createPollingConsumer(FeedEndpoint feedEndpoint, Processor processor);

    protected abstract FeedPollingConsumer createEntryPollingConsumer(FeedEndpoint feedEndpoint, Processor processor, boolean filter, Date lastUpdate);

    protected Exchange createExchangeWithFeedHeader(Object feed, String header) {
        Exchange exchange = createExchange();
        if (isFeedHeader()) {
            exchange.getIn().setHeader(header, feed);
        }
        return exchange;
    }    
    
    /**
     * Creates an Exchange with the entries as the in body.
     *
     * @param feed   the atom feed
     * @return the created exchange
     */
    public abstract Exchange createExchange(Object feed);

    /**
     * Creates an Exchange with the given entry as the in body.
     *
     * @param feed   the feed
     * @param entry  the entry as the in body
     * @return the created exchange
     */
    public abstract Exchange createExchange(Object feed, Object entry);

    // Properties
    //-------------------------------------------------------------------------

    public String getFeedUri() {
        return feedUri;
    }

    public void setFeedUri(String feedUri) {
        this.feedUri = feedUri;
    }

    public boolean isSplitEntries() {
        return splitEntries;
    }

    /**
     * Sets whether or not entries should be sent individually or whether the entire
     * feed should be sent as a single message
     */
    public void setSplitEntries(boolean splitEntries) {
        this.splitEntries = splitEntries;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    /**
     * Sets the timestamp to be used for filtering entries from the atom feeds.
     * This options is only in conjunction with the splitEntries.
     */
    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isFilter() {
        return filter;
    }

    /**
     * Sets whether to use filtering or not of the entries.
     */
    public void setFilter(boolean filter) {
        this.filter = filter;
    }

    /**
     * Sets whether to add the feed object as a header
     */
    public void setFeedHeader(boolean feedHeader) {
        this.feedHeader = feedHeader;
    }

    public boolean isFeedHeader() {
        return feedHeader;
    }

    /**
     * Sets whether to sort entries by published date. Only works when splitEntries = true.
     */
    public void setSortEntries(boolean sortEntries) {
        this.sortEntries = sortEntries;
    }

    public boolean isSortEntries() {
        return sortEntries;
    }      
    
    // Implementation methods
    //-------------------------------------------------------------------------

}
