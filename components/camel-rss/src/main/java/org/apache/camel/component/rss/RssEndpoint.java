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

import java.io.InputStream;
import java.net.URL;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

/**
 * An <a href="http://activemq.apache.org/camel/rss.html">RSS Endpoint</a>.
 * 
 */
public class RssEndpoint extends DefaultPollingEndpoint {
    /**
     * Header key for the {@link com.sun.syndication.feed.synd.SyndFeed} object is stored on the in message on the exchange.
     */
    public static final String HEADER_RSS_FEED = "org.apache.camel.component.rss.feed";
    private String rssUri;   
    private boolean splitEntries = true;    

    public RssEndpoint(String uri, RssComponent component, String rssUri) {
        super(uri, component);
        this.setRssUri(rssUri);
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("RssProducer is not implemented");
    }  
    
    public Consumer createConsumer(Processor processor) throws Exception {
        RssConsumerSupport answer;
        if (isSplitEntries()) {
            answer = new RssEntryPollingConsumer(this, processor);
        } else {
            answer = new RssPollingConsumer(this, processor);
        }
        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a feed,
        // so we override with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(RssConsumerSupport.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);
        return answer;
    }

    public Exchange createExchange(SyndFeed feed) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(feed.getEntries());
        exchange.getIn().setHeader(HEADER_RSS_FEED, feed);
        return exchange;
    }    

    public Exchange createExchange(SyndFeed feed, SyndEntry entry) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(entry);
        exchange.getIn().setHeader(HEADER_RSS_FEED, feed);
        return exchange;
    }
    
    public boolean isSingleton() {
        return true;
    }

    public void setRssUri(String rssUri) {
        this.rssUri = rssUri;
    }

    public String getRssUri() {
        return rssUri;
    }

    public void setSplitEntries(boolean splitEntries) {
        this.splitEntries = splitEntries;
    }

    public boolean isSplitEntries() {
        return splitEntries;
    }   
    
}
