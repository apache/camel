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
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * An <a href="http://activemq.apache.org/camel/atom.html">Atom Endpoint</a>.
 *
 * @version $Revision$
 */
public class AtomEndpoint extends DefaultPollingEndpoint<Exchange> {

    /**
     * Header key for the {@link org.apache.abdera.model.Feed} object is stored on the in message on the exchange.
     */
    public static final String HEADER_ATOM_FEED = "org.apache.camel.component.atom.feed";

    private String atomUri;
    private boolean splitEntries = true;
    private Date lastUpdate;
    private boolean filter = true;

    public AtomEndpoint(String endpointUri, AtomComponent component, String atomUri) {
        super(endpointUri, component);
        this.atomUri = atomUri;

        ObjectHelper.notNull(atomUri, "atomUri property");
    }

    public AtomEndpoint(String endpointUri, String atomUri) {
        this(endpointUri);
        this.atomUri = atomUri;

        ObjectHelper.notNull(atomUri, "atomUri property");
    }

    public AtomEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer<Exchange> createProducer() throws Exception {
        throw new UnsupportedOperationException("AtomProducer is not implemented");
    }

    public Consumer<Exchange> createConsumer(Processor processor) throws Exception {
        AtomConsumerSupport answer;
        if (isSplitEntries()) {
            answer = new AtomEntryPollingConsumer(this, processor, filter, lastUpdate);
        } else {
            answer = new AtomPollingConsumer(this, processor);
        }
        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a feed,
        // so we override with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(AtomConsumerSupport.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);
        return answer;
    }

    /**
     * Creates an Exchange with the entries as the in body.
     *
     * @param feed   the atom feed
     * @return the created exchange
     */
    public Exchange createExchange(Feed feed) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(feed.getEntries());
        exchange.getIn().setHeader(HEADER_ATOM_FEED, feed);
        return exchange;
    }

    /**
     * Creates an Exchange with the given entry as the in body.
     *
     * @param feed   the atom feed
     * @param entry  the entry as the in body
     * @return the created exchange
     */
    public Exchange createExchange(Feed feed, Entry entry) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(entry);
        exchange.getIn().setHeader(HEADER_ATOM_FEED, feed);
        return exchange;
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getAtomUri() {
        return atomUri;
    }

    public void setAtomUri(String atomUri) {
        this.atomUri = atomUri;
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
     * Sets wether to use filtering or not of the entries.
     */
    public void setFilter(boolean filter) {
        this.filter = filter;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

}
