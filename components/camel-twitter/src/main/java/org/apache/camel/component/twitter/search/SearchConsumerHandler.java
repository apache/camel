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
package org.apache.camel.component.twitter.search;

import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.TwitterEventType;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.GeoLocation;
import twitter4j.v1.Query;
import twitter4j.v1.Query.Unit;
import twitter4j.v1.QueryResult;
import twitter4j.v1.Status;

/**
 * Consumes search requests
 */
public class SearchConsumerHandler extends AbstractTwitterConsumerHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SearchConsumerHandler.class);

    private String keywords;

    public SearchConsumerHandler(TwitterEndpoint te, String keywords) {
        super(te);
        this.keywords = keywords;
    }

    @Override
    public List<Exchange> pollConsume() throws TwitterException {
        String keywords = this.keywords;

        Query query;

        if (keywords != null && !keywords.isBlank()) {
            query = Query.of(keywords);
            LOG.debug("Searching twitter with keywords: {}", keywords);
        } else {
            query = Query.of("");
            LOG.debug("Searching twitter without keywords.");
        }

        if (endpoint.getProperties().isFilterOld()) {
            query.sinceId(getLastId());
        }

        return search(query);
    }

    @Override
    public List<Exchange> directConsume() throws TwitterException {
        String keywords = this.keywords;
        if (keywords == null || !keywords.isBlank()) {
            return Collections.emptyList();
        }
        Query query = Query.of(keywords);

        LOG.debug("Searching twitter with keywords: {}", keywords);
        return search(query);
    }

    private List<Exchange> search(Query query) throws TwitterException {
        Integer numberOfPages = 1;

        if (ObjectHelper.isNotEmpty(endpoint.getProperties().getLang())) {
            query.lang(endpoint.getProperties().getLang());
        }

        if (ObjectHelper.isNotEmpty(endpoint.getProperties().getCount())) {
            query.count(endpoint.getProperties().getCount());
        }

        if (ObjectHelper.isNotEmpty(endpoint.getProperties().getNumberOfPages())) {
            numberOfPages = endpoint.getProperties().getNumberOfPages();
        }

        if (ObjectHelper.isNotEmpty(endpoint.getProperties().getLatitude())
                && ObjectHelper.isNotEmpty(endpoint.getProperties().getLongitude())
                && ObjectHelper.isNotEmpty(endpoint.getProperties().getRadius())) {
            GeoLocation location
                    = GeoLocation.of(endpoint.getProperties().getLatitude(), endpoint.getProperties().getLongitude());
            query.geoCode(location, endpoint.getProperties().getRadius(),
                    Unit.valueOf(endpoint.getProperties().getDistanceMetric()));

            LOG.debug("Searching with additional geolocation parameters.");
        }

        LOG.debug("Searching with {} pages.", numberOfPages);

        Twitter twitter = getTwitter();
        QueryResult qr = twitter.v1().search().search(query);
        List<Status> tweets = qr.getTweets();

        for (int i = 1; i < numberOfPages; i++) {
            if (!qr.hasNext()) {
                break;
            }

            qr = twitter.v1().search().search(qr.nextQuery());
            tweets.addAll(qr.getTweets());
        }

        if (endpoint.getProperties().isFilterOld()) {
            for (Status status : tweets) {
                setLastIdIfGreater(status.getId());
            }
        }

        return TwitterEventType.STATUS.createExchangeList(endpoint, tweets);
    }

}
