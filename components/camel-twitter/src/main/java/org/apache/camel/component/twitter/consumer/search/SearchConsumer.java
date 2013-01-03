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
package org.apache.camel.component.twitter.consumer.search;

import java.util.Collections;
import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.TwitterException;

/**
 * Consumes search requests
 */
public class SearchConsumer extends Twitter4JConsumer {

    private static final transient Logger LOG = LoggerFactory.getLogger(SearchConsumer.class);

    public SearchConsumer(TwitterEndpoint te) {
        super(te);
    }

    public List<Status> pollConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();
        Query query = new Query(keywords);
        if (te.getProperties().isFilterOld()) {
            query.setSinceId(lastId);
        }
        LOG.debug("Searching twitter with keywords: {}", keywords);
        return search(query);
    }

    public List<Status> directConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();
        if (keywords == null || keywords.trim().length() == 0) {
            return Collections.emptyList();
        }
        LOG.debug("Searching twitter with keywords: {}", keywords);
        return search(new Query(keywords));
    }

    private List<Status> search(Query query) throws TwitterException {
        QueryResult qr = te.getProperties().getTwitter().search(query);
        List<Status> tweets = qr.getTweets();

        if (te.getProperties().isFilterOld()) {
            for (Status t : tweets) {
                checkLastId(t.getId());
            }
        }

        return tweets;
    }

}
