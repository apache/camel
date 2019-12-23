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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.camel.component.olingo2.api.impl.SystemQueryOption;
import org.apache.camel.component.olingo2.internal.Olingo2Constants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.apache.olingo.odata2.api.servicedocument.ServiceDocument;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.olingo2.api.Olingo2App}
 * APIs.
 * <p>
 * The integration test runs against Apache Olingo 2.0 sample server which is
 * dynamically installed and started during the test.
 * </p>
 */
public class Olingo2ComponentProducerTest extends AbstractOlingo2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo2ComponentProducerTest.class);
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final String ID_PROPERTY = "Id";
    private static final String MANUFACTURERS = "Manufacturers";
    private static final String TEST_MANUFACTURER = "Manufacturers('1')";
    private static final String CARS = "Cars";
    private static final String TEST_RESOURCE_CONTENT_ID = "1";
    private static final String ADDRESS = "Address";
    private static final String TEST_RESOURCE = "$1";
    private static final String TEST_RESOURCE_ADDRESS = TEST_RESOURCE + "/Address";
    private static final String TEST_CREATE_MANUFACTURER = "DefaultContainer.Manufacturers('123')";
    private static final String TEST_SERVICE_URL = "http://localhost:" + PORT + "/MyFormula.svc";

    private static Olingo2SampleServer server;

    public Olingo2ComponentProducerTest() {
        setDefaultTestProperty("serviceUri", "http://localhost:" + PORT + "/MyFormula.svc");
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        startServers(PORT);
        Olingo2SampleServer.generateSampleData(TEST_SERVICE_URL);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    protected static void startServers(int port) throws Exception {
        server = new Olingo2SampleServer(port, "/olingo2_ref");
        server.start();
    }

    @Test
    public void testRead() throws Exception {
        final Map<String, Object> headers = new HashMap<>();

        // read ServiceDocument
        final ServiceDocument document = requestBodyAndHeaders("direct:READSERVICEDOC", null, headers);
        assertNotNull(document);
        assertFalse("ServiceDocument entity sets", document.getEntitySetsInfo().isEmpty());
        LOG.info("Service document has {} entity sets", document.getEntitySetsInfo().size());

        // parameter type is java.util.Map
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOption.$top.name(), "5");
        headers.put("CamelOlingo2.queryParams", queryParams);

        // read ODataFeed
        final ODataFeed manufacturers = requestBodyAndHeaders("direct:READFEED", null, headers);
        assertNotNull(manufacturers);
        final List<ODataEntry> manufacturersEntries = manufacturers.getEntries();
        assertFalse("Manufacturers empty entries", manufacturersEntries.isEmpty());
        LOG.info("Manufacturers feed has {} entries", manufacturersEntries.size());

        // read ODataEntry
        headers.clear();
        headers.put(Olingo2Constants.PROPERTY_PREFIX + "keyPredicate", "'1'");
        final ODataEntry manufacturer = requestBodyAndHeaders("direct:READENTRY", null, headers);
        assertNotNull(manufacturer);
        final Map<String, Object> properties = manufacturer.getProperties();
        assertEquals("Manufacturer Id", "1", properties.get(ID_PROPERTY));
        LOG.info("Manufacturer: {}", properties);
    }

    @Test
    public void testCreateUpdateDelete() throws Exception {
        final Map<String, Object> data = getEntityData();
        Map<String, Object> address;

        final ODataEntry manufacturer = requestBody("direct:CREATE", data);
        assertNotNull("Created Manufacturer", manufacturer);
        final Map<String, Object> properties = manufacturer.getProperties();
        assertEquals("Created Manufacturer Id", "123", properties.get(ID_PROPERTY));
        LOG.info("Created Manufacturer: {}", properties);

        // update
        data.put("Name", "MyCarManufacturer Renamed");
        address = (Map<String, Object>)data.get("Address");
        address.put("Street", "Main Street");

        HttpStatusCodes status = requestBody("direct:UPDATE", data);
        assertNotNull("Update status", status);
        assertEquals("Update status", HttpStatusCodes.NO_CONTENT.getStatusCode(), status.getStatusCode());
        LOG.info("Update status: {}", status);

        // delete
        status = requestBody("direct:DELETE", null);
        assertNotNull("Delete status", status);
        assertEquals("Delete status", HttpStatusCodes.NO_CONTENT.getStatusCode(), status.getStatusCode());
        LOG.info("Delete status: {}", status);
    }

    private Map<String, Object> getEntityData() {
        final Map<String, Object> data = new HashMap<>();
        data.put("Id", "123");
        data.put("Name", "MyCarManufacturer");
        data.put("Founded", new Date());
        Map<String, Object> address = new HashMap<>();
        address.put("Street", "Main");
        address.put("ZipCode", "42421");
        address.put("City", "Fairy City");
        address.put("Country", "FarFarAway");
        data.put("Address", address);
        return data;
    }

    @Test
    public void testBatch() throws Exception {
        final List<Olingo2BatchRequest> batchParts = new ArrayList<>();

        // 1. Edm query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(Olingo2AppImpl.METADATA).build());

        // 2. feed query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(MANUFACTURERS).build());

        // 3. read
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).build());

        // 4. read with expand
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).queryParams(queryParams).build());

        // 5. create
        final Map<String, Object> data = getEntityData();
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(MANUFACTURERS).contentId(TEST_RESOURCE_CONTENT_ID).operation(Operation.CREATE).body(data).build());

        // 6. update address in created entry
        final Map<String, Object> updateData = new HashMap<>(data);
        Map<String, Object> address = (Map<String, Object>)updateData.get(ADDRESS);
        address.put("Street", "Main Street");
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE_ADDRESS).operation(Operation.UPDATE).body(address).build());

        // 7. update
        updateData.put("Name", "MyCarManufacturer Renamed");
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.UPDATE).body(updateData).build());

        // 8. delete
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.DELETE).build());

        // 9. read to verify delete
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_CREATE_MANUFACTURER).build());

        // execute batch request
        final List<Olingo2BatchResponse> responseParts = requestBody("direct:BATCH", batchParts);
        assertNotNull("Batch response", responseParts);
        assertEquals("Batch responses expected", 9, responseParts.size());

        final Edm edm = (Edm)responseParts.get(0).getBody();
        assertNotNull(edm);
        LOG.info("Edm entity sets: {}", edm.getEntitySets());

        final ODataFeed feed = (ODataFeed)responseParts.get(1).getBody();
        assertNotNull(feed);
        LOG.info("Read feed: {}", feed.getEntries());

        ODataEntry dataEntry = (ODataEntry)responseParts.get(2).getBody();
        assertNotNull(dataEntry);
        LOG.info("Read entry: {}", dataEntry.getProperties());

        dataEntry = (ODataEntry)responseParts.get(3).getBody();
        assertNotNull(dataEntry);
        LOG.info("Read entry with $expand: {}", dataEntry.getProperties());

        dataEntry = (ODataEntry)responseParts.get(4).getBody();
        assertNotNull(dataEntry);
        LOG.info("Created entry: {}", dataEntry.getProperties());

        int statusCode = responseParts.get(5).getStatusCode();
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Update address status: {}", statusCode);

        statusCode = responseParts.get(6).getStatusCode();
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Update entry status: {}", statusCode);

        statusCode = responseParts.get(7).getStatusCode();
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Delete status: {}", statusCode);

        assertEquals(HttpStatusCodes.NOT_FOUND.getStatusCode(), responseParts.get(8).getStatusCode());
        final Exception exception = (Exception)responseParts.get(8).getBody();
        assertNotNull(exception);
        LOG.info("Read deleted entry exception: {}", exception);
    }

    /**
     * Read entity set of the People object and with no filter already seen, all
     * items should be present in each message
     *
     * @throws Exception
     */
    @Test
    public void testProducerReadNoFilterAlreadySeen() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        String endpoint = "direct:read-people-nofilterseen";
        int expectedMsgCount = 3;

        MockEndpoint mockEndpoint = getMockEndpoint("mock:producer-noalreadyseen");
        mockEndpoint.expectedMessageCount(expectedMsgCount);

        int expectedEntities = -1;
        for (int i = 0; i < expectedMsgCount; ++i) {
            final ODataFeed manufacturers = (ODataFeed)requestBodyAndHeaders(endpoint, null, headers);
            assertNotNull(manufacturers);
            if (i == 0) {
                expectedEntities = manufacturers.getEntries().size();
            }
        }

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ODataFeed);
            ODataFeed set = (ODataFeed)body;

            //
            // All messages contained all the manufacturers
            //
            assertEquals(expectedEntities, set.getEntries().size());
        }
    }

    /**
     * Read entity set of the People object and filter already seen items on
     * subsequent exchanges
     */
    @Test
    public void testProducerReadFilterAlreadySeen() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        String endpoint = "direct:read-people-filterseen";
        int expectedMsgCount = 3;

        MockEndpoint mockEndpoint = getMockEndpoint("mock:producer-alreadyseen");
        mockEndpoint.expectedMessageCount(expectedMsgCount);

        int expectedEntities = -1;
        for (int i = 0; i < expectedMsgCount; ++i) {
            final ODataFeed manufacturers = (ODataFeed)requestBodyAndHeaders(endpoint, null, headers);
            assertNotNull(manufacturers);
            if (i == 0) {
                expectedEntities = manufacturers.getEntries().size();
            }
        }

        mockEndpoint.assertIsSatisfied();

        for (int i = 0; i < expectedMsgCount; ++i) {
            Object body = mockEndpoint.getExchanges().get(i).getIn().getBody();
            assertTrue(body instanceof ODataFeed);
            ODataFeed set = (ODataFeed)body;

            if (i == 0) {
                //
                // First polled messages contained all the manufacturers
                //
                assertEquals(expectedEntities, set.getEntries().size());
            } else {
                //
                // Subsequent messages should be empty
                // since the filterAlreadySeen property is true
                //
                assertEquals(0, set.getEntries().size());
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test routes for read
                from("direct:READSERVICEDOC").to("olingo2://read/");

                from("direct:READFEED").to("olingo2://read/Manufacturers?$orderBy=Name%20asc");

                from("direct:READENTRY").to("olingo2://read/DefaultContainer.Manufacturers");

                // test route for create
                from("direct:CREATE").to("olingo2://create/Manufacturers");

                // test route for update
                from("direct:UPDATE").to("olingo2://update/Manufacturers('123')");

                // test route for delete
                from("direct:DELETE").to("olingo2://delete/Manufacturers('123')");

                /*
                 * // test route for merge from("direct:MERGE")
                 * .to("olingo2://merge"); // test route for patch
                 * from("direct:PATCH") .to("olingo2://patch");
                 */

                // test route for batch
                from("direct:BATCH").to("olingo2://batch");

                from("direct:read-people-nofilterseen").to("olingo2://read/Manufacturers").to("mock:producer-noalreadyseen");

                from("direct:read-people-filterseen").to("olingo2://read/Manufacturers?filterAlreadySeen=true").to("mock:producer-alreadyseen");
            }
        };
    }
}
