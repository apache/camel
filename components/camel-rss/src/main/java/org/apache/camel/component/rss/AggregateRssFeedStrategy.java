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

import java.util.ArrayList;
import java.util.List;

import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AggregateRssFeedStrategy implements AggregationStrategy {
    protected final Logger log = LoggerFactory.getLogger(AggregateRssFeedStrategy.class);    
    
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }
        SyndFeed oldFeed = oldExchange.getIn().getBody(SyndFeed.class);
        SyndFeed newFeed = newExchange.getIn().getBody(SyndFeed.class);
        if (oldFeed != null && newFeed != null) {                
            List<SyndEntryImpl> oldEntries = CastUtils.cast(oldFeed.getEntries());                  
            List<SyndEntryImpl> newEntries = CastUtils.cast(newFeed.getEntries());
            List<SyndEntryImpl> mergedList = new ArrayList<SyndEntryImpl>(oldEntries.size() + newEntries.size());
            mergedList.addAll(oldEntries);
            mergedList.addAll(newEntries);
            oldFeed.setEntries(mergedList);    
        } else {
            log.debug("Could not merge exchanges. One body was null.");
        }
        return oldExchange;
    }
}
