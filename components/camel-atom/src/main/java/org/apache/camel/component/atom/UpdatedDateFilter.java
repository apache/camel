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

import java.util.Date;

import org.apache.abdera.model.Entry;
import org.apache.camel.component.feed.EntryFilter;
import org.apache.camel.component.feed.FeedEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters out all entries which occur before the last time of the entry we saw (assuming
 * entries arrive sorted in order).
 */
public class UpdatedDateFilter implements EntryFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UpdatedDateFilter.class);
    private Date lastUpdate;

    public UpdatedDateFilter(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public boolean isValidEntry(FeedEndpoint endpoint, Object feed, Object entry) {
        Date updated = ((Entry)entry).getUpdated();
        if (updated == null) {
            // never been updated so get published date
            updated = ((Entry)entry).getPublished();
        }        
        if (updated == null) {
            LOG.debug("No updated time for entry so assuming its valid: entry=[{}]", entry);
            return true;
        }        
        if (lastUpdate != null) {
            // we need to skip the latest updated entry
            if (lastUpdate.after(updated) || lastUpdate.equals(updated)) {
                LOG.debug("Entry is older than lastupdate=[{}], no valid entry=[{}]", lastUpdate, entry);
                return false;
            }
        }
        lastUpdate = updated;
        return true;
    }

}
