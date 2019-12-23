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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.olingo2.api.Olingo2App;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.camel.component.olingo2.api.impl.SystemQueryOption;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmEntitySetInfo;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderReadProperties;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.apache.olingo.odata2.api.servicedocument.Collection;
import org.apache.olingo.odata2.api.servicedocument.ServiceDocument;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Integration test for
 * {@link org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl} using the
 * sample Olingo2 Server dynamically downloaded and started during the test.
 */
public class Olingo2AppAPITest extends AbstractOlingo2AppAPITestSupport {

    private static Olingo2App olingoApp;
    private static Edm edm;
    private static Map<String, EdmEntitySet> edmEntitySetMap;

    private static Olingo2SampleServer server;

    @BeforeClass
    public static void beforeClass() throws Exception {
        startServers(PORT);
        Olingo2SampleServer.generateSampleData(TEST_SERVICE_URL);
        setupClient();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (olingoApp != null) {
            olingoApp.close();
        }
        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    protected static void startServers(int port) throws Exception {
        server = new Olingo2SampleServer(port, "/olingo2_ref");
        server.start();
    }

    protected static void setupClient() throws Exception {
        olingoApp = new Olingo2AppImpl(TEST_SERVICE_URL + "/");
        olingoApp.setContentType(TEST_FORMAT_STRING);

        LOG.info("Read Edm ");
        final TestOlingo2ResponseHandler<Edm> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(null, Olingo2AppImpl.METADATA, null, null, responseHandler);

        edm = responseHandler.await();
        LOG.info("Read default EntityContainer:  {}", responseHandler.await().getDefaultEntityContainer().getName());

        edmEntitySetMap = new HashMap<>();
        for (EdmEntitySet ees : edm.getEntitySets()) {
            edmEntitySetMap.put(ees.getName(), ees);
        }

        // wait for generated data to be registered in server
        Thread.sleep(2000);
    }

    @Test
    public void testServiceDocument() throws Exception {
        final TestOlingo2ResponseHandler<ServiceDocument> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(null, "", null, null, responseHandler);

        final ServiceDocument serviceDocument = responseHandler.await();
        final List<Collection> collections = serviceDocument.getAtomInfo().getWorkspaces().get(0).getCollections();
        assertEquals("Service Atom Collections", 3, collections.size());
        LOG.info("Service Atom Collections:  {}", collections);

        final List<EdmEntitySetInfo> entitySetsInfo = serviceDocument.getEntitySetsInfo();
        assertEquals("Service Entity Sets", 3, entitySetsInfo.size());
        LOG.info("Service Document Entries:  {}", entitySetsInfo);
    }

    @Test
    public void testReadFeed() throws Exception {
        final TestOlingo2ResponseHandler<ODataFeed> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, MANUFACTURERS, null, null, responseHandler);

        final ODataFeed dataFeed = responseHandler.await();
        assertNotNull("Data feed", dataFeed);
        LOG.info("Entries:  {}", prettyPrint(dataFeed));
    }

    @Test
    public void testReadUnparsedFeed() throws Exception {
        final TestOlingo2ResponseHandler<InputStream> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.uread(edm, MANUFACTURERS, null, null, responseHandler);

        final InputStream rawfeed = responseHandler.await();
        assertNotNull("Data feed", rawfeed);
        // for this test, we just let EP to verify the stream data
        final ODataFeed dataFeed = EntityProvider.readFeed(TEST_FORMAT_STRING, edmEntitySetMap.get(MANUFACTURERS), rawfeed, EntityProviderReadProperties.init().build());
        LOG.info("Entries:  {}", prettyPrint(dataFeed));
    }

    @Test
    public void testReadEntry() throws Exception {
        final TestOlingo2ResponseHandler<ODataEntry> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, TEST_MANUFACTURER, null, null, responseHandler);
        ODataEntry entry = responseHandler.await();
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();

        olingoApp.read(edm, TEST_CAR, null, null, responseHandler);
        entry = responseHandler.await();
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);

        olingoApp.read(edm, TEST_MANUFACTURER, queryParams, null, responseHandler);

        ODataEntry entryExpanded = responseHandler.await();
        LOG.info("Single Entry with expanded Cars relation:  {}", prettyPrint(entryExpanded));
    }

    @Test
    public void testReadUnparsedEntry() throws Exception {
        final TestOlingo2ResponseHandler<InputStream> responseHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.uread(edm, TEST_MANUFACTURER, null, null, responseHandler);
        InputStream rawentry = responseHandler.await();
        ODataEntry entry = EntityProvider.readEntry(TEST_FORMAT_STRING, edmEntitySetMap.get(MANUFACTURERS), rawentry, EntityProviderReadProperties.init().build());
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();

        olingoApp.uread(edm, TEST_CAR, null, null, responseHandler);
        rawentry = responseHandler.await();
        entry = EntityProvider.readEntry(TEST_FORMAT_STRING, edmEntitySetMap.get(CARS), rawentry, EntityProviderReadProperties.init().build());
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);

        olingoApp.uread(edm, TEST_MANUFACTURER, queryParams, null, responseHandler);

        rawentry = responseHandler.await();
        ODataEntry entryExpanded = EntityProvider.readEntry(TEST_FORMAT_STRING, edmEntitySetMap.get(MANUFACTURERS), rawentry, EntityProviderReadProperties.init().build());
        LOG.info("Single Entry with expanded Cars relation:  {}", prettyPrint(entryExpanded));
    }

    @Test
    public void testReadUpdateProperties() throws Exception {
        // test simple property Manufacturer.Founded
        final TestOlingo2ResponseHandler<Map<String, Object>> propertyHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, null, null, propertyHandler);

        Calendar founded = (Calendar)propertyHandler.await().get(FOUNDED_PROPERTY);
        LOG.info("Founded property {}", founded);

        final TestOlingo2ResponseHandler<Calendar> valueHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, TEST_MANUFACTURER_FOUNDED_VALUE, null, null, valueHandler);

        founded = valueHandler.await();
        LOG.info("Founded property {}", founded);

        final TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new TestOlingo2ResponseHandler<>();
        final HashMap<String, Object> properties = new HashMap<>();
        properties.put(FOUNDED_PROPERTY, new Date());

//        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, properties, statusHandler);
        // requires a plain Date for XML
        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, null, new Date(), statusHandler);

        LOG.info("Founded property updated with status {}", statusHandler.await().getStatusCode());

        statusHandler.reset();

        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_VALUE, null, new Date(), statusHandler);

        LOG.info("Founded property updated with status {}", statusHandler.await().getStatusCode());

        // test complex property Manufacturer.Address
        propertyHandler.reset();

        olingoApp.read(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, null, null, propertyHandler);

        final Map<String, Object> address = propertyHandler.await();
        LOG.info("Address property {}", prettyPrint(address, 0));

        statusHandler.reset();

        address.clear();
        // Olingo2 sample server MERGE/PATCH behaves like PUT!!!
//        address.put("Street", "Main Street");
        address.put("Street", "Star Street 137");
        address.put("City", "Stuttgart");
        address.put("ZipCode", "70173");
        address.put("Country", "Germany");

//        olingoApp.patch(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, address, statusHandler);
        olingoApp.merge(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, null, address, statusHandler);

        LOG.info("Address property updated with status {}", statusHandler.await().getStatusCode());
    }

    @Test
    public void testReadDeleteCreateLinks() throws Exception {
        final TestOlingo2ResponseHandler<List<String>> linksHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, TEST_MANUFACTURER_LINKS_CARS, null, null, linksHandler);

        final List<String> links = linksHandler.await();
        assertFalse(links.isEmpty());
        LOG.info("Read links: {}", links);

        final TestOlingo2ResponseHandler<String> linkHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, TEST_CAR_LINK_MANUFACTURER, null, null, linkHandler);

        final String link = linkHandler.await();
        LOG.info("Read link: {}", link);

//Deleting relationships through links is not supported in Olingo2 at the time of writing this test
        /*
         * final TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new
         * TestOlingo2ResponseHandler<HttpStatusCodes>(); final
         * ArrayList<Map<String, Object>> carKeys = new ArrayList<Map<String,
         * Object>>(); for (String carLink : links) { final Matcher matcher =
         * LINK_PATTERN.matcher(carLink); assertTrue("Link pattern " + carLink,
         * matcher.matches()); final String carId = matcher.group(1); final
         * HashMap<String, Object> keys = new HashMap<String, Object>();
         * keys.put(ID_PROPERTY, carId); carKeys.add(keys); // delete
         * manufacturer->car link statusHandler.reset(); final String
         * resourcePath = TEST_MANUFACTURER_LINKS_CARS + "('" + carId + "')";
         * olingoApp.delete(resourcePath, statusHandler);
         * assertEquals("Delete car link " + resourcePath,
         * HttpStatusCodes.OK.getStatusCode(),
         * statusHandler.await().getStatusCode()); } // add links to all Cars
         * statusHandler.reset(); olingoApp.create(edm,
         * TEST_MANUFACTURER_LINKS_CARS, carKeys, statusHandler);
         * assertEquals("Links update",
         * HttpStatusCodes.ACCEPTED.getStatusCode(),
         * statusHandler.await().getStatusCode()); // delete car->manufacturer
         * link statusHandler.reset();
         * olingoApp.delete(TEST_CAR_LINK_MANUFACTURER, statusHandler);
         * assertEquals("Delete manufacturer link " +
         * TEST_CAR_LINK_MANUFACTURER, HttpStatusCodes.OK.getStatusCode(),
         * statusHandler.await().getStatusCode()); // add link to Manufacturer
         * statusHandler.reset(); final HashMap<String, Object> manufacturerKey
         * = new HashMap<String, Object>(); manufacturerKey.put(ID_PROPERTY,
         * "1"); olingoApp.create(edm, TEST_CAR_LINK_MANUFACTURER,
         * manufacturerKey, statusHandler); assertEquals("Link update",
         * HttpStatusCodes.ACCEPTED.getStatusCode(),
         * statusHandler.await().getStatusCode());
         */
    }

    @Test
    public void testReadCount() throws Exception {
        final TestOlingo2ResponseHandler<Long> countHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.read(edm, MANUFACTURERS + COUNT_OPTION, null, null, countHandler);

        LOG.info("Manufacturers count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_MANUFACTURER + COUNT_OPTION, null, null, countHandler);

        LOG.info("Manufacturer count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_MANUFACTURER_LINKS_CARS + COUNT_OPTION, null, null, countHandler);

        LOG.info("Manufacturers links count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_CAR_LINK_MANUFACTURER + COUNT_OPTION, null, null, countHandler);

        LOG.info("Manufacturer link count: {}", countHandler.await());
    }

    @Test
    public void testCreateUpdateDeleteEntry() throws Exception {

        // create entry to update
        final TestOlingo2ResponseHandler<ODataEntry> entryHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.create(edm, MANUFACTURERS, null, getEntityData(), entryHandler);

        ODataEntry createdEntry = entryHandler.await();
        LOG.info("Created Entry:  {}", prettyPrint(createdEntry));

        Map<String, Object> data = getEntityData();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>)data.get(ADDRESS);

        data.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");
        final TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new TestOlingo2ResponseHandler<>();

        olingoApp.update(edm, TEST_CREATE_MANUFACTURER, null, data, statusHandler);
        statusHandler.await();

        statusHandler.reset();
        data.put("Name", "MyCarManufacturer Patched");
        olingoApp.patch(edm, TEST_CREATE_MANUFACTURER, null, data, statusHandler);
        statusHandler.await();

        entryHandler.reset();
        olingoApp.read(edm, TEST_CREATE_MANUFACTURER, null, null, entryHandler);

        ODataEntry updatedEntry = entryHandler.await();
        LOG.info("Updated Entry successfully:  {}", prettyPrint(updatedEntry));

        statusHandler.reset();
        olingoApp.delete(TEST_CREATE_MANUFACTURER, null, statusHandler);

        HttpStatusCodes statusCode = statusHandler.await();
        LOG.info("Deletion of Entry was successful:  {}: {}", statusCode.getStatusCode(), statusCode.getInfo());

        try {
            LOG.info("Verify Delete Entry");

            entryHandler.reset();
            olingoApp.read(edm, TEST_CREATE_MANUFACTURER, null, null, entryHandler);

            entryHandler.await();
            fail("Entry not deleted!");
        } catch (Exception e) {
            LOG.info("Deleted entry not found: {}", e.getMessage());
        }
    }

    @Test
    public void testBatchRequest() throws Exception {

        final List<Olingo2BatchRequest> batchParts = new ArrayList<>();

        // Edm query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(Olingo2AppImpl.METADATA).build());

        // feed query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(MANUFACTURERS).build());

        // read
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).build());

        // read with expand
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).queryParams(queryParams).build());

        // create
        final Map<String, Object> data = getEntityData();
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(MANUFACTURERS).contentId(TEST_RESOURCE_CONTENT_ID).operation(Operation.CREATE).body(data).build());

        // update
        final Map<String, Object> updateData = new HashMap<>(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>)updateData.get(ADDRESS);
        updateData.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.UPDATE).body(updateData).build());

        // delete
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.DELETE).build());

        final TestOlingo2ResponseHandler<List<Olingo2BatchResponse>> responseHandler = new TestOlingo2ResponseHandler<>();

        // read to verify delete
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_CREATE_MANUFACTURER).build());

        olingoApp.batch(edm, null, batchParts, responseHandler);

        final List<Olingo2BatchResponse> responseParts = responseHandler.await(15, TimeUnit.MINUTES);
        assertEquals("Batch responses expected", 8, responseParts.size());

        assertNotNull(responseParts.get(0).getBody());
        final ODataFeed feed = (ODataFeed)responseParts.get(1).getBody();
        assertNotNull(feed);
        LOG.info("Batch feed:  {}", prettyPrint(feed));

        ODataEntry dataEntry = (ODataEntry)responseParts.get(2).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch read entry:  {}", prettyPrint(dataEntry));

        dataEntry = (ODataEntry)responseParts.get(3).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch read entry with expand:  {}", prettyPrint(dataEntry));

        dataEntry = (ODataEntry)responseParts.get(4).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch create entry:  {}", prettyPrint(dataEntry));

        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(5).getStatusCode());
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(6).getStatusCode());

        assertEquals(HttpStatusCodes.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
        final Exception exception = (Exception)responseParts.get(7).getBody();
        assertNotNull(exception);
        LOG.info("Batch retrieve deleted entry:  {}", exception);
    }
}
