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
package org.apache.camel.component.rss;

import javax.naming.Context;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import org.apache.camel.Body;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

public class RssFilterTest extends CamelTestSupport {

    @Test
    public void testFilterOutNonCamelPosts() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(6);
        mock.assertIsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext jndi = new JndiContext();
        jndi.bind("myFilterBean", new FilterBean());
        return jndi;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // See RssFilterWithXPathTest for an example of how to do this with XPath

                // START SNIPPET: ex1
                // only entries with Camel in the title will get through the filter
                from("rss:file:src/test/data/rss20.xml?splitEntries=true&consumer.delay=100").
                        filter().method("myFilterBean", "titleContainsCamel").to("mock:result");
                // END SNIPPET: ex1
            }
        };
    }

    // START SNIPPET: ex2
    public static class FilterBean {
        public boolean titleContainsCamel(@Body SyndFeed feed) {
            SyndEntry firstEntry = (SyndEntry) feed.getEntries().get(0);
            return firstEntry.getTitle().contains("Camel");
        }
    }
    // END SNIPPET: ex2     
}
