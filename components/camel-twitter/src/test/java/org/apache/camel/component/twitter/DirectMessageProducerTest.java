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

import java.util.Date;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.core.Is.is;

/**
 * Tests posting a twitter update with the default In Message Exchange Pattern
 */
public class DirectMessageProducerTest extends CamelTwitterTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DirectMessageProducerTest.class);

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testDirectMessage() throws Exception {
        Date now = new Date();
        String tweet = "Test a tweet posted on " + now.toString();
        LOG.info("Tweet: " + tweet);
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        // send tweet to the twitter endpoint
        producerTemplate.sendBody("direct:tweets", tweet);


        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodyReceived().body(String.class);
        // Message headers should be preserved
        resultEndpoint.assertIsSatisfied();

        List<Exchange> tweets = resultEndpoint.getExchanges();
        assertNotNull(tweets);
        assertThat(tweets.size(), is(1));
        String receivedTweet = tweets.get(0).getIn().getBody(String.class);
        assertThat(receivedTweet, is(tweet));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:tweets")
                        //.to("log:org.apache.camel.component.twitter?level=INFO&showAll=true&multiline=true")
                        .to("twitter-directmessage:cameltweet?" + getUriTokens())
                        //.to("log:org.apache.camel.component.twitter?level=INFO&showAll=true&multiline=true")
                        .to("mock:result");
            }
        };
    }
}
