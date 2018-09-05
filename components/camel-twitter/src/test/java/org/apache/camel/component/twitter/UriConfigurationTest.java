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
package org.apache.camel.component.twitter;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.data.StreamingType;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.component.twitter.directmessage.TwitterDirectMessageEndpoint;
import org.apache.camel.component.twitter.search.TwitterSearchEndpoint;
import org.apache.camel.component.twitter.streaming.TwitterStreamingEndpoint;
import org.apache.camel.component.twitter.timeline.TwitterTimelineEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class UriConfigurationTest extends Assert {

    private CamelContext context = new DefaultCamelContext();
    private CamelTwitterTestSupport support = new CamelTwitterTestSupport();

    @Deprecated
    @Test
    public void testDeprecatedUri() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter:search?" + support.getUriTokens());
        assertTrue("Endpoint not a TwitterEndpoint: " + endpoint, endpoint instanceof TwitterEndpoint);
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?" + support.getUriTokens());
        assertTrue("Endpoint not a TwitterSearchEndpoint: " + endpoint, endpoint instanceof TwitterSearchEndpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;

        assertTrue(!twitterEndpoint.getProperties().getConsumerKey().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getConsumerSecret().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getAccessToken().isEmpty());
        assertTrue(!twitterEndpoint.getProperties().getAccessTokenSecret().isEmpty());
    }
    
    @Test
    public void testPageSetting() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?count=50&numberOfPages=2");
        assertTrue("Endpoint not a TwitterSearchEndpoint: " + endpoint, endpoint instanceof TwitterSearchEndpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;

        assertEquals(new Integer(50), twitterEndpoint.getProperties().getCount());
        assertEquals(new Integer(2), twitterEndpoint.getProperties().getNumberOfPages());
    }
    
    @Test
    public void testHttpProxySetting() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo?httpProxyHost=example.com&httpProxyPort=3338&httpProxyUser=test&httpProxyPassword=pwd");
        assertTrue("Endpoint not a TwitterSearchEndpoint: " + endpoint, endpoint instanceof TwitterSearchEndpoint);
        TwitterSearchEndpoint twitterEndpoint = (TwitterSearchEndpoint) endpoint;
        
        assertEquals("example.com", twitterEndpoint.getProperties().getHttpProxyHost());
        assertEquals(Integer.valueOf(3338), twitterEndpoint.getProperties().getHttpProxyPort());
        assertEquals("test", twitterEndpoint.getProperties().getHttpProxyUser());
        assertEquals("pwd", twitterEndpoint.getProperties().getHttpProxyPassword());
    }

    @Test
    public void testDirectMessageEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-directmessage:foo");
        assertTrue("Endpoint not a TwitterDirectMessageEndpoint: " + endpoint, endpoint instanceof TwitterDirectMessageEndpoint);
    }

    @Test
    public void testSearchEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-search:foo");
        assertTrue("Endpoint not a TwitterSearchEndpoint: " + endpoint, endpoint instanceof TwitterSearchEndpoint);
    }

    @Test
    public void testStreamingEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-streaming:filter");
        assertTrue("Endpoint not a TwitterStreamingEndpoint: " + endpoint, endpoint instanceof TwitterStreamingEndpoint);
        TwitterStreamingEndpoint streamingEndpoint = (TwitterStreamingEndpoint)endpoint;
        assertEquals(StreamingType.FILTER, streamingEndpoint.getStreamingType());
        endpoint = context.getEndpoint("twitter-streaming:sample");
        assertTrue("Endpoint not a TwitterStreamingEndpoint: " + endpoint, endpoint instanceof TwitterStreamingEndpoint);
        streamingEndpoint = (TwitterStreamingEndpoint)endpoint;
        assertEquals(StreamingType.SAMPLE, streamingEndpoint.getStreamingType());
        endpoint = context.getEndpoint("twitter-streaming:user");
        assertTrue("Endpoint not a TwitterStreamingEndpoint: " + endpoint, endpoint instanceof TwitterStreamingEndpoint);
        streamingEndpoint = (TwitterStreamingEndpoint)endpoint;
        assertEquals(StreamingType.USER, streamingEndpoint.getStreamingType());
    }

    @Test
    public void testTimelineEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("twitter-timeline:home");
        assertTrue("Endpoint not a TwitterTimelineEndpoint: " + endpoint, endpoint instanceof TwitterTimelineEndpoint);
        TwitterTimelineEndpoint timelineEndpoint = (TwitterTimelineEndpoint)endpoint;
        assertEquals(TimelineType.HOME, timelineEndpoint.getTimelineType());
        endpoint = context.getEndpoint("twitter-timeline:mentions");
        assertTrue("Endpoint not a TwitterTimelineEndpoint: " + endpoint, endpoint instanceof TwitterTimelineEndpoint);
        timelineEndpoint = (TwitterTimelineEndpoint)endpoint;
        assertEquals(TimelineType.MENTIONS, timelineEndpoint.getTimelineType());
        endpoint = context.getEndpoint("twitter-timeline:retweetsofme");
        assertTrue("Endpoint not a TwitterTimelineEndpoint: " + endpoint, endpoint instanceof TwitterTimelineEndpoint);
        timelineEndpoint = (TwitterTimelineEndpoint)endpoint;
        assertEquals(TimelineType.RETWEETSOFME, timelineEndpoint.getTimelineType());
        endpoint = context.getEndpoint("twitter-timeline:user");
        assertTrue("Endpoint not a TwitterTimelineEndpoint: " + endpoint, endpoint instanceof TwitterTimelineEndpoint);
        timelineEndpoint = (TwitterTimelineEndpoint)endpoint;
        assertEquals(TimelineType.USER, timelineEndpoint.getTimelineType());
    }
}
