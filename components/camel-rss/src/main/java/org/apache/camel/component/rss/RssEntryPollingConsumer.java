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

import java.util.Collections;
import java.util.Date;

import com.rometools.rome.feed.synd.SyndFeed;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.EntryFilter;
import org.apache.camel.component.feed.FeedEntryPollingConsumer;
import org.apache.camel.util.ObjectHelper;

/**
 * Consumer to poll RSS feeds and return each entry from the feed step by step.
 */
public class RssEntryPollingConsumer extends FeedEntryPollingConsumer {

    public RssEntryPollingConsumer(RssEndpoint endpoint, Processor processor, boolean filter, Date lastUpdate, boolean throttleEntries) {
        super(endpoint, processor, filter, lastUpdate, throttleEntries);
    }

    @Override
    protected void populateList(Object feed) throws Exception {
        if (list == null) {
            list = ((SyndFeed) feed).getEntries();
            if (endpoint.isSortEntries()) {
                sortEntries();
            }
            entryIndex = list.size() - 1;
        }
    }

    @SuppressWarnings("unchecked")
    protected void sortEntries() {
        Collections.sort(list, new RssDateComparator());
    }

    @Override
    protected Object createFeed() throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getUsername()) || ObjectHelper.isEmpty(endpoint.getPassword())) {
            return RssUtils.createFeed(endpoint.getFeedUri(), RssEntryPollingConsumer.class.getClassLoader());
        } else {
            return RssUtils.createFeed(endpoint.getFeedUri(), endpoint.getUsername(), endpoint.getPassword(), RssEntryPollingConsumer.class.getClassLoader());
        }
    }

    @Override
    protected void resetList() {
        list = null;
    }

    @Override
    protected EntryFilter createEntryFilter(Date lastUpdate) {
        return new UpdatedDateFilter(lastUpdate);
    }
}
