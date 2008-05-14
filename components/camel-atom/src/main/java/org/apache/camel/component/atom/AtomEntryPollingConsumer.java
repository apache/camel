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

import java.io.IOException;
import java.util.List;
import java.util.Date;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.ParseException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.PollingConsumerSupport;

/**
 * Consumer to poll atom feeds and return each entry from the feed step by step.
 *
 * @version $Revision$
 */
public class AtomEntryPollingConsumer extends PollingConsumerSupport<Exchange> {
    private final AtomEndpoint endpoint;
    private Document<Feed> document;
    private int entryIndex;
    private EntryFilter entryFilter;
    private List<Entry> list;

    public AtomEntryPollingConsumer(AtomEndpoint endpoint, boolean filter, Date lastUpdate) {
        super(endpoint);
        this.endpoint = endpoint;
        if (filter) {
            entryFilter = new UpdatedDateFilter(lastUpdate);
        }
    }

    public Exchange receiveNoWait() {
        try {
            getDocument();
            Feed feed = document.getRoot();

            while (hasNextEntry()) {
                Entry entry = list.get(entryIndex--);

                boolean valid = true;
                if (entryFilter != null) {
                    valid = entryFilter.isValidEntry(endpoint, document, entry);
                }
                if (valid) {
                    return endpoint.createExchange(feed, entry);
                }
            }

            // reset document to be able to poll again
            document = null;
            return null;
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Exchange receive() {
        return receiveNoWait();
    }

    public Exchange receive(long timeout) {
        return receiveNoWait();
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }

    private Document<Feed> getDocument() throws IOException, ParseException {
        if (document == null) {
            document = AtomUtils.parseDocument(endpoint.getAtomUri());
            list = document.getRoot().getEntries();
            entryIndex = list.size() - 1;
        }
        return document;
    }

    private boolean hasNextEntry() {
        return entryIndex >= 0;
    }

}
