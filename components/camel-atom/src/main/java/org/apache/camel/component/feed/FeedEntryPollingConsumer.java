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
package org.apache.camel.component.feed;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;

/**
 * Consumer to poll feeds and return each entry from the feed step by step.
 */
public abstract class FeedEntryPollingConsumer<E> extends FeedPollingConsumer implements ResumeAware<ResumeStrategy> {
    protected int entryIndex;
    protected ResumeStrategy resumeStrategy;
    @SuppressWarnings("rawtypes")
    protected List<E> list;
    protected boolean throttleEntries;
    protected Object feed;

    public FeedEntryPollingConsumer(FeedEndpoint endpoint, Processor processor, boolean throttleEntries) {
        super(endpoint, processor);
        this.throttleEntries = throttleEntries;
    }

    @Override
    public int poll() throws Exception {
        if (feed == null) {
            // populate new feed
            feed = createFeed();
            populateList(feed);
        }

        int polledMessages = 0;
        while (hasNextEntry()) {
            E entry = list.get(entryIndex--);
            polledMessages++;

            boolean valid = true;
            if (resumeStrategy != null) {
                ResumeAdapter adapter = resumeStrategy.getAdapter();

                if (adapter instanceof EntryFilter) {
                    valid = ((EntryFilter) adapter).isValidEntry(entry);
                }
            }
            if (valid) {
                Exchange exchange = endpoint.createExchange(feed, entry);
                getProcessor().process(exchange);
                if (this.throttleEntries) {
                    // return and wait for the next poll to continue from last time (this consumer is stateful)
                    return polledMessages;
                }
            }
        }

        // reset feed and list to be able to poll again
        feed = null;
        resetList();

        return polledMessages;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public String adapterFactoryService() {
        return "atom-adapter-factory";
    }

    protected abstract void resetList();

    protected abstract void populateList(Object feed) throws Exception;

    private boolean hasNextEntry() {
        return entryIndex >= 0;
    }
}
