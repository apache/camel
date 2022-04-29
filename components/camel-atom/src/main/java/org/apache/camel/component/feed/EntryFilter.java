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

import org.apache.camel.resume.ResumeAdapter;

/**
 * Filter used by the {@link org.apache.camel.component.feed.FeedEntryPollingConsumer} to filter entries from the feed.
 *
 * @param <E> entry type
 */
public interface EntryFilter<E> extends ResumeAdapter {

    /**
     * Tests to be used as filtering the feed for only entries of interest, such as only new entries, etc.
     *
     * @param  entry the given entry to filter
     * @return       <tt>true</tt> to include the entry, <ff>false</tt> to skip it
     */
    boolean isValidEntry(E entry);

    @Override
    default void resume() {
        // NO-OP by default. Implementations can implement more complex behaviors if needed
    }
}
