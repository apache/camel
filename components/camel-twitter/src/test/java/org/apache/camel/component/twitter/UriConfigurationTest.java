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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.component.twitter.directmessage.TwitterDirectMessageEndpoint;
import org.apache.camel.component.twitter.search.TwitterSearchEndpoint;
import org.apache.camel.component.twitter.timeline.TwitterTimelineEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UriConfigurationTest {

    private CamelContext context = new DefaultCamelContext();
    private CamelTwitterITSupport support = new CamelTwitterITSupport();

    @Test
    public void testBasicAuthentication() {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?" + support.getUriTokens());
        assertTrue(endpoint instanceof TwitterSearchEndpoint, "Endpoint not a TwitterSearchEndpoint: " + endpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;

        assertFalse(twitterEndpoint.getProperties().getConsumerKey().isEmpty());
        assertFalse(twitterEndpoint.getProperties().getConsumerSecret().isEmpty());
        assertFalse(twitterEndpoint.getProperties().getAccessToken().isEmpty());
        assertFalse(twitterEndpoint.getProperties().getAccessTokenSecret().isEmpty());
    }

    @Test
    public void testPageSetting() {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?count=50&numberOfPages=2&" + support.getUriTokens());
        assertTrue(endpoint instanceof TwitterSearchEndpoint, "Endpoint not a TwitterSearchEndpoint: " + endpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;

        assertEquals(Integer.valueOf(50), twitterEndpoint.getProperties().getCount());
        assertEquals(Integer.valueOf(2), twitterEndpoint.getProperties().getNumberOfPages());
    }

    @Test
    public void testHttpProxySetting() {
        Endpoint endpoint = context.getEndpoint(
                "twitter-search:foo?httpProxyHost=example.com&httpProxyPort=3338&httpProxyUser=test&httpProxyPassword=pwd&"
                                                + support.getUriTokens());
        assertTrue(endpoint instanceof TwitterSearchEndpoint, "Endpoint not a TwitterSearchEndpoint: " + endpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;

        assertEquals("example.com", twitterEndpoint.getProperties().getHttpProxyHost());
        assertEquals(Integer.valueOf(3338), twitterEndpoint.getProperties().getHttpProxyPort());
        assertEquals("test", twitterEndpoint.getProperties().getHttpProxyUser());
        assertEquals("pwd", twitterEndpoint.getProperties().getHttpProxyPassword());
    }

    @Test
    public void testDirectMessageEndpoint() {
        Endpoint endpoint = context.getEndpoint("twitter-directmessage:foo?" + support.getUriTokens());
        assertTrue(endpoint instanceof TwitterDirectMessageEndpoint,
                "Endpoint not a TwitterDirectMessageEndpoint: " + endpoint);
    }

    @Test
    public void testSearchEndpoint() {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?" + support.getUriTokens());
        assertTrue(endpoint instanceof TwitterSearchEndpoint, "Endpoint not a TwitterSearchEndpoint: " + endpoint);
    }

    @Test
    public void testTimelineEndpoint() {
        // set on component level instead
        AbstractTwitterComponent twitter = context.getComponent("twitter-timeline", AbstractTwitterComponent.class);
        twitter.setAccessToken(support.accessToken);
        twitter.setAccessTokenSecret(support.accessTokenSecret);
        twitter.setConsumerKey(support.consumerKey);
        twitter.setConsumerSecret(support.consumerSecret);

        Endpoint endpoint = context.getEndpoint("twitter-timeline:home");
        assertTrue(endpoint instanceof TwitterTimelineEndpoint, "Endpoint not a TwitterTimelineEndpoint: " + endpoint);
        TwitterTimelineEndpoint timelineEndpoint = (TwitterTimelineEndpoint) endpoint;
        assertEquals(TimelineType.HOME, timelineEndpoint.getTimelineType());
        endpoint = context.getEndpoint("twitter-timeline:mentions");
        assertTrue(endpoint instanceof TwitterTimelineEndpoint, "Endpoint not a TwitterTimelineEndpoint: " + endpoint);
        timelineEndpoint = (TwitterTimelineEndpoint) endpoint;
        assertEquals(TimelineType.MENTIONS, timelineEndpoint.getTimelineType());
        endpoint = context.getEndpoint("twitter-timeline:user");
        assertTrue(endpoint instanceof TwitterTimelineEndpoint, "Endpoint not a TwitterTimelineEndpoint: " + endpoint);
        timelineEndpoint = (TwitterTimelineEndpoint) endpoint;
        assertEquals(TimelineType.USER, timelineEndpoint.getTimelineType());

        endpoint = context.getEndpoint("twitter-timeline:list");
        assertTrue(endpoint instanceof TwitterTimelineEndpoint, "Endpoint not a TwitterTimelineEndpoint: " + endpoint);
        timelineEndpoint = (TwitterTimelineEndpoint) endpoint;
        assertEquals(TimelineType.LIST, timelineEndpoint.getTimelineType());
    }
}
