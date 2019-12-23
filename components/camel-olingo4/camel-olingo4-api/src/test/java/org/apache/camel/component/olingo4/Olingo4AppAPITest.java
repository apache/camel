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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.olingo4.api.Olingo4App;
import org.apache.camel.component.olingo4.api.Olingo4ResponseHandler;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchChangeRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchQueryRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchResponse;
import org.apache.camel.component.olingo4.api.batch.Operation;
import org.apache.camel.component.olingo4.api.impl.Olingo4AppImpl;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientEnumValue;
import org.apache.olingo.client.api.domain.ClientObjectFactory;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.apache.olingo.client.api.domain.ClientServiceDocument;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.client.api.serialization.ODataReader;
import org.apache.olingo.client.api.serialization.ODataSerializerException;
import org.apache.olingo.client.api.serialization.ODataWriter;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOptionKind;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * Integration test for
 * {@link org.apache.camel.component.olingo4.api.impl.Olingo4AppImpl} using the
 * sample OData 4.0 remote TripPin service published on
 * http://services.odata.org/TripPinRESTierService.
 */
public class Olingo4AppAPITest {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo4AppAPITest.class);
    private static final long TIMEOUT = 10;

    private static final String PEOPLE = "People";
    private static final String TEST_PEOPLE = "People('russellwhyte')";
    private static final String TEST_AIRLINE = "Airlines('FM')";
    private static final String TEST_AIRLINE_TO_UPDATE = "Airlines('AA')"; // Careful
                                                                           // using
                                                                           // this
                                                                           // as
                                                                           // it
                                                                           // get
                                                                           // updated!
    private static final String TEST_AIRLINE_TO_DELETE = "Airlines('MU')"; // Careful
                                                                           // using
                                                                           // this
                                                                           // as
                                                                           // it
                                                                           // gets
                                                                           // deleted!
    private static final String TRIPS = "Trips";
    private static final String TEST_CREATE_RESOURCE_CONTENT_ID = "1";
    private static final String TEST_UPDATE_RESOURCE_CONTENT_ID = "2";
    private static final String TEST_CREATE_KEY = "'lewisblack'";
    private static final String TEST_CREATE_PEOPLE = PEOPLE + "(" + TEST_CREATE_KEY + ")";
    private static final String TEST_AIRPORT = "Airports('KSFO')";
    private static final String TEST_AIRPORTS_SIMPLE_PROPERTY = TEST_AIRPORT + "/Name";
    private static final String TEST_AIRPORTS_COMPLEX_PROPERTY = TEST_AIRPORT + "/Location";
    private static final String TEST_AIRPORTS_SIMPLE_PROPERTY_VALUE = TEST_AIRPORTS_SIMPLE_PROPERTY + "/$value";
    private static final String COUNT_OPTION = "/$count";
    private static final String TEST_UNBOUND_ACTION_RESETDATASOURCE = "ResetDataSource";
    private static final String TEST_BOUND_ACTION_PEOPLE_SHARETRIP = TEST_PEOPLE + "/Microsoft.OData.Service.Sample.TrippinInMemory.Models.ShareTrip";

    private static final String TEST_SERVICE_BASE_URL = "http://services.odata.org/TripPinRESTierService";
    private static final ContentType TEST_FORMAT = ContentType.APPLICATION_JSON;
    private static final String TEST_FORMAT_STRING = TEST_FORMAT.toString();

    private static Olingo4App olingoApp;
    private static Edm edm;
    private final ODataClient odataClient = ODataClientFactory.getClient();
    private final ClientObjectFactory objFactory = odataClient.getObjectFactory();
    private final ODataReader reader = odataClient.getReader();

    @BeforeClass
    public static void beforeClass() throws Exception {
        setupClient();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (olingoApp != null) {
            olingoApp.close();
        }
    }

    protected static void setupClient() throws Exception {
        olingoApp = new Olingo4AppImpl(getRealServiceUrl(TEST_SERVICE_BASE_URL));
        olingoApp.setContentType(TEST_FORMAT_STRING);

        LOG.info("Read Edm ");
        final TestOlingo4ResponseHandler<Edm> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(null, Constants.METADATA, null, null, responseHandler);

        edm = responseHandler.await();
        LOG.info("Read default EntityContainer:  {}", responseHandler.await().getEntityContainer().getName());
    }

    /*
     * Every request to the demo OData 4.0
     * (http://services.odata.org/TripPinRESTierService) generates unique
     * service URL with postfix like (S(tuivu3up5ygvjzo5fszvnwfv)) for each
     * session This method makes reuest to the base URL and return URL with
     * generated postfix
     */
    @SuppressWarnings("deprecation")
    protected static String getRealServiceUrl(String baseUrl) throws ClientProtocolException, IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(baseUrl);
        HttpContext httpContext = new BasicHttpContext();
        httpclient.execute(httpGet, httpContext);
        HttpUriRequest currentReq = (HttpUriRequest)httpContext.getAttribute(ExecutionContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost)httpContext.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
        String currentUrl = (currentReq.getURI().isAbsolute()) ? currentReq.getURI().toString() : (currentHost.toURI() + currentReq.getURI());

        return currentUrl;
    }

    @Test
    public void testServiceDocument() throws Exception {
        final TestOlingo4ResponseHandler<ClientServiceDocument> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(null, "", null, null, responseHandler);
        final ClientServiceDocument serviceDocument = responseHandler.await();

        final Map<String, URI> entitySets = serviceDocument.getEntitySets();
        assertEquals("Service Entity Sets", 4, entitySets.size());
        LOG.info("Service Document Entries:  {}", entitySets);
    }

    @Test
    public void testReadEntitySet() throws Exception {
        final TestOlingo4ResponseHandler<ClientEntitySet> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, PEOPLE, null, null, responseHandler);

        final ClientEntitySet entitySet = responseHandler.await();
        assertNotNull(entitySet);
        assertEquals("Entity set count", 20, entitySet.getEntities().size());
        LOG.info("Entities:  {}", prettyPrint(entitySet));
    }

    @Test
    public void testReadUnparsedEntitySet() throws Exception {
        final TestOlingo4ResponseHandler<InputStream> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.uread(edm, PEOPLE, null, null, responseHandler);

        final InputStream rawEntitySet = responseHandler.await();
        assertNotNull("Data entity set", rawEntitySet);
        final ClientEntitySet entitySet = reader.readEntitySet(rawEntitySet, TEST_FORMAT);
        assertEquals("Entity set count", 20, entitySet.getEntities().size());
        LOG.info("Entries:  {}", prettyPrint(entitySet));
    }

    @Test
    public void testReadEntity() throws Exception {
        final TestOlingo4ResponseHandler<ClientEntity> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE, null, null, responseHandler);
        ClientEntity entity = responseHandler.await();
        assertEquals("Shanghai Airline", entity.getProperty("Name").getValue().toString());
        LOG.info("Single Entity:  {}", prettyPrint(entity));

        responseHandler.reset();

        olingoApp.read(edm, TEST_PEOPLE, null, null, responseHandler);
        entity = responseHandler.await();
        assertEquals("Russell", entity.getProperty("FirstName").getValue().toString());
        LOG.info("Single Entry:  {}", prettyPrint(entity));

        responseHandler.reset();
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOptionKind.EXPAND.toString(), TRIPS);

        olingoApp.read(edm, TEST_PEOPLE, queryParams, null, responseHandler);
        ClientEntity entityExpanded = responseHandler.await();
        LOG.info("Single People Entiry with expanded Trips relation:  {}", prettyPrint(entityExpanded));
    }

    @Test
    public void testReadUnparsedEntity() throws Exception {
        final TestOlingo4ResponseHandler<InputStream> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.uread(edm, TEST_AIRLINE, null, null, responseHandler);
        InputStream rawEntity = responseHandler.await();
        assertNotNull("Data entity", rawEntity);
        ClientEntity entity = reader.readEntity(rawEntity, TEST_FORMAT);
        assertEquals("Shanghai Airline", entity.getProperty("Name").getValue().toString());
        LOG.info("Single Entity:  {}", prettyPrint(entity));

        responseHandler.reset();

        olingoApp.uread(edm, TEST_PEOPLE, null, null, responseHandler);
        rawEntity = responseHandler.await();
        entity = reader.readEntity(rawEntity, TEST_FORMAT);
        assertEquals("Russell", entity.getProperty("FirstName").getValue().toString());
        LOG.info("Single Entity:  {}", prettyPrint(entity));

        responseHandler.reset();
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOptionKind.EXPAND.toString(), TRIPS);

        olingoApp.uread(edm, TEST_PEOPLE, queryParams, null, responseHandler);

        rawEntity = responseHandler.await();
        entity = reader.readEntity(rawEntity, TEST_FORMAT);
        LOG.info("Single People Entiry with expanded Trips relation:  {}", prettyPrint(entity));
    }

    @Test
    public void testReadUpdateProperties() throws Exception {
        // test simple property Airports.Name
        final TestOlingo4ResponseHandler<ClientPrimitiveValue> propertyHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRPORTS_SIMPLE_PROPERTY, null, null, propertyHandler);

        ClientPrimitiveValue name = propertyHandler.await();
        assertEquals("San Francisco International Airport", name.toString());
        LOG.info("Airport name property value {}", name.asPrimitive());

        final TestOlingo4ResponseHandler<ClientPrimitiveValue> valueHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRPORTS_SIMPLE_PROPERTY_VALUE, null, null, valueHandler);
        ClientPrimitiveValue nameValue = valueHandler.await();
        assertEquals("San Francisco International Airport", name.toString());
        LOG.info("Airport name property value {}", nameValue);

        TestOlingo4ResponseHandler<HttpStatusCode> statusHandler = new TestOlingo4ResponseHandler<>();
        // All properties updates (simple and complex) are performing through
        // ClientEntity object
        ClientEntity clientEntity = objFactory.newEntity(null);
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Middle")));
        olingoApp.update(edm, TEST_PEOPLE, null, clientEntity, statusHandler);
        HttpStatusCode statusCode = statusHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
        LOG.info("Name property updated with status {}", statusCode.getStatusCode());

        // Check for updated property by reading entire entity
        final TestOlingo4ResponseHandler<ClientEntity> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_PEOPLE, null, null, responseHandler);
        ClientEntity entity = responseHandler.await();
        assertEquals("Middle", entity.getProperty("MiddleName").getValue().toString());
        LOG.info("Updated Single Entity:  {}", prettyPrint(entity));
    }

    @Test
    public void testReadCount() throws Exception {
        final TestOlingo4ResponseHandler<Long> countHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, PEOPLE + COUNT_OPTION, null, null, countHandler);
        Long count = countHandler.await();
        assertEquals(20, count.intValue());
        LOG.info("People count: {}", count);
    }

    /**
     * The Airline resource is implemented with Optimistic Concurrency. This
     * requires an eTag to be first fetched via a read before performing patch,
     * update, delete or merge operations. The test should complete successfully
     * and not throw an error of the form 'The request need to have If-Match or
     * If-None-Match header'
     *
     * @throws Exception
     */
    @Test
    public void testDeleteOptimisticConcurrency() throws Exception {
        // test simple property Airlines
        final TestOlingo4ResponseHandler<ClientEntity> entityHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE_TO_DELETE, null, null, entityHandler);

        // Confirm presence of eTag
        ClientEntity airline = entityHandler.await();
        assertNotNull(airline);
        assertNotNull(airline.getETag());

        TestOlingo4ResponseHandler<HttpStatusCode> statusHandler = new TestOlingo4ResponseHandler<>();

        //
        // Call delete
        //
        olingoApp.delete(TEST_AIRLINE_TO_DELETE, null, statusHandler);

        HttpStatusCode statusCode = statusHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
        LOG.info("Deleted entity at {}", TEST_AIRLINE_TO_DELETE);

        // Check for deleted entity
        final TestOlingo4ResponseHandler<HttpStatusCode> responseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.read(edm, TEST_AIRLINE_TO_DELETE, null, null, responseHandler);

        statusCode = statusHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
        LOG.info("Deleted entity at {}", TEST_AIRLINE_TO_DELETE);
    }

    /**
     * The Airline resource is implemented with Optimistic Concurrency. This
     * requires an eTag to be first fetched via a read before performing patch,
     * update, delete or merge operations. The test should complete successfully
     * and not throw an error of the form 'The request need to have If-Match or
     * If-None-Match header'
     *
     * @throws Exception
     */
    @Test
    public void testPatchOptimisticConcurrency() throws Exception {
        // test simple property Airlines
        final TestOlingo4ResponseHandler<ClientEntity> entityHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE_TO_UPDATE, null, null, entityHandler);

        // Confirm presence of eTag
        ClientEntity airline = entityHandler.await();
        assertNotNull(airline);
        assertNotNull(airline.getETag());

        TestOlingo4ResponseHandler<HttpStatusCode> statusHandler = new TestOlingo4ResponseHandler<>();
        ClientEntity clientEntity = objFactory.newEntity(null);
        String newAirlineName = "The Patched American Airlines";
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("Name", objFactory.newPrimitiveValueBuilder().buildString(newAirlineName)));

        //
        // Call patch
        //
        olingoApp.patch(edm, TEST_AIRLINE_TO_UPDATE, null, clientEntity, statusHandler);

        HttpStatusCode statusCode = statusHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
        LOG.info("Name property updated with status {}", statusCode.getStatusCode());

        // Check for updated entity
        final TestOlingo4ResponseHandler<ClientEntity> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE_TO_UPDATE, null, null, responseHandler);
        ClientEntity entity = responseHandler.await();
        assertEquals(newAirlineName, entity.getProperty("Name").getValue().toString());
        LOG.info("Updated Single Entity:  {}", prettyPrint(entity));
    }

    /**
     * The Airline resource is implemented with Optimistic Concurrency. This
     * requires an eTag to be first fetched via a read before performing patch,
     * update, delete or merge operations. The test should complete successfully
     * and not throw an error of the form 'The request need to have If-Match or
     * If-None-Match header'
     *
     * @throws Exception
     */
    @Test
    public void testUpdateOptimisticConcurrency() throws Exception {
        // test simple property Airlines
        final TestOlingo4ResponseHandler<ClientEntity> entityHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE_TO_UPDATE, null, null, entityHandler);

        // Confirm presence of eTag
        ClientEntity airline = entityHandler.await();
        assertNotNull(airline);
        assertNotNull(airline.getETag());

        TestOlingo4ResponseHandler<HttpStatusCode> statusHandler = new TestOlingo4ResponseHandler<>();
        ClientEntity clientEntity = objFactory.newEntity(null);
        String newAirlineName = "The Updated American Airlines";
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("Name", objFactory.newPrimitiveValueBuilder().buildString(newAirlineName)));

        //
        // Call update
        //
        olingoApp.update(edm, TEST_AIRLINE_TO_UPDATE, null, clientEntity, statusHandler);

        HttpStatusCode statusCode = statusHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
        LOG.info("Name property updated with status {}", statusCode.getStatusCode());

        // Check for updated entity
        final TestOlingo4ResponseHandler<ClientEntity> responseHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.read(edm, TEST_AIRLINE_TO_UPDATE, null, null, responseHandler);
        ClientEntity entity = responseHandler.await();
        assertEquals(newAirlineName, entity.getProperty("Name").getValue().toString());
        LOG.info("Updated Single Entity:  {}", prettyPrint(entity));
    }

    @Test
    public void testCreateUpdateDeleteEntity() throws Exception {

        // create an entity to update
        final TestOlingo4ResponseHandler<ClientEntity> entryHandler = new TestOlingo4ResponseHandler<>();

        olingoApp.create(edm, PEOPLE, null, createEntity(), entryHandler);

        ClientEntity createdEntity = entryHandler.await();
        LOG.info("Created Entity:  {}", prettyPrint(createdEntity));

        final TestOlingo4ResponseHandler<HttpStatusCode> statusHandler = new TestOlingo4ResponseHandler<>();
        ClientEntity updateEntity = createEntity();
        updateEntity.getProperties().add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Middle")));
        olingoApp.update(edm, TEST_CREATE_PEOPLE, null, updateEntity, statusHandler);
        statusHandler.await();

        statusHandler.reset();
        updateEntity = createEntity();
        updateEntity.getProperties().add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Middle Patched")));
        olingoApp.patch(edm, TEST_CREATE_PEOPLE, null, updateEntity, statusHandler);
        statusHandler.await();

        entryHandler.reset();
        olingoApp.read(edm, TEST_CREATE_PEOPLE, null, null, entryHandler);
        ClientEntity updatedEntity = entryHandler.await();
        LOG.info("Updated Entity successfully:  {}", prettyPrint(updatedEntity));

        statusHandler.reset();
        olingoApp.delete(TEST_CREATE_PEOPLE, null, statusHandler);
        HttpStatusCode statusCode = statusHandler.await();
        LOG.info("Deletion of Entity was successful:  {}: {}", statusCode.getStatusCode(), statusCode.getInfo());

        try {
            LOG.info("Verify Delete Entity");

            entryHandler.reset();
            olingoApp.read(edm, TEST_CREATE_PEOPLE, null, null, entryHandler);

            entryHandler.await();
            fail("Entity not deleted!");
        } catch (Exception e) {
            LOG.info("Deleted entity not found: {}", e.getMessage());
        }
    }

    @Test
    public void testBatchRequest() throws Exception {

        final List<Olingo4BatchRequest> batchParts = new ArrayList<>();

        // 1. Edm query
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(Constants.METADATA).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 2. Query entity set
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 3. Read entity
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 4. Read with expand
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOptionKind.EXPAND.toString(), TRIPS);
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_PEOPLE).queryParams(queryParams).resourceUri(TEST_SERVICE_BASE_URL).build());

        // 5. Create entity
        final ClientEntity clientEntity = createEntity();
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).contentId(TEST_CREATE_RESOURCE_CONTENT_ID).operation(Operation.CREATE)
            .body(clientEntity).build());

        // 6. Update entity
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).contentId(TEST_UPDATE_RESOURCE_CONTENT_ID)
            .operation(Operation.UPDATE).body(clientEntity).build());

        // 7. Delete entity
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).operation(Operation.DELETE).build());

        // 8. Read to verify entity delete
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_CREATE_PEOPLE).resourceUri(TEST_SERVICE_BASE_URL).build());

        final TestOlingo4ResponseHandler<List<Olingo4BatchResponse>> responseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.batch(edm, null, batchParts, responseHandler);

        final List<Olingo4BatchResponse> responseParts = responseHandler.await(15, TimeUnit.MINUTES);
        assertEquals("Batch responses expected", 8, responseParts.size());

        assertNotNull(responseParts.get(0).getBody());

        final ClientEntitySet clientEntitySet = (ClientEntitySet)responseParts.get(1).getBody();
        assertNotNull(clientEntitySet);
        LOG.info("Batch entity set:  {}", prettyPrint(clientEntitySet));

        ClientEntity returnClientEntity = (ClientEntity)responseParts.get(2).getBody();
        assertNotNull(returnClientEntity);
        LOG.info("Batch read entity:  {}", prettyPrint(returnClientEntity));

        returnClientEntity = (ClientEntity)responseParts.get(3).getBody();
        assertNotNull(returnClientEntity);
        LOG.info("Batch read entity with expand:  {}", prettyPrint(returnClientEntity));

        ClientEntity createdClientEntity = (ClientEntity)responseParts.get(4).getBody();
        assertNotNull(createdClientEntity);
        assertEquals(TEST_CREATE_RESOURCE_CONTENT_ID, responseParts.get(4).getContentId());
        LOG.info("Batch created entity:  {}", prettyPrint(returnClientEntity));

        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), responseParts.get(5).getStatusCode());
        assertEquals(TEST_UPDATE_RESOURCE_CONTENT_ID, responseParts.get(5).getContentId());
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), responseParts.get(6).getStatusCode());
        assertEquals(HttpStatusCode.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
    }

    @Test
    public void testUnboundActionRequest() throws Exception {
        final TestOlingo4ResponseHandler<HttpStatusCode> responseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.action(edm, TEST_UNBOUND_ACTION_RESETDATASOURCE, null, null, responseHandler);

        final HttpStatusCode statusCode = responseHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
    }

    @Test
    public void testBoundActionRequest() throws Exception {
        final ClientEntity clientEntity = objFactory.newEntity(null);
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("userName", objFactory.newPrimitiveValueBuilder().buildString("scottketchum")));
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("tripId", objFactory.newPrimitiveValueBuilder().buildInt32(0)));

        final TestOlingo4ResponseHandler<HttpStatusCode> responseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.action(edm, TEST_BOUND_ACTION_PEOPLE_SHARETRIP, null, clientEntity, responseHandler);

        final HttpStatusCode statusCode = responseHandler.await();
        assertEquals(HttpStatusCode.NO_CONTENT, statusCode);
    }

    // Unfortunately there is no action that returns a client entity. So we fake
    // one
    @Test
    public void testBoundActionRequestWithClientEntityResponse() throws Exception {
        final ODataClient odataClient = ODataClientFactory.getClient();
        final ODataWriter odataWriter = odataClient.getWriter();

        final HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        httpClientBuilder.addInterceptorFirst(new HttpResponseInterceptor() {
            @Override
            public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
                if (response.getStatusLine().getStatusCode() == HttpStatusCode.NO_CONTENT.getStatusCode()) {
                    try {
                        response.setEntity(new InputStreamEntity(odataWriter.writeEntity(createEntity(), ContentType.JSON),
                                                                 org.apache.http.entity.ContentType.parse(ContentType.JSON.toContentTypeString())));
                        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                    } catch (ODataSerializerException e) {
                        throw new IOException(e);
                    }
                }
            }
        });
        final Olingo4App olingoApp = new Olingo4AppImpl(getRealServiceUrl(TEST_SERVICE_BASE_URL), httpClientBuilder);
        olingoApp.setContentType(TEST_FORMAT_STRING);

        final TestOlingo4ResponseHandler<Edm> responseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.read(null, Constants.METADATA, null, null, responseHandler);
        final Edm edm = responseHandler.await();

        final ClientEntity clientEntity = objFactory.newEntity(null);
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("userName", objFactory.newPrimitiveValueBuilder().buildString("scottketchum")));
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("tripId", objFactory.newPrimitiveValueBuilder().buildInt32(0)));

        final TestOlingo4ResponseHandler<ClientEntity> actionResponseHandler = new TestOlingo4ResponseHandler<>();
        olingoApp.action(edm, TEST_BOUND_ACTION_PEOPLE_SHARETRIP, null, clientEntity, actionResponseHandler);

        final ClientEntity result = actionResponseHandler.await();
        assertEquals("lewisblack", result.getProperty("UserName").getValue().toString());
    }

    private ClientEntity createEntity() {
        ClientEntity clientEntity = objFactory.newEntity(null);

        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("UserName", objFactory.newPrimitiveValueBuilder().buildString("lewisblack")));
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("FirstName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));
        clientEntity.getProperties().add(objFactory.newPrimitiveProperty("LastName", objFactory.newPrimitiveValueBuilder().buildString("Black")));

        return clientEntity;
    }

    private static String prettyPrint(ClientEntitySet entitySet) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (ClientEntity entity : entitySet.getEntities()) {
            builder.append(prettyPrint(entity.getProperties(), 1)).append('\n');
        }
        builder.append("]\n");
        return builder.toString();
    }

    private static String prettyPrint(ClientEntity entity) {
        return prettyPrint(entity.getProperties(), 0);
    }

    @SuppressWarnings("unchecked")
    private static String prettyPrint(Map<String, Object> properties, int level) {
        StringBuilder b = new StringBuilder();
        Set<Entry<String, Object>> entries = properties.entrySet();

        for (Entry<String, Object> entry : entries) {
            intend(b, level);
            b.append(entry.getKey()).append(": ");
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = prettyPrint((Map<String, Object>)value, level + 1);
            } else if (value instanceof Calendar) {
                Calendar cal = (Calendar)value;
                value = DateFormat.getInstance().format(cal.getTime());
            }
            b.append(value).append("\n");
        }
        // remove last line break
        b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    private static String prettyPrint(Collection<ClientProperty> properties, int level) {
        StringBuilder b = new StringBuilder();

        for (Object property : properties) {
            intend(b, level);
            if (property instanceof ClientProperty) {
                ClientProperty entry = (ClientProperty)property;
                ClientValue value = entry.getValue();
                if (value.isCollection()) {
                    ClientCollectionValue cclvalue = value.asCollection();
                    b.append(entry.getName()).append(": ");
                    b.append(prettyPrint(cclvalue.asJavaCollection(), level + 1));
                } else if (value.isComplex()) {
                    ClientComplexValue cpxvalue = value.asComplex();
                    b.append(prettyPrint(cpxvalue.asJavaMap(), level + 1));
                } else if (value.isEnum()) {
                    ClientEnumValue cnmvalue = value.asEnum();
                    b.append(entry.getName()).append(": ");
                    b.append(cnmvalue.getValue()).append("\n");
                } else if (value.isPrimitive()) {
                    b.append(entry.getName()).append(": ");
                    b.append(entry.getValue()).append("\n");
                }
            } else {
                b.append(property.toString()).append("\n");
            }

        }
        return b.toString();
    }

    private static void intend(StringBuilder builder, int intendLevel) {
        for (int i = 0; i < intendLevel; i++) {
            builder.append("  ");
        }
    }

    private static final class TestOlingo4ResponseHandler<T> implements Olingo4ResponseHandler<T> {
        private T response;
        private Exception error;
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onResponse(T response, Map<String, String> responseHeaders) {
            this.response = response;
            if (LOG.isDebugEnabled()) {
                if (response instanceof ClientEntitySet) {
                    LOG.debug("Received response: {}", prettyPrint((ClientEntitySet)response));
                } else if (response instanceof ClientEntity) {
                    LOG.debug("Received response: {}", prettyPrint((ClientEntity)response));
                } else {
                    LOG.debug("Received response: {}", response);
                }
            }
            latch.countDown();
        }

        @Override
        public void onException(Exception ex) {
            error = ex;
            latch.countDown();
        }

        @Override
        public void onCanceled() {
            error = new IllegalStateException("Request Canceled");
            latch.countDown();
        }

        public T await() throws Exception {
            return await(TIMEOUT, TimeUnit.SECONDS);
        }

        public T await(long timeout, TimeUnit unit) throws Exception {
            assertTrue("Timeout waiting for response", latch.await(timeout, unit));
            if (error != null) {
                throw error;
            }
            assertNotNull("Response", response);
            return response;
        }

        public void reset() {
            latch.countDown();
            latch = new CountDownLatch(1);
            response = null;
            error = null;
        }
    }
}
