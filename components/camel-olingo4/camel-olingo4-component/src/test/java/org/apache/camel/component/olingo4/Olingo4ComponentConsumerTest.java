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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.junit.Test;

public class Olingo4ComponentConsumerTest extends AbstractOlingo4TestSupport {

    private static final String PEOPLE = "People";
    private static final String TEST_PEOPLE = "People('russellwhyte')";
    private static final String AIRPORTS = "Airports";

    public Olingo4ComponentConsumerTest() {
        setUseRouteBuilder(false);
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return false;
    }

    private void addRouteAndStartContext(RouteBuilder builder) throws Exception {
        context().addRoutes(builder);
        startCamelContext();
    }

    /**
     * Read entity set of the People object
     * and filter already seen items on subsequent exchanges
     * Use a delay since the mock endpoint does not always get
     * the correct number of exchanges before being satisfied.
     *
     * Note:
     * - consumer.splitResults is set to false since this ensures the first returned message
     *   contains all the results. This is preferred for the purposes of this test. The default
     *   will mean the first n messages contain the results (where n is the result total) then
     *   subsequent messages will be empty
     */
    @Test
    public void testConsumerReadFilterAlreadySeen() throws Exception {
        int expectedEntities = 20;
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-alreadyseen");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + PEOPLE + "?consumer.delay=2&consumer.sendEmptyMessageWhenIdle=true&consumer.splitResult=false&filterAlreadySeen=true")
                    .to("mock:consumer-alreadyseen");
            };
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

    @Test
    public void testConsumerReadFilterAlreadySeenWithPredicateAndSplitResults() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-kp-airport");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + AIRPORTS + "('KSFO')"
                        + "?filterAlreadySeen=true&"
                        + "consumer.delay=2&consumer.sendEmptyMessageWhenIdle=true&"
                        + "consumer.splitResult=true")
                    .to("mock:consumer-splitresult-kp-airport");
            };
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
     * Read entity set of the People object and split the results
     * into individual messages
     */
    @Test
    public void testConsumerReadSplitResults() throws Exception {
        int expectedMsgCount = 3;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult");
        mockEndpoint.expectedMinimumMessageCount(expectedMsgCount);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + PEOPLE + "?consumer.splitResult=true")
                    .to("mock:consumer-splitresult");
            };
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
            ClientEntity entity = (ClientEntity)body;
            ClientProperty nameProperty = entity.getProperty("UserName");
            assertNotNull(nameProperty);

            switch(i) {
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
     * Read value of the People object and split the results
     * into individual messages
     */
    @Test
    public void testConsumerReadClientValuesSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/FavoriteFeature?consumer.splitResult=true")
                    .to("mock:consumer-splitresult-value");
            };
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
     * Read value of the People object's AddressInfo collection value
     * & split the results into individual messages for each address
     */
    @Test
    public void testConsumerReadClientCollectionValuesSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-splitresult-collection-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/AddressInfo?consumer.splitResult=true")
                    .to("mock:consumer-splitresult-collection-value");
            };
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
     * Read value of the People object's AddressInfo collection value
     * & split the results into individual messages for each address
     */
    @Test
    public void testConsumerReadClientCollectionValuesNoSplitResults() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:consumer-nosplitresult-colleciton-value");
        mockEndpoint.expectedMinimumMessageCount(1);

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("olingo4://read/" + TEST_PEOPLE + "/AddressInfo?consumer.splitResult=false")
                    .to("mock:consumer-nosplitresult-colleciton-value");
            };
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
