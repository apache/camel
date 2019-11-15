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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.entity.ContentType;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataDeltaFeed;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// CHECKSTYLE:OFF
public class AbstractOlingo2AppAPITestSupport {

    protected static final String SERVICE_NAME = "MyFormula.svc";
    protected static final Logger LOG = LoggerFactory.getLogger(Olingo2AppAPITest.class);
    protected static final int PORT = AvailablePortFinder.getNextAvailable();
    protected static final long TIMEOUT = 100000;
    protected static final String MANUFACTURERS = "Manufacturers";
    protected static final String FQN_MANUFACTURERS = "DefaultContainer.Manufacturers";
    protected static final String ADDRESS = "Address";
    protected static final String CARS = "Cars";
    protected static final String TEST_KEY = "'1'";
    protected static final String TEST_CREATE_KEY = "'123'";
    protected static final String TEST_MANUFACTURER = FQN_MANUFACTURERS + "(" + TEST_KEY + ")";
    protected static final String TEST_CREATE_MANUFACTURER = MANUFACTURERS + "(" + TEST_CREATE_KEY + ")";
    protected static final String TEST_RESOURCE_CONTENT_ID = "1";
    protected static final String TEST_RESOURCE = "$" + TEST_RESOURCE_CONTENT_ID;
    protected static final char NEW_LINE = '\n';
    protected static final String TEST_CAR = "Manufacturers('1')/Cars('1')";
    protected static final String TEST_MANUFACTURER_FOUNDED_PROPERTY = "Manufacturers('1')/Founded";
    protected static final String TEST_MANUFACTURER_FOUNDED_VALUE = "Manufacturers('1')/Founded/$value";
    protected static final String FOUNDED_PROPERTY = "Founded";
    protected static final String TEST_MANUFACTURER_ADDRESS_PROPERTY = "Manufacturers('1')/Address";
    protected static final String TEST_MANUFACTURER_LINKS_CARS = "Manufacturers('1')/$links/Cars";
    protected static final String TEST_CAR_LINK_MANUFACTURER = "Cars('1')/$links/Manufacturer";
    protected static final String COUNT_OPTION = "/$count";
    protected static final String TEST_SERVICE_URL = "http://localhost:" + PORT + "/" + SERVICE_NAME;
    protected static final ContentType TEST_FORMAT = ContentType.APPLICATION_JSON;
    protected static final String TEST_FORMAT_STRING = TEST_FORMAT.toString();
    protected static final String ID_PROPERTY = "Id";

    protected static Map<String, Object> getEntityData() {
        Map<String, Object> data = new HashMap<>();
        data.put(ID_PROPERTY, "123");
        data.put("Name", "MyCarManufacturer");
        data.put(FOUNDED_PROPERTY, new Date());
        Map<String, Object> address = new HashMap<>();
        address.put("Street", "Main");
        address.put("ZipCode", "42421");
        address.put("City", "Fairy City");
        address.put("Country", "FarFarAway");
        data.put(ADDRESS, address);
        return data;
    }

    protected static void indent(StringBuilder builder, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            builder.append("  ");
        }
    }

    protected static String prettyPrint(ODataFeed dataFeed) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (ODataEntry entry : dataFeed.getEntries()) {
            builder.append(prettyPrint(entry.getProperties(), 1)).append('\n');
        }
        builder.append("]\n");
        return builder.toString();
    }

    protected static String prettyPrint(ODataEntry createdEntry) {
        return prettyPrint(createdEntry.getProperties(), 0);
    }

    protected static String prettyPrint(Map<String, Object> properties, int level) {
        StringBuilder b = new StringBuilder();
        Set<Map.Entry<String, Object>> entries = properties.entrySet();

        for (Map.Entry<String, Object> entry : entries) {
            indent(b, level);
            b.append(entry.getKey()).append(": ");
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> objectMap = (Map<String, Object>)value;
                value = prettyPrint(objectMap, level + 1);
                b.append(value).append(NEW_LINE);
            } else if (value instanceof Calendar) {
                Calendar cal = (Calendar)value;
                value = DateFormat.getInstance().format(cal.getTime());
                b.append(value).append(NEW_LINE);
            } else if (value instanceof ODataDeltaFeed) {
                ODataDeltaFeed feed = (ODataDeltaFeed)value;
                List<ODataEntry> inlineEntries = feed.getEntries();
                b.append("{");
                for (ODataEntry oDataEntry : inlineEntries) {
                    value = prettyPrint(oDataEntry.getProperties(), level + 1);
                    b.append("\n[\n").append(value).append("\n],");
                }
                b.deleteCharAt(b.length() - 1);
                indent(b, level);
                b.append("}\n");
            } else {
                b.append(value).append(NEW_LINE);
            }
        }
        // remove last line break
        b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    protected static final class TestOlingo2ResponseHandler<T> implements Olingo2ResponseHandler<T> {

        private T response;
        private Exception error;
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onResponse(T response, Map<String, String> responseHeaders) {
            this.response = response;
            if (LOG.isDebugEnabled()) {
                if (response instanceof ODataFeed) {
                    LOG.debug("Received response: {}", prettyPrint((ODataFeed)response));
                } else if (response instanceof ODataEntry) {
                    LOG.debug("Received response: {}", prettyPrint((ODataEntry)response));
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
