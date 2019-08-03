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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 *
 */
public class SearchByExchangeDirectTest extends CamelTwitterTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Produce("direct:header")
    protected ProducerTemplate templateHeader;

    @Produce("direct:double")
    protected ProducerTemplate templateDouble;

    @Test
    public void testSearchTimelineWithStaticQuery() throws Exception {
        template.sendBody(null);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> tweets = mock.getExchanges();
        for (Exchange e : tweets) {
            log.info("Tweet: " + e.getIn().getBody(String.class));
        }
    }

    @Test
    public void testSearchTimelineWithDynamicQuery() throws Exception {
        templateHeader.sendBodyAndHeader(null, TwitterConstants.TWITTER_KEYWORDS, "java");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> tweets = mock.getExchanges();
        for (Exchange e : tweets) {
            log.info("Tweet: " + e.getIn().getBody(String.class));
        }
    }
    
    @Test
    public void testSearchTimelineWithDynamicQuerySinceId() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(TwitterConstants.TWITTER_KEYWORDS, "java");
        headers.put(TwitterConstants.TWITTER_SINCEID, new Long(258347905419730944L));
        templateHeader.sendBodyAndHeaders(null, headers);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> tweets = mock.getExchanges();
        for (Exchange e : tweets) {
            log.info("Tweet: " + e.getIn().getBody(String.class));
        }
    }
    
    @Test
    public void testSearchTimelineWithDynamicQuerySinceIdAndMaxId() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(TwitterConstants.TWITTER_KEYWORDS, "java");
        headers.put(TwitterConstants.TWITTER_SINCEID, new Long(258347905419730944L));
        headers.put(TwitterConstants.TWITTER_MAXID, new Long(258348815243960320L));
        templateHeader.sendBodyAndHeaders(null, headers);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(0);
    }

    @Test
    public void testDoubleSearchKeepingOld() throws Exception {
        templateDouble.sendBodyAndHeader(null, TwitterConstants.TWITTER_KEYWORDS, "java");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
        List<Exchange> tweets = mock.getExchanges();
        for (Exchange e : tweets) {
            log.info("Tweet: " + e.getIn().getBody(String.class));
        }

        // calls the same query again, expecting to receive the same amount of
        // tweets, because we told this route to not filter old(consumed) tweets
        int total = mock.getReceivedCounter();
        templateDouble.sendBodyAndHeader(null, TwitterConstants.TWITTER_KEYWORDS, "java");

        // due race condition
        assertTrue(mock.getReceivedCounter() >= total);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .toF("twitter-search://java?%s", getUriTokens())
                        .split().body()
                        .to("mock:result");

                from("direct:header")
                        .toF("twitter-search://foo?%s", getUriTokens())
                        .split().body()
                        .to("mock:result");

                from("direct:double")
                        .toF("twitter-search://foo?filterOld=false&%s", getUriTokens())
                        .split().body()
                        .to("mock:result");
            }
        };
    }
}
