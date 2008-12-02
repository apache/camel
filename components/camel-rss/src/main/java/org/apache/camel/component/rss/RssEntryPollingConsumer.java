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
import java.util.List;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Consumer to poll RSS feeds and return each entry from the feed step by step.
 *
 */
public class RssEntryPollingConsumer extends RssPollingConsumer {
    private int entryIndex;
    private List<SyndEntry> list;

    public RssEntryPollingConsumer(RssEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public RssEntryPollingConsumer(RssEndpoint endpoint, Processor processor, boolean filter, Date lastUpdate) {
        this(endpoint, processor);
    }

    public void poll() throws Exception {
        SyndFeed feed = createFeed();
        populateList(feed);        

        while (hasNextEntry()) {
            SyndEntry entry = list.get(entryIndex--);
            Exchange exchange = endpoint.createExchange(feed, entry);
            getProcessor().process(exchange);
            // return and wait for the next poll to continue from last time (this consumer is stateful)
            return;
        }
        
        list = null;
    }

    private void populateList(SyndFeed feed) {
        if (list == null) {
            list = feed.getEntries();
            entryIndex = list.size() - 1;
        }
    }

    private boolean hasNextEntry() {
        return entryIndex >= 0;
    }

}
