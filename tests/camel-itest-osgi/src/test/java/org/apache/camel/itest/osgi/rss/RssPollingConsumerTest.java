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
package org.apache.camel.itest.osgi.rss;

import java.net.URL;

import com.sun.syndication.feed.synd.SyndFeed;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rss.RssConstants;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
@Ignore("abdera-core bundle has a wrong stax api dependency")
public class RssPollingConsumerTest extends OSGiIntegrationTestSupport {

    @Test
    public void testGrabbingListOfEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Message in = exchange.getIn();
        assertNotNull(in);
        assertTrue(in.getBody() instanceof SyndFeed);
        assertTrue(in.getHeader(RssConstants.RSS_FEED) instanceof SyndFeed);

        SyndFeed feed = in.getHeader(RssConstants.RSS_FEED, SyndFeed.class);
        assertTrue(feed.getAuthor().contains("Jonathan Anstey"));

        SyndFeed body = in.getBody(SyndFeed.class);
        assertEquals(10, body.getEntries().size());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // load the resource first
                URL url = this.getClass().getResource("/org/apache/camel/itest/osgi/rss/rss20.xml");
                if (url != null) {
                    from("rss:" + url.toString() + "?splitEntries=false&consumer.delay=100").to("mock:result");
                } else {
                    throw new CamelException("Can't find the right rss file");
                }
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-rss"));
        
        return options;
    }
   
}
