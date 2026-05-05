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
import java.util.Optional;

import com.apptasticsoftware.rssreader.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ItemUpdatedIdempotentStrategy implements AtomIdempotentStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ItemUpdatedIdempotentStrategy.class);
    private Date lastUpdate;

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
        if (lastUpdate != null) {
            // we need to skip the latest updated entry
            if (lastUpdate.after(updated) || lastUpdate.equals(updated)) {
                LOG.debug("Entry is older than lastupdate=[{}], no valid entry=[{}]", lastUpdate, item);
                return false;
            }
        }
        lastUpdate = updated;
        return true;
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
