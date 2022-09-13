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
package org.apache.camel.component.olingo2;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.olingo2.AbstractOlingo2AppAPITestSupport.createConfiguration;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.olingo2.api.Olingo2App} APIs.
 * <p>
 * The integration test runs against Apache Olingo 2.0 sample server which is dynamically installed and started during
 * the test.
 * </p>
 */
public class Olingo2ComponentConsumerTest extends AbstractOlingo2TestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final String TEST_SERVICE_URL = "http://localhost:" + PORT + "/MyFormula.svc";

    private static final JettyConfiguration JETTY_CONFIGURATION = createConfiguration(PORT);

    @RegisterExtension
    public static JettyEmbeddedService service = new JettyEmbeddedService(JETTY_CONFIGURATION);

    public Olingo2ComponentConsumerTest() {
        setDefaultTestProperty("serviceUri", "http://localhost:" + PORT + "/MyFormula.svc");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.adapt(ExtendedCamelContext.class).getBeanIntrospection().setLoggingLevel(LoggingLevel.INFO);
        context.adapt(ExtendedCamelContext.class).getBeanIntrospection().setExtendedStatistics(true);
        return context;
    }

    @BeforeAll
    public static void beforeClass() throws IOException {
        Olingo2TestUtil.generateSampleData(TEST_SERVICE_URL);

    }

    private void addRouteAndStartContext(RouteBuilder builder) throws Exception {
        context().addRoutes(builder);
        startCamelContext();
    }

    /**
     * Read entity set of the People object and filter already seen items on subsequent exchanges Use a delay since the
     * mock endpoint does not always get the correct number of exchanges before being satisfied. Note: - splitResults is
     * set to false since this ensures the first returned message contains all the results. This is preferred for the
     * purposes of this test. The default will mean the first n messages contain the results (where n is the result
     * total) then subsequent messages will be empty
     */
    @Test
    public void testConsumerReadFilterAlreadySeen() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-alreadyseen");
        mockEndpoint.expectedMessageCount(expectedMsgCount);
        mockEndpoint.setResultWaitTime(60000);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers?filterAlreadySeen=true&" + "delay=2&sendEmptyMessageWhenIdle=true&"
                     + "splitResult=false")
                             .to("mock:consumer-alreadyseen");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();

            if (i == 0) {
                //
                // First polled messages contained all the manufacturers
                //
                assertTrue(body instanceof ODataFeed);
                ODataFeed set = (ODataFeed) body;
                assertTrue(set.getEntries().size() > 0);
            } else {
                //
                // Subsequent polling messages should be empty
                // since the filterAlreadySeen property is true
                //
                assertNull(body);
            }
        }

        // should be reflection free
        long counter = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();
        assertEquals(0, counter);
    }

    /**
     * Read entity set of the People object and filter already seen items on subsequent exchanges Use a delay since the
     * mock endpoint does not always get the correct number of exchanges before being satisfied. Note: - splitResults is
     * set to false since this ensures the first returned message contains all the results. - sendEmptyMessageWhenIdle
     * is set to false so only 1 message should even be returned.
     */
    @Test
    public void testConsumerReadFilterAlreadySeenNoEmptyMsgs() throws Exception {
        int expectedMsgCount = 1;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-alreadyseen");
        //
        // Add 1 to count since we want to wait for full result time
        // before asserting that only 1 message has been delivered
        //
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount + 1);
        mockEndpoint.setResultWaitTime(6000L);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers?filterAlreadySeen=true&" + "delay=2&sendEmptyMessageWhenIdle=false&"
                     + "splitResult=false")
                             .to("mock:consumer-alreadyseen");
            }
        };
        addRouteAndStartContext(builder);

        //
        // Want to wait for entire result time & there should
        // be exactly 1 exchange transmitted to the endpoint
        //
        mockEndpoint.assertIsNotSatisfied();

        // Only 1 exchange so this is good!
        assertEquals(1, mockEndpoint.getExchanges().size());
        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();

        //
        // Only polled message contains all the entities
        //
        assertTrue(body instanceof ODataFeed);
        ODataFeed set = (ODataFeed) body;
        assertTrue(set.getEntries().size() > 0);
    }

    /**
     * WithPredicate in address FilterAlreadySeen: true SplitResults: true sendEmptyMessageWhenIdle: true
     *
     * @throws Exception
     */
    @Test
    public void testConsumerReadFilterAlreadySeenWithPredicate1() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-kp-manufacturer");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers('1')?filterAlreadySeen=true&" + "delay=2&sendEmptyMessageWhenIdle=true&"
                     + "splitResult=true")
                             .to("mock:consumer-splitresult-kp-manufacturer");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();

            if (i == 0) {
                //
                // First polled messages contained all the entities
                //
                assertTrue(body instanceof ODataEntry);
                ODataEntry entry = (ODataEntry) body;
                Object nameValue = entry.getProperties().get("Name");
                assertNotNull(nameValue);
                assertEquals("Star Powered Racing", nameValue.toString());
            } else {
                //
                // Subsequent polling messages should be empty
                // since the filterAlreadySeen property is true
                //
                assertNull(body);
            }
        }
    }

    /**
     * WithPredicate in address FilterAlreadySeen: true SplitResults: true sendEmptyMessageWhenIdle: false
     *
     * @throws Exception
     */
    @Test
    public void testConsumerReadFilterAlreadySeenWithPredicate2() throws Exception {
        int expectedMsgCount = 1;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-kp-manufacturer");

        //
        // Add 1 to count since we want to wait for full result time
        // before asserting that only 1 message has been delivered
        //
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount + 1);
        mockEndpoint.setResultWaitTime(6000L);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers('1')?filterAlreadySeen=true&" + "delay=2&sendEmptyMessageWhenIdle=false&"
                     + "splitResult=true")
                             .to("mock:consumer-splitresult-kp-manufacturer");
            }
        };
        addRouteAndStartContext(builder);

        //
        // Want to wait for entire result time & there should
        // be exactly 1 exchange transmitted to the endpoint
        //
        mockEndpoint.assertIsNotSatisfied();

        // Only 1 exchange so this is good!
        assertEquals(1, mockEndpoint.getExchanges().size());

        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();
        //
        // Only polled message contains the entity
        //
        assertTrue(body instanceof ODataEntry);
        ODataEntry entry = (ODataEntry) body;
        Object nameValue = entry.getProperties().get("Name");
        assertNotNull(nameValue);
        assertEquals("Star Powered Racing", nameValue.toString());

        // should be reflection free
        long counter = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();
        assertEquals(0, counter);
    }

    /**
     * Read value of the Address object and split the results into individual messages
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConsumerReadClientValuesSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-value");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(60000);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers('1')/Address?splitResult=true").to("mock:consumer-value");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();
        //
        // 1 individual message in the exchange
        //
        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(Map.class, body);
        Map<String, Object> value = (Map<String, Object>) body;
        Object addrObj = value.get("Address");
        assertIsInstanceOf(Map.class, addrObj);
        Map<String, Object> addrMap = (Map<String, Object>) addrObj;
        assertEquals("70173", addrMap.get("ZipCode"));
        assertEquals("Star Street 137", addrMap.get("Street"));
        assertEquals("Germany", addrMap.get("Country"));
        assertEquals("Stuttgart", addrMap.get("City"));
    }

    /**
     * Read entity set of the Manufacturers object and split the results into individual messages
     */
    @Test
    public void testConsumerReadSplitResults() throws Exception {
        int expectedMsgCount = 2;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers?splitResult=true").to("mock:consumer-splitresult");
            }
        };

        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();

        //
        // 2 individual messages in the exchange,
        // each containing a different entity.
        //
        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ODataEntry);
            ODataEntry entry = (ODataEntry) body;
            Map<String, Object> properties = entry.getProperties();
            assertNotNull(properties);

            Object name = properties.get("Name");
            assertNotNull(name);
            assertTrue(name.toString().contains("Powered Racing"));
        }
    }

    /**
     * Read entity set of the Manufacturers object and split the results into individual messages. Auto add inline count
     * or results to entities.
     */
    @Test
    public void testConsumerReadSplitResultsWithInlineCount() throws Exception {
        int expectedMsgCount = 2;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-with-count");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo2://read/Manufacturers/?$inlinecount=allpages&splitResult=true")
                        .to("mock:consumer-splitresult-with-count");
            }
        };

        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();

        //
        // 2 individual messages in the exchange,
        // each containing a different entity.
        //
        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ODataEntry);
            ODataEntry entry = (ODataEntry) body;
            Map<String, Object> properties = entry.getProperties();
            assertNotNull(properties);

            Object name = properties.get("Name");
            assertNotNull(name);
            assertTrue(name.toString().contains("Powered Racing"));
            Object resultCount = properties.get("ResultCount");
            assertTrue(((Integer) resultCount) > 0);
        }
    }
}
