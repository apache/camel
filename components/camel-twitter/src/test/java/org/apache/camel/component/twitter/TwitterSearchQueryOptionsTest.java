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
package org.apache.camel.component.twitter;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.search.TwitterSearchEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Query;
import twitter4j.v1.QueryResult;
import twitter4j.v1.SearchResource;
import twitter4j.v1.Status;
import twitter4j.v1.TwitterV1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The twitter-search endpoint must send its options with the query. A Twitter4J {@link Query} is immutable, so the
 * result of each of its methods has to be kept.
 */
class TwitterSearchQueryOptionsTest extends CamelTestSupport {

    private static final String URI = "twitter-search:camel?consumerKey=a&consumerSecret=b&accessToken=c&accessTokenSecret=d";

    // the ids of the tweets that match the search
    private final List<Long> tweets = new ArrayList<>();
    // the queries that were sent
    private final List<Query> queries = new ArrayList<>();

    @Test
    void consumerPollsOnlyTheTweetsNewerThanTheLastOne() throws Exception {
        tweets.addAll(List.of(10L, 11L));
        List<Long> routed = new ArrayList<>();
        Consumer consumer = endpoint("&startScheduler=false").createConsumer(exchange -> routed.add(tweetId(exchange)));
        consumer.start();
        try {
            poll(consumer);
            assertEquals(List.of(10L, 11L), routed);

            poll(consumer);
            assertEquals(List.of(10L, 11L), routed, "The second poll must not route the same tweets again");

            tweets.add(12L);
            poll(consumer);
            assertEquals(List.of(10L, 11L, 12L), routed);
        } finally {
            consumer.stop();
        }
    }

    @Test
    void consumerWithoutFilterOldPollsTheTweetsAgain() throws Exception {
        tweets.addAll(List.of(10L, 11L));
        List<Long> routed = new ArrayList<>();
        Consumer consumer = endpoint("&startScheduler=false&filterOld=false")
                .createConsumer(exchange -> routed.add(tweetId(exchange)));
        consumer.start();
        try {
            poll(consumer);
            poll(consumer);
            assertEquals(List.of(10L, 11L, 10L, 11L), routed);
        } finally {
            consumer.stop();
        }
    }

    @Test
    void consumerSendsLanguageCountAndGeolocation() throws Exception {
        Consumer consumer = endpoint("&startScheduler=false&lang=en&count=3"
                                     + "&latitude=1.5&longitude=2.5&radius=3&distanceMetric=km")
                .createConsumer(exchange -> {
                });
        consumer.start();
        try {
            poll(consumer);
        } finally {
            consumer.stop();
        }

        assertEquals(1, queries.size());
        Query query = queries.get(0);
        assertEquals("camel", query.query);
        assertEquals("en", query.lang);
        assertEquals(3, query.count);
        assertEquals("1.5,2.5,3.0km", query.geocode);
    }

    @Test
    void directConsumerSearchesTheKeywordsAtStartup() throws Exception {
        tweets.addAll(List.of(10L, 11L));
        List<Long> routed = new ArrayList<>();
        Consumer consumer = endpoint("&startScheduler=false&type=direct")
                .createConsumer(exchange -> routed.add(tweetId(exchange)));
        consumer.start();
        try {
            assertEquals(List.of(10L, 11L), routed);
        } finally {
            consumer.stop();
        }
    }

    @Test
    void producerSendsSinceIdMaxIdLanguageAndCountOfTheHeaders() throws Exception {
        tweets.addAll(List.of(10L, 11L, 12L, 13L));
        TwitterSearchEndpoint endpoint = endpoint("");
        Producer producer = endpoint.createProducer();
        producer.start();
        Exchange exchange = endpoint.createExchange();
        try {
            exchange.getIn().setHeader(TwitterConstants.TWITTER_SINCEID, 10L);
            exchange.getIn().setHeader(TwitterConstants.TWITTER_MAXID, 12L);
            exchange.getIn().setHeader(TwitterConstants.TWITTER_SEARCH_LANGUAGE, "en");
            exchange.getIn().setHeader(TwitterConstants.TWITTER_COUNT, 2);
            producer.process(exchange);
        } finally {
            producer.stop();
        }

        List<Long> found = new ArrayList<>();
        for (Object status : exchange.getIn().getBody(List.class)) {
            found.add(((Status) status).getId());
        }
        assertEquals(List.of(11L, 12L), found);
        assertEquals(1, queries.size());
        assertEquals("en", queries.get(0).lang);
        assertEquals(2, queries.get(0).count);
    }

    @Test
    void producerSendsTheOptionsOfTheEndpoint() throws Exception {
        tweets.addAll(List.of(10L, 11L, 12L));
        TwitterSearchEndpoint endpoint = endpoint("&sinceId=11&lang=de&count=7");
        Producer producer = endpoint.createProducer();
        producer.start();
        Exchange exchange = endpoint.createExchange();
        try {
            producer.process(exchange);
        } finally {
            producer.stop();
        }

        assertEquals(1, exchange.getIn().getBody(List.class).size());
        assertEquals(12L, ((Status) exchange.getIn().getBody(List.class).get(0)).getId());
        assertEquals("de", queries.get(0).lang);
        assertEquals(7, queries.get(0).count);
    }

    private TwitterSearchEndpoint endpoint(String options) throws TwitterException {
        TwitterSearchEndpoint endpoint = context.getEndpoint(URI + options, TwitterSearchEndpoint.class);
        endpoint.getProperties().setTwitter(twitter());
        return endpoint;
    }

    /**
     * A Twitter client whose search records the query and returns the matching tweets with an id above since_id and up
     * to max_id, as the search API does.
     */
    private Twitter twitter() throws TwitterException {
        SearchResource search = mock(SearchResource.class);
        when(search.search(any(Query.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            queries.add(query);
            List<Status> found = new ArrayList<>();
            for (long id : tweets) {
                if (id > query.sinceId && (query.maxId == -1 || id <= query.maxId)) {
                    found.add(tweet(id));
                }
            }
            QueryResult result = mock(QueryResult.class);
            when(result.getTweets()).thenReturn(found);
            return result;
        });
        TwitterV1 v1 = mock(TwitterV1.class);
        when(v1.search()).thenReturn(search);
        Twitter twitter = mock(Twitter.class);
        when(twitter.v1()).thenReturn(v1);
        return twitter;
    }

    private static Status tweet(long id) {
        Status status = mock(Status.class);
        when(status.getId()).thenReturn(id);
        return status;
    }

    private static long tweetId(Exchange exchange) {
        return exchange.getIn().getBody(Status.class).getId();
    }

    /**
     * Runs one poll of the consumer, whose scheduler is not started.
     */
    private static void poll(Consumer consumer) {
        ((Runnable) consumer).run();
    }
}
