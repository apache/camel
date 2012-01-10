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

import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Tweet;
import twitter4j.TwitterException;

/**
 * Consumes search requests
 * 
 */
public class SearchConsumer extends Twitter4JConsumer {

    TwitterEndpoint te;

    public SearchConsumer(TwitterEndpoint te) {
        this.te = te;
    }

    public List<Tweet> pollConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();
        Query query = new Query(keywords);
        query.setSinceId(lastId);
        return search(query);
    }

    public List<Tweet> directConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();
        return search(new Query(keywords));
    }

    private List<Tweet> search(Query query) throws TwitterException {
        QueryResult qr = te.getTwitter().search(query);
        List<Tweet> tweets = qr.getTweets();

        for (Tweet t : tweets) {
            checkLastId(t.getId());
        }
        return tweets;
    }
}
