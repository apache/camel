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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchChangeRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchQueryRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchResponse;
import org.apache.camel.component.olingo4.api.batch.Operation;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.domain.ClientServiceDocument;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.ex.ODataError;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOptionKind;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.assertStringContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.olingo4.api.Olingo4App} APIs.
 * <p>
 * The integration test runs against using the sample OData 4.0 remote TripPin service published on
 * http://services.odata.org/TripPinRESTierService.
 * </p>
 */
public class Olingo4ComponentProducerTest extends AbstractOlingo4TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo4ComponentProducerTest.class);

    private static final String PEOPLE = "People";
    private static final String TEST_PEOPLE = "People('russellwhyte')";
    private static final String TEST_CREATE_KEY = "'lewisblack'";
    private static final String TEST_CREATE_PEOPLE = PEOPLE + "(" + TEST_CREATE_KEY + ")";
    private static final String TEST_CREATE_RESOURCE_CONTENT_ID = "1";
    private static final String TEST_UPDATE_RESOURCE_CONTENT_ID = "2";
    private static final String TEST_CREATE_JSON = "{\n" + "  \"UserName\": \"lewisblack\",\n" + "  \"FirstName\": \"Lewis\",\n"
                                                   + "  \"LastName\": \"Black\"\n" + "}";
    private static final String TEST_UPDATE_JSON
            = "{\n" + "  \"UserName\": \"lewisblack\",\n" + "  \"FirstName\": \"Lewis\",\n" + "  \"MiddleName\": \"Black\",\n"
              + "  \"LastName\": \"Black\"\n" + "}";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        beanIntrospection.setLoggingLevel(LoggingLevel.INFO);
        beanIntrospection.setExtendedStatistics(true);
        return context;
    }

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

    @Test
    public void testReadWithFilter() {
        // Read entity set with filter of the Airports object
        final ClientEntitySet entities = (ClientEntitySet) requestBody("direct:readwithfilter", null);

        assertNotNull(entities);
        assertEquals(1, entities.getEntities().size());
    }

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

    private ClientEntity createEntity() {
        ClientEntity clientEntity = objFactory.newEntity(null);

        clientEntity.getProperties().add(
                objFactory.newPrimitiveProperty("UserName", objFactory.newPrimitiveValueBuilder().buildString("lewisblack")));
        clientEntity.getProperties()
                .add(objFactory.newPrimitiveProperty("FirstName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));
        clientEntity.getProperties()
                .add(objFactory.newPrimitiveProperty("LastName", objFactory.newPrimitiveValueBuilder().buildString("Black")));

        return clientEntity;
    }

    @Test
    public void testBatch() {
        final List<Olingo4BatchRequest> batchParts = new ArrayList<>();

        // 1. Edm query
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(Constants.METADATA).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 2. Read entities
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 3. Read entity
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 4. Read with $top
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOptionKind.TOP.toString(), "5");
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).queryParams(queryParams)
                .build());

        // 5. Create entity
        ClientEntity clientEntity = createEntity();
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(PEOPLE).resourceUri(TEST_SERVICE_BASE_URL)
                .contentId(TEST_CREATE_RESOURCE_CONTENT_ID).operation(Operation.CREATE)
                .body(clientEntity).build());

        // 6. Update middle name in created entry
        clientEntity.getProperties()
                .add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL)
                .contentId(TEST_UPDATE_RESOURCE_CONTENT_ID)
                .operation(Operation.UPDATE).body(clientEntity).build());

        // 7. Delete entity
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL)
                .operation(Operation.DELETE).build());

        // 8. Read deleted entity to verify delete
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        // execute batch request
        final List<Olingo4BatchResponse> responseParts = requestBody("direct:batch", batchParts);
        assertNotNull(responseParts, "Batch response");
        assertEquals(8, responseParts.size(), "Batch responses expected");

        final Edm edm = (Edm) responseParts.get(0).getBody();
        assertNotNull(edm);
        LOG.info("Edm entity sets: {}", edm.getEntityContainer().getEntitySets());

        ClientEntitySet entitySet = (ClientEntitySet) responseParts.get(1).getBody();
        assertNotNull(entitySet);
        LOG.info("Read entities: {}", entitySet.getEntities());

        clientEntity = (ClientEntity) responseParts.get(2).getBody();
        assertNotNull(clientEntity);
        LOG.info("Read entiry properties: {}", clientEntity.getProperties());

        ClientEntitySet entitySetWithTop = (ClientEntitySet) responseParts.get(3).getBody();
        assertNotNull(entitySetWithTop);
        assertEquals(5, entitySetWithTop.getEntities().size());
        LOG.info("Read entities with $top=5: {}", entitySet.getEntities());

        clientEntity = (ClientEntity) responseParts.get(4).getBody();
        assertNotNull(clientEntity);
        LOG.info("Created entity: {}", clientEntity.getProperties());

        int statusCode = responseParts.get(5).getStatusCode();
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Update MdiddleName status: {}", statusCode);

        statusCode = responseParts.get(6).getStatusCode();
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Delete entity status: {}", statusCode);

        assertEquals(HttpStatusCode.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
        final ODataError error = (ODataError) responseParts.get(7).getBody();
        assertNotNull(error);
        LOG.info("Read deleted entity error: {}", error.getMessage());
    }

    @Test
    public void testUnboundActionRequest() {
        final HttpStatusCode status = requestBody("direct:unbound-action-ResetDataSource", null);
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), status.getStatusCode());
    }

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

                // test route for batch
                from("direct:batch").to("olingo4://batch");

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
}
