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
package org.apache.camel.component.rss;

import java.util.Comparator;
import java.util.Date;

import com.sun.syndication.feed.synd.SyndEntry;

public class RssDateComparator implements Comparator<SyndEntry> {

    public int compare(SyndEntry s1, SyndEntry s2) {
        Date d1 = getUpdatedDate(s1);
        Date d2 = getUpdatedDate(s2);
        if (d2 != null && d1 != null) {
            return d2.compareTo(d1);
        } else {
            return 0;
        }
    }

    private Date getUpdatedDate(SyndEntry entry) {
        Date date = entry.getUpdatedDate();
        if (date == null) {
            date = entry.getPublishedDate();
        }        
        return date;
    }    
}
