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

import java.util.Date;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Filters out all entries which occur before the last time of the entry we saw (assuming
 * entries arrive sorted in order).
 *
 * @version $Revision$
 */
public class UpdatedDateFilter implements EntryFilter {

    private static final transient Log LOG = LogFactory.getLog(UpdatedDateFilter.class);
    private Date lastUpdate;

    public UpdatedDateFilter(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isValidEntry(AtomEndpoint endpoint, Document<Feed> feed, Entry entry) {
        Date updated = entry.getUpdated();
        if (updated == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No updated time for entry so assuming its valid: entry=[" + entry + "]");
            }
            return true;
        }
        if (lastUpdate != null) {
            if (lastUpdate.after(updated)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Entry is older than lastupdate=[" + lastUpdate
                        + "], no valid entry=[" + entry + "]");
                }
                return false;
            }
        }
        lastUpdate = updated;
        return true;
    }

}
