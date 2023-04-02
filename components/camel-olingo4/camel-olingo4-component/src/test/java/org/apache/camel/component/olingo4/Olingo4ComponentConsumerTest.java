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
package org.apache.camel.component.olingo4;

import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class Olingo4ComponentConsumerTest extends AbstractOlingo4TestSupport {

    private static final String PEOPLE = "People";
    private static final String TEST_PEOPLE = "People('russellwhyte')";
    private static final String AIRPORTS = "Airports";

    public Olingo4ComponentConsumerTest() {
        setUseRouteBuilder(false);
    }

    private void addRouteAndStartContext(RouteBuilder builder) throws Exception {
        context().addRoutes(builder);
        startCamelContext();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        beanIntrospection.setLoggingLevel(LoggingLevel.INFO);
        beanIntrospection.setExtendedStatistics(true);
        return context;
    }

    @Test
    public void testConsumerQueryWithExpand() throws Exception {
        int expectedMsgCount = 1;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-query");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + PEOPLE + "?" + "$filter=LastName eq 'Whyte'&$expand=Trips").to("mock:consumer-query");
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
                assertTrue(body instanceof ClientEntity);
                ClientEntity e = (ClientEntity) body;
                ClientProperty nameProp = e.getProperty("UserName");
                assertNotNull(nameProp);
                assertEquals("russellwhyte", nameProp.getValue().toString());
            }
        }

        // should be reflection free
        long counter = PluginHelper.getBeanIntrospection(context).getInvokedCounter();
        assertEquals(0, counter);
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
        int expectedEntities = 20;
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-alreadyseen");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + PEOPLE
                     + "?delay=2&sendEmptyMessageWhenIdle=true&splitResult=false&filterAlreadySeen=true")
                        .to("mock:consumer-alreadyseen");
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
                assertTrue(body instanceof ClientEntitySet);
                ClientEntitySet set = (ClientEntitySet) body;
                assertEquals(expectedEntities, set.getEntities().size());
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
     * Read entity set of the People object and filter already seen items on subsequent exchanges Use a delay since the
     * mock endpoint does not always get the correct number of exchanges before being satisfied. Note: - splitResults is
     * set to false since this ensures the first returned message contains all the results. - sendEmptyMessageWhenIdle
     * is set to false so only 1 message should even be returned.
     */
    @Test
    public void testConsumerReadFilterAlreadySeenNoEmptyMsgs() throws Exception {
        int expectedEntities = 20;
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
                from("olingo4://read/" + PEOPLE
                     + "?delay=2&sendEmptyMessageWhenIdle=false&splitResult=false&filterAlreadySeen=true")
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
        assertTrue(body instanceof ClientEntitySet);
        ClientEntitySet set = (ClientEntitySet) body;
        assertEquals(expectedEntities, set.getEntities().size());
    }

    /**
     * WithPredicate in address FilterAlreadySeen: true SplitResults: true sendEmptyMessageWhenIdle: true
     *
     * @throws Exception
     */
    @Test
    public void testConsumerReadFilterAlreadySeenWithPredicate1() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-kp-airport");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + AIRPORTS + "('KSFO')" + "?filterAlreadySeen=true&"
                     + "delay=2&sendEmptyMessageWhenIdle=true&"
                     + "splitResult=true").to("mock:consumer-splitresult-kp-airport");
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
                assertTrue(body instanceof ClientEntity);
                ClientEntity ksfoEntity = (ClientEntity) body;
                ClientProperty nameProp = ksfoEntity.getProperty("Name");
                assertNotNull(nameProp);
                assertEquals("San Francisco International Airport", nameProp.getValue().toString());
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
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-kp-airport");

        //
        // Add 1 to count since we want to wait for full result time
        // before asserting that only 1 message has been delivered
        //
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount + 1);
        mockEndpoint.setResultWaitTime(6000L);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + AIRPORTS + "('KSFO')" + "?filterAlreadySeen=true&"
                     + "delay=2&sendEmptyMessageWhenIdle=false&"
                     + "splitResult=true").to("mock:consumer-splitresult-kp-airport");
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
        assertTrue(body instanceof ClientEntity);
        ClientEntity ksfoEntity = (ClientEntity) body;
        ClientProperty nameProp = ksfoEntity.getProperty("Name");
        assertNotNull(nameProp);
        assertEquals("San Francisco International Airport", nameProp.getValue().toString());

        // should be reflection free
        long counter = PluginHelper.getBeanIntrospection(context).getInvokedCounter();
        assertEquals(0, counter);
    }

    /**
     * Read entity set of the People object and split the results into individual messages
     */
    @Test
    public void testConsumerReadSplitResults() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + PEOPLE + "?splitResult=true").to("mock:consumer-splitresult");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();
        //
        // At least 3 individual messages in the exchange,
        // each containing a different entity.
        //
        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ClientEntity);
            ClientEntity entity = (ClientEntity) body;
            ClientProperty nameProperty = entity.getProperty("UserName");
            assertNotNull(nameProperty);

            switch (i) {
                case 0:
                    assertEquals("russellwhyte", nameProperty.getValue().toString());
                    break;
                case 1:
                    assertEquals("scottketchum", nameProperty.getValue().toString());
                    break;
                case 2:
                    assertEquals("ronaldmundy", nameProperty.getValue().toString());
                    break;
                default:
            }
        }
    }

    /**
     * Read value of the People object and split the results into individual messages
     */
    @Test
    public void testConsumerReadClientValuesSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/FavoriteFeature?splitResult=true")
                        .to("mock:consumer-splitresult-value");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();
        //
        // 1 individual message in the exchange
        //
        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(ClientPrimitiveValue.class, body);
        ClientPrimitiveValue value = (ClientPrimitiveValue) body;
        assertEquals("Feature1", value.toString());
    }

    /**
     * Read value of the People object's AddressInfo collection value & split the results into individual messages for
     * each address
     */
    @Test
    public void testConsumerReadClientCollectionValuesSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-collection-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/AddressInfo?splitResult=true")
                        .to("mock:consumer-splitresult-collection-value");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();
        //
        // 1 individual message in the exchange
        //
        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(ClientComplexValue.class, body);
        ClientComplexValue value = (ClientComplexValue) body;
        assertEquals("Boise", value.get("City").getComplexValue().get("Name").getValue().toString());
    }

    /**
     * Read value of the People object's AddressInfo collection value & split the results into individual messages for
     * each address
     */
    @Test
    public void testConsumerReadClientCollectionValuesNoSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-nosplitresult-colleciton-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/AddressInfo?splitResult=false")
                        .to("mock:consumer-nosplitresult-colleciton-value");
            }
        };
        addRouteAndStartContext(builder);

        mockEndpoint.assertIsSatisfied();
        //
        // 1 individual collection value message in the exchange
        //
        Object body = mockEndpoint.getExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(ClientCollectionValue.class, body);
        ClientCollectionValue<?> value = (ClientCollectionValue<?>) body;
        assertEquals(1, value.size());
        Iterator<?> propIter = value.iterator();
        Object propValueObj = propIter.next();
        assertIsInstanceOf(ClientComplexValue.class, propValueObj);
        ClientComplexValue propValue = (ClientComplexValue) propValueObj;
        assertEquals("Boise", propValue.get("City").getComplexValue().get("Name").getValue().toString());
    }
}
