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

import java.util.List;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter;

public class SearchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SearchProducer.class);

    private volatile long lastId;
    private TwitterEndpoint endpoint;
    private String keywords;

    public SearchProducer(TwitterEndpoint endpoint, String keywords) {
        super(endpoint);
        this.endpoint = endpoint;
        this.keywords = keywords;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        long myLastId = lastId;
        // KEYWORDS
        // keywords from header take precedence
        String keywords = exchange.getIn().getHeader(TwitterConstants.TWITTER_KEYWORDS, String.class);
        if (keywords == null) {
            keywords = this.keywords;
        }

        if (keywords == null) {
            throw new CamelExchangeException("No keywords to use for query", exchange);
        }

        Query query = new Query(keywords);

        // filter of older tweets
        if (endpoint.getProperties().isFilterOld() && myLastId != 0) {
            query.setSinceId(myLastId);
        }
        
        // since id
        Long sinceId = exchange.getIn().getHeader(TwitterConstants.TWITTER_SINCEID, Long.class);
        if (sinceId == null) {
            sinceId = endpoint.getProperties().getSinceId();
        }
        if (ObjectHelper.isNotEmpty(sinceId)) {
            query.setSinceId(sinceId);
        }
        
        // max id
        Long maxId = exchange.getIn().getHeader(TwitterConstants.TWITTER_MAXID, Long.class);
        if (ObjectHelper.isNotEmpty(maxId)) {
            query.setMaxId(maxId);
        }

        // language
        String lang = exchange.getIn().getHeader(TwitterConstants.TWITTER_SEARCH_LANGUAGE, String.class);
        if (lang == null) {
            lang = endpoint.getProperties().getLang();
        }

        if (ObjectHelper.isNotEmpty(lang)) {
            query.setLang(lang);
        }

        // number of elements per page
        Integer count = exchange.getIn().getHeader(TwitterConstants.TWITTER_COUNT, Integer.class);
        if (count == null) {
            count = endpoint.getProperties().getCount();
        }
        if (ObjectHelper.isNotEmpty(count)) {
            query.setCount(count);
        }

        // number of pages
        Integer numberOfPages = exchange.getIn().getHeader(TwitterConstants.TWITTER_NUMBER_OF_PAGES, Integer.class);
        if (numberOfPages == null) {
            numberOfPages = endpoint.getProperties().getNumberOfPages();
        }

        Twitter twitter = endpoint.getProperties().getTwitter();
        LOG.debug("Searching twitter with keywords: {}", keywords);
        QueryResult results = twitter.search(query);
        List<Status> list = results.getTweets();

        for (int i = 1; i < numberOfPages; i++) {
            if (!results.hasNext()) {
                break;
            }
            LOG.debug("Fetching page");
            results = twitter.search(results.nextQuery());
            list.addAll(results.getTweets());
        }

        if (endpoint.getProperties().isFilterOld()) {
            for (Status t : list) {
                long newId = t.getId();
                if (newId > myLastId) {
                    myLastId = newId;
                }
            }
        }

        exchange.getIn().setBody(list);
        // update the lastId after finished the processing
        if (myLastId > lastId) {
            lastId = myLastId;
        }
    }

}
