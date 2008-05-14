package org.apache.camel.component.atom;

import java.util.List;

import org.apache.abdera.model.Feed;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for AtomPollingConsumer
 */
public class AtomPollingConsumerTest extends ContextTestSupport {

    public void testNoSplitEntries() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Message in = exchange.getIn();
        assertNotNull(in);
        assertTrue(in.getBody() instanceof List);
        assertTrue(in.getHeader(AtomEndpoint.HEADER_ATOM_FEED) instanceof Feed);

        Feed feed = in.getHeader(AtomEndpoint.HEADER_ATOM_FEED, Feed.class);
        assertEquals("James Strachan", feed.getAuthor().getName());

        List entries = in.getBody(List.class);
        assertEquals(7, entries.size());

    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("atom:file:src/test/data/feed.atom?splitEntries=false").to("mock:result");
            }
        };
    }

}
