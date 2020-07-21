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
package org.apache.camel.component.atom;

import java.util.List;

import org.apache.abdera.model.Feed;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for AtomPollingConsumer
 */
public class AtomPollingConsumerTest extends CamelTestSupport {

    @Test
    void testNoSplitEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Message in = exchange.getIn();
        assertNotNull(in);
        assertTrue(in.getBody() instanceof List);
        assertTrue(in.getHeader(AtomConstants.ATOM_FEED) instanceof Feed);

        Feed feed = in.getHeader(AtomConstants.ATOM_FEED, Feed.class);
        assertEquals("James Strachan", feed.getAuthor().getName());

        List<?> entries = in.getBody(List.class);
        assertEquals(7, entries.size());
    }

    @Test
    void testUsingAtomUriParameter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
    }

    @Test
    void testNoCamelParametersInFeedUri() {
        AtomEndpoint endpoint = context.getEndpoint("atom:file:src/test/data/feed.atom?splitEntries=false", AtomEndpoint.class);
        assertEquals("file:src/test/data/feed.atom", endpoint.getFeedUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("atom:file:src/test/data/feed.atom?splitEntries=false").to("mock:result");

                // this is a bit weird syntax that normally is not used using the feedUri parameter
                from("atom:?feedUri=file:src/test/data/feed.atom&splitEntries=false").to("mock:result2");
            }
        };
    }

}
