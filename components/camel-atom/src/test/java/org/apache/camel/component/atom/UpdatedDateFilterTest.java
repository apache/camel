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

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.camel.component.feed.EntryFilter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for UpdatedDateFilter
 */
public class UpdatedDateFilterTest extends Assert {

    @Test
    public void testFilter() throws Exception {
        Document<Feed> doc = AtomUtils.parseDocument("file:src/test/data/feed.atom");
        assertNotNull(doc);

        // timestamp from the feed to use as base
        // 2007-11-13T13:35:25.014Z
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+1:00"));
        cal.set(2007, Calendar.NOVEMBER, 13, 14, 35, 0);
        EntryFilter filter = new UpdatedDateFilter(cal.getTime());

        List<Entry> entries = doc.getRoot().getEntries();

        // must reverse backwards
        for (int i = entries.size() - 1; i > 0; i--) {
            Entry entry = entries.get(i);
            boolean valid = filter.isValidEntry(null, doc, entry);
            // only the 3 last should be true
            if (i > 3) {
                assertEquals("not valid", false, valid);
            } else {
                assertEquals("valid", true, valid);
            }
        }
    }

}
