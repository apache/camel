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

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RssEntryPollingConsumerWithFilterTest extends CamelTestSupport {

    @Test
    public void testListOfEntriesIsSplitIntoPieces() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // two entries were published after Fri, 31 Oct 2008 12:02:21 -0500
        mock.expectedMessageCount(2);
        mock.assertIsSatisfied();
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // timestamp from the feed to use as base
        // Fri, 31 Oct 2008 12:02:21 -0500
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT-5:00"));
        cal.set(2008, Calendar.OCTOBER, 31, 12, 02, 21);

        registry.bind("myBean", new MyBean(cal.getTime()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("rss:file:src/test/data/rss20.xml?splitEntries=true&delay=100").filter().method("myBean", "isAfterDate")
                        .to("mock:result");
            }
        };
    }

    public static class MyBean {
        private final Date time;

        public MyBean(Date time) {
            this.time = time;
        }

        public boolean isAfterDate(Exchange ex) {
            SyndFeed feed = ex.getIn().getBody(SyndFeed.class);
            assertEquals(1, feed.getEntries().size());
            SyndEntry entry = feed.getEntries().get(0);
            return entry.getPublishedDate().after(time);
        }
    }
}
