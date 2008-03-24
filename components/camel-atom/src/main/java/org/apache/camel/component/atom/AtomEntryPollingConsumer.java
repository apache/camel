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

import java.util.List;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.PollingConsumerSupport;

/**
 * @version $Revision$
 */
public class AtomEntryPollingConsumer extends PollingConsumerSupport {
    private final AtomEndpoint endpoint;
    private Document<Feed> document;
    private int entryIndex;
    private EntryFilter entryFilter = new UpdatedDateFilter();
    private List<Entry> list;

    public AtomEntryPollingConsumer(AtomEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public Exchange receiveNoWait() {
        try {
            getDocument();

            while (hasNextEntry()) {
                Entry entry = list.get(entryIndex--);
                if (entryFilter.isValidEntry(endpoint, document, entry)) {
                    return endpoint.createExchange(document, entry);
                }
            }
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

    // Properties
    //-------------------------------------------------------------------------

    public EntryFilter getEntryFilter() {
        return entryFilter;
    }

    public void setEntryFilter(EntryFilter entryFilter) {
        this.entryFilter = entryFilter;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }

    public Document<Feed> getDocument() throws Exception {
        if (document == null) {
            document = endpoint.parseDocument();
            list = document.getRoot().getEntries();
            entryIndex = list.size() - 1;
        }
        return document;
    }

    protected boolean hasNextEntry() {
        return entryIndex >= 0;
    }
}
