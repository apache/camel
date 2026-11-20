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

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.apptasticsoftware.rssreader.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ItemUpdatedIdempotentStrategy implements AtomIdempotentStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ItemUpdatedIdempotentStrategy.class);
    private Date lastUpdate;
    // the ids of the entries routed with the date lastUpdate: another entry with the same date is a new entry
    private final Set<String> lastUpdateIds = new HashSet<>();

    @Override
    public boolean isValidItem(Item item) {
        Date updated = toDate(item.getUpdated());
        if (updated == null) {
            // never been updated so get published date
            updated = toDate(item.getPubDate());
        }
        if (updated == null) {
            LOG.debug("No updated time for entry so assuming its valid: entry=[{}]", item);
            return true;
        }
        String id = idOf(item);
        if (lastUpdate != null) {
            // we need to skip the latest updated entry
            if (lastUpdate.after(updated)) {
                LOG.debug("Entry is older than lastupdate=[{}], no valid entry=[{}]", lastUpdate, item);
                return false;
            }
            if (lastUpdate.equals(updated)) {
                // entries published or updated in the same second (or with a date without a time) are told apart by
                // their id: only an entry already routed with that date is skipped
                if (id == null || !lastUpdateIds.add(id)) {
                    LOG.debug("Entry has the same date as lastupdate=[{}] and was routed already, no valid entry=[{}]",
                            lastUpdate, item);
                    return false;
                }
                return true;
            }
        }
        lastUpdate = updated;
        lastUpdateIds.clear();
        if (id != null) {
            lastUpdateIds.add(id);
        }
        return true;
    }

    private static String idOf(Item item) {
        // the atom:id of the entry, else its link
        return item.getGuid().or(item::getLink).orElse(null);
    }

    private Date toDate(Optional<String> dateString) {
        try {
            return AtomConverter.toDate(dateString.orElse(null));
        } catch (NullPointerException | ParseException e) {
            LOG.debug("Failed to parse date string: {}", dateString, e);
            return null;
        }
    }

}
