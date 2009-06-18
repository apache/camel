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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.sun.syndication.feed.synd.SyndFeed;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class RssCustomAggregatorTest extends CamelTestSupport {

    @Test
    public void testMergingListOfEntries() throws Exception { 
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Message in = exchange.getIn();
        assertNotNull(in);
        assertTrue(in.getBody() instanceof SyndFeed);
        assertTrue(in.getHeader(RssConstants.RSS_FEED) instanceof SyndFeed);

        SyndFeed body = in.getBody(SyndFeed.class);
        assertEquals(20, body.getEntries().size());
    }
    
    @Override
    @Before
    public void setUp() throws Exception {        
        super.setUp();   
        copy("src/test/data/rss20.xml", "target/rss20.xml");                
    }
    
    private void copy(String source, String destination) throws IOException {
        BufferedReader input =  new BufferedReader(new FileReader(new File(source)));
        BufferedWriter output = new BufferedWriter(new FileWriter(new File(destination)));
 
        String line = null; 
        while ((line = input.readLine()) != null) {
            output.write(line);
        }
        input.close();
        output.close();       
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ex
                from("rss:file:src/test/data/rss20.xml?sortEntries=true&consumer.delay=50").to("seda:temp");
                from("rss:file:target/rss20.xml?sortEntries=true&consumer.delay=50").to("seda:temp");
                
                from("seda:temp").aggregate(new AggregateRssFeedCollection()).batchTimeout(5000L).to("mock:result");
                // END SNIPPET: ex
            }
        };
    }
}
