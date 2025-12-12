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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
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
import org.apache.olingo.client.api.domain.ClientServiceDocument;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit6.TestSupport.assertStringContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.olingo4.api.Olingo4App} APIs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Olingo4ComponentProducerTest extends AbstractOlingo4WireMockTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo4ComponentProducerTest.class);

    private static final String TEST_CREATE_JSON = """
            {
              "UserName": "lewisblack",
              "FirstName": "Lewis",
              "LastName": "Black"
            }""";
    private static final String TEST_UPDATE_JSON = """
            {
              "UserName": "lewisblack",
              "FirstName": "Lewis",
              "MiddleName": "Black",
              "LastName": "Black"
            }""";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        beanIntrospection.setLoggingLevel(LoggingLevel.INFO);
        beanIntrospection.setExtendedStatistics(true);
        return context;
    }

    @Order(1)
    @Test
    public void testRead() {
        final Map<String, Object> headers = new HashMap<>();

        // Read metadata ($metadata) object
        final Edm metadata = (Edm) requestBodyAndHeaders("direct:readmetadata", null, headers);
        assertNotNull(metadata);
        assertEquals(1, metadata.getSchemas().size());

        // Read service document object
        final ClientServiceDocument document
                = (ClientServiceDocument) requestBodyAndHeaders("direct:readdocument", null, headers);

        assertNotNull(document);
        assertTrue(document.getEntitySets().size() > 1);
        LOG.info("Service document has {} entity sets", document.getEntitySets().size());

        // Read entity set of the People object
        final ClientEntitySet entities = (ClientEntitySet) requestBodyAndHeaders("direct:readentities", null, headers);
        assertNotNull(entities);
        assertEquals(5, entities.getEntities().size());

        // Read object count with query options passed through header
        final Long count = (Long) requestBodyAndHeaders("direct:readcount", null, headers);
        assertEquals(20, count.intValue());

        final ClientPrimitiveValue value = (ClientPrimitiveValue) requestBodyAndHeaders("direct:readvalue", null, headers);
        LOG.info("Client value \"{}\" has type {}", value, value.getTypeName());
        assertEquals("Male", value.asPrimitive().toString());

        final ClientPrimitiveValue singleProperty
                = (ClientPrimitiveValue) requestBodyAndHeaders("direct:readsingleprop", null, headers);
        assertTrue(singleProperty.isPrimitive());
        assertEquals("San Francisco International Airport", singleProperty.toString());

        final ClientComplexValue complexProperty
                = (ClientComplexValue) requestBodyAndHeaders("direct:readcomplexprop", null, headers);
        assertTrue(complexProperty.isComplex());
        assertEquals("San Francisco", complexProperty.get("City").getComplexValue().get("Name").getValue().toString());

        final ClientCollectionValue<?> collectionProperty
                = (ClientCollectionValue<?>) requestBodyAndHeaders("direct:readcollectionprop", null, headers);
        assertTrue(collectionProperty.isCollection());
        assertEquals(1, collectionProperty.size());
        Iterator<?> propIter = collectionProperty.iterator();
        Object propValueObj = propIter.next();
        assertIsInstanceOf(ClientComplexValue.class, propValueObj);
        ClientComplexValue propValue = (ClientComplexValue) propValueObj;
        assertEquals("Boise", propValue.get("City").getComplexValue().get("Name").getValue().toString());

        final ClientEntity entity = (ClientEntity) requestBodyAndHeaders("direct:readentitybyid", null, headers);
        assertNotNull(entity);
        assertEquals("Russell", entity.getProperty("FirstName").getValue().toString());

        final ClientEntity unbFuncReturn = (ClientEntity) requestBodyAndHeaders("direct:callunboundfunction", null, headers);
        assertNotNull(unbFuncReturn);

        // should be reflection free
        long counter = PluginHelper.getBeanIntrospection(context).getInvokedCounter();
        assertEquals(0, counter);
    }

    @Order(2)
    @Test
    public void testReadWithFilter() {
        // Read entity set with filter of the Airports object
        final ClientEntitySet entities = (ClientEntitySet) requestBody("direct:readwithfilter", null);

        assertNotNull(entities);
        assertEquals(1, entities.getEntities().size());
    }

    @Order(3)
    @Test
    public void testCreateUpdateDelete() {
        final ClientEntity clientEntity = createEntity();

        ClientEntity entity = requestBody("direct:create-entity", clientEntity);
        assertNotNull(entity);
        assertEquals("Lewis", entity.getProperty("FirstName").getValue().toString());
        assertEquals("", entity.getProperty("MiddleName").getValue().toString());

        // update
        clientEntity.getProperties()
                .add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));

        HttpStatusCode status = requestBody("direct:update-entity", clientEntity);
        assertNotNull(status, "Update status");
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode(), "Update status");
        LOG.info("Update entity status: {}", status);

        // delete
        status = requestBody("direct:delete-entity", null);
        assertNotNull(status, "Delete status");
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode(), "Delete status");
        LOG.info("Delete status: {}", status);

        // check for delete
        try {
            requestBody("direct:read-deleted-entity", null);
        } catch (CamelExecutionException e) {
            String causeMsg = e.getCause().getMessage();
            assertTrue(causeMsg.contains("[HTTP/1.1 404 Not Found]"));
        }
    }

    @Order(4)
    @Test
    public void testCreateUpdateDeleteFromJson() {
        ClientEntity entity = requestBody("direct:create-entity", TEST_CREATE_JSON);
        assertNotNull(entity);
        assertEquals("Lewis", entity.getProperty("FirstName").getValue().toString());
        assertEquals("Black", entity.getProperty("LastName").getValue().toString());
        assertEquals("lewisblack", entity.getProperty("UserName").getValue().toString());
        assertEquals("", entity.getProperty("MiddleName").getValue().toString());

        // update
        HttpStatusCode status = requestBody("direct:update-entity", TEST_UPDATE_JSON);
        assertNotNull(status, "Update status");
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode(), "Update status");
        LOG.info("Update entity status: {}", status);

        // delete
        status = requestBody("direct:delete-entity", null);
        assertNotNull(status, "Delete status");
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode(), "Delete status");
        LOG.info("Delete status: {}", status);

        // check for delete
        try {
            requestBody("direct:read-deleted-entity", null);
        } catch (CamelExecutionException e) {
            String causeMsg = e.getCause().getMessage();
            assertTrue(causeMsg.contains("[HTTP/1.1 404 Not Found]"));
        }
    }

    @Order(5)
    @Test
    public void testUnboundActionRequest() {
        final HttpStatusCode status = requestBody("direct:unbound-action-ResetDataSource", null);
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode());
    }

    @Order(6)
    @Test
    public void testBoundActionRequest() {
        final ClientEntity clientEntity = objFactory.newEntity(null);
        clientEntity.getProperties().add(
                objFactory.newPrimitiveProperty("userName", objFactory.newPrimitiveValueBuilder().buildString("scottketchum")));
        clientEntity.getProperties()
                .add(objFactory.newPrimitiveProperty("tripId", objFactory.newPrimitiveValueBuilder().buildInt32(0)));

        final HttpStatusCode status = requestBody("direct:bound-action-people", clientEntity);
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    @Order(7)
    @Test
    public void testEndpointHttpHeaders() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final ClientEntity entity = (ClientEntity) requestBodyAndHeaders("direct:read-etag", null, headers);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:check-etag-header");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.assertIsSatisfied();

        Map<String, String> responseHttpHeaders = (Map<String, String>) mockEndpoint.getExchanges().get(0).getIn()
                .getHeader("CamelOlingo4.responseHttpHeaders");
        assertEquals(responseHttpHeaders.get("ETag"), entity.getETag());

        Map<String, String> endpointHttpHeaders = new HashMap<>();
        endpointHttpHeaders.put("If-Match", entity.getETag());
        headers.put("CamelOlingo4.endpointHttpHeaders", endpointHttpHeaders);
        requestBodyAndHeaders("direct:delete-with-etag", null, headers);

        // check for deleted entity with ETag
        try {
            requestBody("direct:read-etag", null);
        } catch (CamelExecutionException e) {
            assertStringContains(e.getCause().getMessage(), "The request resource is not found.");
        }
    }

    /**
     * Read entity set of the People object and with no filter already seen, all items should be present in each message
     *
     * @throws Exception
     */
    @Order(8)
    @Test
    public void testProducerReadNoFilterAlreadySeen() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        String endpoint = "direct:read-people-nofilterseen";
        int expectedEntities = 20;
        int expectedMsgCount = 3;

        MockEndpoint mockEndpoint = getMockEndpoint("mock:producer-noalreadyseen");
        mockEndpoint.expectedMessageCount(expectedMsgCount);

        for (int i = 0; i < expectedMsgCount; ++i) {
            final ClientEntitySet entities = (ClientEntitySet) requestBodyAndHeaders(endpoint, null, headers);
            assertNotNull(entities);
        }

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ClientEntitySet);
            ClientEntitySet set = (ClientEntitySet) body;

            //
            // All messages contained all the entities
            //
            assertEquals(expectedEntities, set.getEntities().size());
        }
    }

    /**
     * Read entity set of the People object and filter already seen items on subsequent exchanges
     */
    @Order(9)
    @Test
    public void testProducerReadFilterAlreadySeen() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        String endpoint = "direct:read-people-filterseen";
        int expectedEntities = 20;
        int expectedMsgCount = 3;

        MockEndpoint mockEndpoint = getMockEndpoint("mock:producer-alreadyseen");
        mockEndpoint.expectedMessageCount(expectedMsgCount);

        for (int i = 0; i < expectedMsgCount; ++i) {
            final ClientEntitySet entities = (ClientEntitySet) requestBodyAndHeaders(endpoint, null, headers);
            assertNotNull(entities);
        }

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ClientEntitySet);
            ClientEntitySet set = (ClientEntitySet) body;

            if (i == 0) {
                //
                // First polled messages contained all the entities
                //
                assertEquals(expectedEntities, set.getEntities().size());
            } else {
                //
                // Subsequent messages should be empty
                // since the filterAlreadySeen property is true
                //
                assertEquals(0, set.getEntities().size());
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test routes for read
                from("direct:readmetadata").to("olingo4://read/$metadata");

                from("direct:readdocument").to("olingo4://read/");

                from("direct:readentities").to("olingo4://read/" + PEOPLE + "?$top=5&$orderby=FirstName asc");

                from("direct:readcount").to("olingo4://read/" + PEOPLE + "/$count");

                from("direct:readvalue").to("olingo4://read/" + TEST_PEOPLE + "/Gender/$value");

                from("direct:readsingleprop").to("olingo4://read/Airports('KSFO')/Name");

                from("direct:readcomplexprop").to("olingo4://read/Airports('KSFO')/Location");

                from("direct:readcollectionprop").to("olingo4://read/" + TEST_PEOPLE + "/AddressInfo");

                from("direct:readentitybyid").to("olingo4://read/" + TEST_PEOPLE + "");

                from("direct:readwithfilter")
                        .to("olingo4://read/Airports?$filter=Name eq 'San Francisco International Airport'");

                from("direct:callunboundfunction").to("olingo4://read/GetNearestAirport(lat=33,lon=-118)");

                // test route for create individual entity
                from("direct:create-entity").to("olingo4://create/" + PEOPLE);

                // test route for update
                from("direct:update-entity").to("olingo4://update/" + PEOPLE + "('lewisblack')");

                // test route for delete
                from("direct:delete-entity").to("olingo4://delete/" + PEOPLE + "('lewisblack')");

                // test route for delete
                from("direct:read-deleted-entity").to("olingo4://delete/" + PEOPLE + "('lewisblack')");

                from("direct:read-etag").to("olingo4://read/Airlines('AA')").to("mock:check-etag-header");

                from("direct:delete-with-etag").to("olingo4://delete/Airlines('AA')");

                from("direct:read-people-nofilterseen").to("olingo4://read/" + PEOPLE).to("mock:producer-noalreadyseen");

                from("direct:read-people-filterseen").to("olingo4://read/" + PEOPLE + "?filterAlreadySeen=true")
                        .to("mock:producer-alreadyseen");

                // test routes action's
                from("direct:unbound-action-ResetDataSource").to("olingo4://action/ResetDataSource");

                from("direct:bound-action-people").to(
                        "olingo4://action/" + TEST_PEOPLE + "/Trippin.ShareTrip");
            }
        };
    }

    @Override
    public String getClassIdentifier() {
        return "Olingo4ComponentProducerTest";
    }
}
