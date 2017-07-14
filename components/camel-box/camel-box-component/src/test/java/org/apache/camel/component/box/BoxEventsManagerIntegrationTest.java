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
package org.apache.camel.component.box;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxEventsManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxEventsManagerApiMethod;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link BoxEventsManager}
 * APIs. TODO Move the file to src/test/java, populate parameter values, and
 * remove @Ignore annotations. The class source won't be generated again if the
 * generator MOJO finds it under src/test/java.
 */
public class BoxEventsManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxEventsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxEventsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";

    @Test
    public void testListen() throws Exception {
        try {
            // generate a file create event
            createTestFile();
        } finally {
            // generate a file delete event
            deleteTestFile();
        }

        MockEndpoint mockEndpoint = getMockEndpoint("mock:boxEvents");
        mockEndpoint.expectedMinimumMessageCount(2);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> exchanges = mockEndpoint.getExchanges();
        assertNotNull("poll result", exchanges);
        assertFalse("poll result", exchanges.isEmpty());
        LOG.debug("poll result: " + exchanges);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                // test route for events
                from("box://" + PATH_PREFIX + "/listen?startingPosition=0").to("mock:boxEvents");

            }
        };
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context()
                .getEndpoint("box://" + PATH_PREFIX + "/listen?startingPosition=0");
        return endpoint.getBoxConnection();
    }

    private void createTestFile() throws FileNotFoundException {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }
}
