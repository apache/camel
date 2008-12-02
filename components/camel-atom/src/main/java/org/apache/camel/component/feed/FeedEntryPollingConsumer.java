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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Consumer to poll feeds and return each entry from the feed step by step.
 *
 */
public abstract class FeedEntryPollingConsumer extends FeedPollingConsumer {
    protected int entryIndex;
    protected EntryFilter entryFilter;
    protected List list;

    public FeedEntryPollingConsumer(FeedEndpoint endpoint, Processor processor, boolean filter, Date lastUpdate) {
        super(endpoint, processor);
        if (filter) {
            entryFilter = createEntryFilter(lastUpdate);
        }
    }

    public void poll() throws Exception {
        Object feed = createFeed();
        populateList(feed);   

        while (hasNextEntry()) {
            Object entry = list.get(entryIndex--);

            boolean valid = true;
            if (entryFilter != null) {
                valid = entryFilter.isValidEntry(endpoint, feed, entry);
            }
            if (valid) {
                Exchange exchange = endpoint.createExchange(feed, entry);
                getProcessor().process(exchange);
                // return and wait for the next poll to continue from last time (this consumer is stateful)
                return;
            }
        }

        // reset list to be able to poll again
        resetList();
    }

    protected abstract EntryFilter createEntryFilter(Date lastUpdate);
    
    protected abstract void resetList();

    protected abstract void populateList(Object feed) throws Exception; 
    
    private boolean hasNextEntry() {
        return entryIndex >= 0;
    }
}
