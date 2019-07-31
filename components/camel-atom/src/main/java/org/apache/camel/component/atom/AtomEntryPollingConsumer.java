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

import java.io.IOException;
import java.util.Date;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.ParseException;
import org.apache.camel.Processor;
import org.apache.camel.component.feed.EntryFilter;
import org.apache.camel.component.feed.FeedEntryPollingConsumer;
import org.apache.camel.util.ObjectHelper;

/**
 * Consumer to poll atom feeds and return each entry from the feed step by step.
 */
public class AtomEntryPollingConsumer extends FeedEntryPollingConsumer {
    private Document<Feed> document;

    public AtomEntryPollingConsumer(AtomEndpoint endpoint, Processor processor, boolean filter, Date lastUpdate, boolean throttleEntries) {
        super(endpoint, processor, filter, lastUpdate, throttleEntries);
    }

    private Document<Feed> getDocument() throws IOException, ParseException {
        if (document == null) {
            if (ObjectHelper.isEmpty(endpoint.getUsername()) || ObjectHelper.isEmpty(endpoint.getPassword())) {
                document = AtomUtils.parseDocument(endpoint.getFeedUri());
            } else {
                document = AtomUtils.parseDocument(endpoint.getFeedUri(), endpoint.getUsername(), endpoint.getPassword());
            }
            Feed root = document.getRoot();
            if (endpoint.isSortEntries()) {
                sortEntries(root);
            }
            list = root.getEntries();
            entryIndex = list.size() - 1;
        }
        return document;
    }

    protected void sortEntries(Feed feed) {
        feed.sortEntriesByUpdated(true);
    }

    @Override
    protected void populateList(Object feed) throws ParseException, IOException {
        // list is populated already in the createFeed method
    }

    @Override
    protected Object createFeed() throws IOException {
        return getDocument().getRoot();
    }

    @Override
    protected void resetList() {
        document = null;
    }

    @Override
    protected EntryFilter createEntryFilter(Date lastUpdate) {
        return new UpdatedDateFilter(lastUpdate);
    }
}
