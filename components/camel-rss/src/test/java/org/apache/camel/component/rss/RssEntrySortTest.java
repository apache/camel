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

import java.util.Date;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import org.apache.camel.Body;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RssEntrySortTest extends CamelTestSupport {

    @Test
    public void testSortedEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sorted");
        mock.expectsAscending(ExpressionBuilder.beanExpression("myBean?method=getPubDate"));
        mock.expectedMessageCount(10);
        mock.setResultWaitTime(15000L);
        mock.assertIsSatisfied();
    }

    @Test
    public void testUnSortedEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unsorted");
        mock.expectsAscending(ExpressionBuilder.beanExpression("myBean?method=getPubDate"));
        mock.expectedMessageCount(10);
        mock.setResultWaitTime(2000L);
        mock.assertIsNotSatisfied(2000L);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("myBean", new MyBean());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("rss:file:src/test/data/rss20.xml?splitEntries=true&sortEntries=true&delay=50").to("mock:sorted");
                from("rss:file:src/test/data/rss20.xml?splitEntries=true&sortEntries=false&delay=50").to("mock:unsorted");
            }
        };
    }

    public static class MyBean {
        public Date getPubDate(@Body Object body) {
            SyndFeed feed = (SyndFeed) body;
            SyndEntry syndEntry = feed.getEntries().get(0);
            Date date = syndEntry.getUpdatedDate();
            if (date == null) {
                date = syndEntry.getPublishedDate();
            }
            return date;
        }
    }
}
