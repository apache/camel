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
package org.apache.camel.component.rss;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.feed.FeedEndpoint;
import org.apache.camel.component.feed.FeedPollingConsumer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * When the feed cannot be cloned, the split consumer must fail the exchange instead of sending a null body.
 */
public class RssEntryCloneFailureTest extends CamelTestSupport {

    @Test
    public void testCloneFailureFailsTheExchange() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(2);

        MockEndpoint.assertIsSatisfied(context);

        for (Exchange exchange : dead.getReceivedExchanges()) {
            Throwable caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
            assertInstanceOf(RuntimeCamelException.class, caught);
            assertInstanceOf(CloneNotSupportedException.class, caught.getCause());
        }
        Set<String> titles = dead.getReceivedExchanges().stream()
                .map(e -> e.getIn().getBody(SyndEntry.class).getTitle())
                .collect(Collectors.toSet());
        assertEquals(Set.of("Entry 1", "Entry 2"), titles);
    }

    @Test
    public void testCreateExchangeKeepsEntryAndSetsException() {
        RssEndpoint endpoint = context.getEndpoint("rss:file:src/test/data/rss20.xml", RssEndpoint.class);
        SyndFeed feed = createNonCloneableFeed();
        SyndEntry entry = feed.getEntries().get(0);

        Exchange exchange = endpoint.createExchange(feed, entry);

        assertSame(entry, exchange.getIn().getBody());
        assertInstanceOf(RuntimeCamelException.class, exchange.getException());
        assertInstanceOf(CloneNotSupportedException.class, exchange.getException().getCause());
        assertSame(feed, exchange.getIn().getHeader(RssConstants.RSS_FEED));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                RssEndpoint rss = new NonCloneableFeedEndpoint();
                rss.setCamelContext(context);
                rss.setFeedUri("file:src/test/data/rss20.xml");
                rss.setSplitEntries(true);
                rss.setDelay(60000);
                rss.setThrottleEntries(false);
                context.addEndpoint("myrss", rss);

                from("myrss").to("mock:result");
            }
        };
    }

    private static SyndFeed createNonCloneableFeed() {
        SyndFeed feed = new NonCloneableFeed();
        feed.setFeedType("rss_2.0");
        feed.setTitle("Feed");
        List<SyndEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle("Entry " + i);
            entries.add(entry);
        }
        feed.setEntries(entries);
        return feed;
    }

    private static class NonCloneableFeed extends SyndFeedImpl {
        @Override
        public Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException("Cannot clone this feed");
        }
    }

    private static class NonCloneableFeedEndpoint extends RssEndpoint {
        @Override
        protected FeedPollingConsumer createEntryPollingConsumer(
                FeedEndpoint feedEndpoint, Processor processor, boolean throttleEntries)
                throws Exception {
            RssEntryPollingConsumer answer = new RssEntryPollingConsumer(this, processor, throttleEntries) {
                @Override
                protected Object createFeed() {
                    return createNonCloneableFeed();
                }
            };
            configureConsumer(answer);
            return answer;
        }
    }
}
