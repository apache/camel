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
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.GeoLocation;
import twitter4j.Query;
import twitter4j.Query.Unit;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Consumes search requests
 */
public class SearchConsumer extends Twitter4JConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SearchConsumer.class);

    public SearchConsumer(TwitterEndpoint te) {
        super(te);
    }

    public List<Status> pollConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();

        Query query;

        if (keywords != null && keywords.trim().length() > 0) {
            query = new Query(keywords);
            LOG.debug("Searching twitter with keywords: {}", keywords);
        } else {
            query = new Query();
            LOG.debug("Searching twitter without keywords.");
        }

        if (te.getProperties().isFilterOld()) {
            query.setSinceId(lastId);
        }

        return search(query);
    }

    public List<Status> directConsume() throws TwitterException {
        String keywords = te.getProperties().getKeywords();
        if (keywords == null || keywords.trim().length() == 0) {
            return Collections.emptyList();
        }
        Query query = new Query(keywords);

        LOG.debug("Searching twitter with keywords: {}", keywords);
        return search(query);
    }

    private List<Status> search(Query query) throws TwitterException {
        Integer numberOfPages = 1;

        if (ObjectHelper.isNotEmpty(te.getProperties().getLang())) {
            query.setLang(te.getProperties().getLang());
        }

        if (ObjectHelper.isNotEmpty(te.getProperties().getCount())) {
            query.setCount(te.getProperties().getCount());
        }

        if (ObjectHelper.isNotEmpty(te.getProperties().getNumberOfPages())) {
            numberOfPages = te.getProperties().getNumberOfPages();
        }

        if (ObjectHelper.isNotEmpty(te.getProperties().getLatitude())
                && ObjectHelper.isNotEmpty(te.getProperties().getLongitude())
                && ObjectHelper.isNotEmpty(te.getProperties().getRadius())) {
            GeoLocation location = new GeoLocation(te.getProperties().getLatitude(), te.getProperties().getLongitude());
            query.setGeoCode(location, te.getProperties().getRadius(), Unit.valueOf(te.getProperties().getDistanceMetric()));

            LOG.debug("Searching with additional geolocation parameters.");
        }

        LOG.debug("Searching with {} pages.", numberOfPages);

        Twitter twitter = te.getProperties().getTwitter();
        QueryResult qr = twitter.search(query);
        List<Status> tweets = qr.getTweets();

        for (int i = 1; i < numberOfPages; i++) {
            if (!qr.hasNext()) {
                break;
            }

            qr = twitter.search(qr.nextQuery());
            tweets.addAll(qr.getTweets());
        }

        if (te.getProperties().isFilterOld()) {
            for (Status t : tweets) {
                checkLastId(t.getId());
            }
        }

        return tweets;
    }

}
