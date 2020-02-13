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
import java.util.Map;

import javax.ws.rs.HttpMethod;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.camel.component.olingo2.api.Olingo2App;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.commons.ODataHttpHeaders;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntityContainer;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmEntityType;
import org.apache.olingo.odata2.api.edm.EdmProperty;
import org.apache.olingo.odata2.api.edm.EdmServiceMetadata;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;
import org.apache.olingo.odata2.api.processor.ODataResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests support for concurrency properties which generate and require reading
 * eTags before patch, update and delete operations. Since the embedded olingo2
 * odata service does not contain any concurrency properties, it is necessary to
 * mock up a new server. Uses a cutdown version of the reference odata service
 * and adds in extra concurrency properties. Service's dispatcher only tests the
 * correct calls are made and whether the eTags are correctly added as headers
 * to the requisite requests.
 */
public class Olingo2AppAPIETagEnabledTest extends AbstractOlingo2AppAPITestSupport {

    private static MockWebServer server;
    private static Olingo2App olingoApp;
    private static Edm edm;
    private static EdmEntitySet manufacturersSet;

    @BeforeClass
    public static void scaffold() throws Exception {
        initEdm();
        initServer();
    }

    @AfterClass
    public static void unscaffold() throws Exception {
        if (olingoApp != null) {
            olingoApp.close();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    private static void initEdm() throws Exception {
        InputStream edmXml = Olingo2AppAPIETagEnabledTest.class.getResourceAsStream("etag-enabled-service.xml");
        edm = EntityProvider.readMetadata(edmXml, true);
        assertNotNull(edm);

        EdmEntityContainer entityContainer = edm.getDefaultEntityContainer();
        assertNotNull(entityContainer);
        manufacturersSet = entityContainer.getEntitySet(MANUFACTURERS);
        assertNotNull(manufacturersSet);

        EdmEntityType entityType = manufacturersSet.getEntityType();
        assertNotNull(entityType);

        //
        // Check we have enabled eTag properties
        //
        EdmProperty property = (EdmProperty)entityType.getProperty("Id");
        assertNotNull(property.getFacets());
    }

    private static void initServer() throws Exception {
        server = new MockWebServer();
        //
        // Init dispatcher prior to start of server
        //
        server.setDispatcher(new Dispatcher() {

            @SuppressWarnings("resource")
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
                MockResponse mockResponse = new MockResponse();

                switch (recordedRequest.getMethod()) {
                    case HttpMethod.GET:
                        try {
                            if (recordedRequest.getPath().endsWith("/" + TEST_CREATE_MANUFACTURER)) {

                                ODataResponse odataResponse = EntityProvider.writeEntry(TEST_FORMAT.getMimeType(), manufacturersSet, getEntityData(),
                                        EntityProviderWriteProperties.serviceRoot(getServiceUrl().uri()).build());
                                InputStream entityStream = odataResponse.getEntityAsStream();
                                mockResponse.setResponseCode(HttpStatusCodes.OK.getStatusCode());
                                mockResponse.setBody(new Buffer().readFrom(entityStream));
                                return mockResponse;

                            } else if (recordedRequest.getPath().endsWith("/" + Olingo2AppImpl.METADATA)) {

                                EdmServiceMetadata serviceMetadata = edm.getServiceMetadata();
                                return mockResponse.setResponseCode(HttpStatusCodes.OK.getStatusCode())
                                        .addHeader(ODataHttpHeaders.DATASERVICEVERSION, serviceMetadata.getDataServiceVersion())
                                        .setBody(new Buffer().readFrom(serviceMetadata.getMetadata()));
                            }

                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        break;
                    case HttpMethod.PATCH:
                    case HttpMethod.PUT:
                    case HttpMethod.POST:
                    case HttpMethod.DELETE:
                        //
                        // Objective of the test:
                        // The Read has to have been called by
                        // Olingo2AppImpl.argumentWithETag
                        // which should then populate the IF-MATCH header with the
                        // eTag value.
                        // Verify the eTag value is present.
                        //
                        assertNotNull(recordedRequest.getHeader(HttpHeader.IF_MATCH.asString()));

                        return mockResponse.setResponseCode(HttpStatusCodes.NO_CONTENT.getStatusCode());
                    default:
                        break;
                }

                mockResponse.setResponseCode(HttpStatusCodes.NOT_FOUND.getStatusCode()).setBody("{ status: \"Not Found\"}");
                return mockResponse;
            }
        });
        server.start();

        //
        // have to init olingoApp after start of server
        // since getBaseUrl() will call server start
        //
        olingoApp = new Olingo2AppImpl(getServiceUrl() + "/");
        olingoApp.setContentType(TEST_FORMAT_STRING);
    }

    private static HttpUrl getServiceUrl() {
        if (server == null) {
            fail("Test programming failure. Server not initialised");
        }

        return server.url(SERVICE_NAME);
    }

    @Test
    public void testPatchEntityWithETag() throws Exception {
        TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new TestOlingo2ResponseHandler<>();

        Map<String, Object> data = getEntityData();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>)data.get(ADDRESS);

        data.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        //
        // Call patch
        //
        olingoApp.patch(edm, TEST_CREATE_MANUFACTURER, null, data, statusHandler);

        HttpStatusCodes statusCode = statusHandler.await();
        assertEquals(HttpStatusCodes.NO_CONTENT, statusCode);
    }

    @Test
    public void testUpdateEntityWithETag() throws Exception {
        TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new TestOlingo2ResponseHandler<>();

        Map<String, Object> data = getEntityData();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>)data.get(ADDRESS);

        data.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        //
        // Call update
        //
        olingoApp.update(edm, TEST_CREATE_MANUFACTURER, null, data, statusHandler);

        HttpStatusCodes statusCode = statusHandler.await();
        assertEquals(HttpStatusCodes.NO_CONTENT, statusCode);
    }

    @Test
    public void testDeleteEntityWithETag() throws Exception {
        TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler = new TestOlingo2ResponseHandler<>();

        Map<String, Object> data = getEntityData();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>)data.get(ADDRESS);

        data.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        //
        // Call delete
        //
        olingoApp.delete(TEST_CREATE_MANUFACTURER, null, statusHandler);

        HttpStatusCodes statusCode = statusHandler.await();
        assertEquals(HttpStatusCodes.NO_CONTENT, statusCode);
    }
}
