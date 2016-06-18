package org.apache.camel.commands;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractLocalCamelControllerTest {

    private final DummyCamelController localCamelController;

    private final CamelContext context;

    public AbstractLocalCamelControllerTest() throws Exception {
        context = new DefaultCamelContext();
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("context1"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").id("route1").delay(100).to("mock:result1");
                from("direct:start2").id("route2").delay(100).to("mock:result2");
                from("direct:start3").id("route3").delay(100).to("mock:result3");
            }
        });

        localCamelController = new DummyCamelController(context);
    }

    @Before
    public void startContext() throws Exception {
        context.start();
    }

    @After
    public void stopContext() throws Exception {
        context.stop();
    }

    @Test
    public void testBrowseInflightExchangesWithMoreRoutes() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");
        context.createProducerTemplate().asyncSendBody("direct:start2", "Start two");
        context.createProducerTemplate().asyncSendBody("direct:start3", "Start three");

        // let the exchange proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertEquals("Context should contain three inflight exchanges", 3, inflightExchanges.size());
    }

    @Test
    public void testBrowseInflightExchangesWithNoRoutes() throws Exception {
        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertTrue("Context without routes should not have any inflight exchanges", inflightExchanges.isEmpty());
    }

    @Test
    public void testBrowseInflightExchangesWithOneRoute() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");

        // let the exchange proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertEquals("Context should contain one inflight exchange", 1, inflightExchanges.size());
    }

    @Test
    public void testBrowseInflightExchangesWithSpecificRoute() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");
        context.createProducerTemplate().asyncSendBody("direct:start2", "Start two");
        context.createProducerTemplate().asyncSendBody("direct:start3", "Start three");

        // let the exchanges proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", "route2", 0, false);

        assertEquals("Context should contain one inflight exchange for specific route", 1, inflightExchanges.size());
    }
}
