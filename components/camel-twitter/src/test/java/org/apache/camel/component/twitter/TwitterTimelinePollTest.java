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
import org.apache.camel.component.twitter.timeline.TwitterTimelineEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Paging;
import twitter4j.v1.ResponseList;
import twitter4j.v1.Status;
import twitter4j.v1.TimelinesResources;
import twitter4j.v1.TwitterV1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A poll of a timeline must send one request, and take the id of the last tweet from the tweets it routes.
 */
class TwitterTimelinePollTest extends CamelTestSupport {

    private static final String URI = "twitter-timeline:home?consumerKey=a&consumerSecret=b&accessToken=c&accessTokenSecret=d"
                                      + "&startScheduler=false";

    // the ids of the tweets of the timeline
    private final List<Long> timeline = new ArrayList<>();
    // the since_id of the requests that were sent
    private final List<Long> requests = new ArrayList<>();
    // run after a request has been answered, to post a tweet while the consumer is polling
    private Runnable afterRequest = () -> {
    };

    @Test
    void pollSendsOneRequest() throws Exception {
        timeline.add(10L);
        List<Long> routed = new ArrayList<>();
        Consumer consumer = consumer(routed);
        consumer.start();
        try {
            poll(consumer);

            assertEquals(List.of(10L), routed);
            assertEquals(1, requests.size(), "A poll must send one request to the timeline");
        } finally {
            consumer.stop();
        }
    }

    @Test
    void tweetPostedDuringAPollIsRoutedByTheNextPoll() throws Exception {
        timeline.add(10L);
        // tweet 11 is posted right after the first request has been answered
        afterRequest = () -> {
            if (requests.size() == 1) {
                timeline.add(11L);
            }
        };
        List<Long> routed = new ArrayList<>();
        Consumer consumer = consumer(routed);
        consumer.start();
        try {
            poll(consumer);
            assertEquals(List.of(10L), routed);

            poll(consumer);
            assertEquals(List.of(10L, 11L), routed, "The tweet posted during the first poll must not be skipped");
        } finally {
            consumer.stop();
        }
    }

    private Consumer consumer(List<Long> routed) throws Exception {
        TwitterTimelineEndpoint endpoint = context.getEndpoint(URI, TwitterTimelineEndpoint.class);
        endpoint.getProperties().setTwitter(twitter());
        return endpoint.createConsumer(exchange -> routed.add(exchange.getIn().getBody(Status.class).getId()));
    }

    /**
     * Runs one poll of the consumer, whose scheduler is not started.
     */
    private static void poll(Consumer consumer) {
        ((Runnable) consumer).run();
    }

    /**
     * A Twitter client whose home timeline records the request and returns the tweets with an id above since_id, as the
     * API does.
     */
    @SuppressWarnings("unchecked")
    private Twitter twitter() throws TwitterException {
        TimelinesResources timelines = mock(TimelinesResources.class);
        when(timelines.getHomeTimeline(any(Paging.class))).thenAnswer(invocation -> {
            Paging paging = invocation.getArgument(0);
            requests.add(paging.sinceId);
            List<Status> found = new ArrayList<>();
            for (long id : timeline) {
                if (id > paging.sinceId) {
                    found.add(tweet(id));
                }
            }
            afterRequest.run();
            // a response of the API is a list of the tweets
            return mock(ResponseList.class, delegatesTo(found));
        });
        TwitterV1 v1 = mock(TwitterV1.class);
        when(v1.timelines()).thenReturn(timelines);
        Twitter twitter = mock(Twitter.class);
        when(twitter.v1()).thenReturn(v1);
        return twitter;
    }

    private static Status tweet(long id) {
        Status status = mock(Status.class);
        when(status.getId()).thenReturn(id);
        return status;
    }
}
