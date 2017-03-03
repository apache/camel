/**
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
package org.apache.camel.component.box2;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2SearchManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.box2.api.Box2SearchManager}
 * APIs.
 */
public class Box2SearchManagerIntegrationTest extends AbstractBox2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Box2SearchManagerIntegrationTest.class);
    private static final String PATH_PREFIX = Box2ApiCollection.getCollection()
            .getApiName(Box2SearchManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";

    private BoxFile testFile;

    @Test
    public void testSearchFolder() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", "0");
        // parameter type is String
        headers.put("CamelBox2.query", CAMEL_TEST_FILE_NAME);

        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBodyAndHeaders("direct://SEARCHFOLDER", null, headers);

        assertNotNull("searchFolder result", result);
        assertEquals("searchFolder file found", 1, result.size());
        LOG.debug("searchFolder: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for searchFolder
                from("direct://SEARCHFOLDER").to("box2://" + PATH_PREFIX + "/searchFolder");

            }
        };
    }

    @Before
    public void setupTest() throws Exception {
        createTestFile();
    }

    @After
    public void teardownTest() {
        deleteTestFile();
    }

    public BoxAPIConnection getConnection() {
        Box2Endpoint endpoint = (Box2Endpoint) context().getEndpoint("box2://" + PATH_PREFIX + "/searchFolder");
        return endpoint.getBoxConnection();
    }

    private void createTestFile() throws FileNotFoundException {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }

    private void deleteTestFile() {
        try {
            testFile.delete();
        } catch (Throwable t) {
        }
        testFile = null;
    }
}
