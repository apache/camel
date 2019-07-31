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
package org.apache.camel.component.google.drive;

import java.util.List;

import com.google.api.services.drive.model.Change;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveChangesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for com.google.api.services.drive.Drive$Changes APIs.
 */
public class DriveChangesIntegrationTest extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveChangesIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleDriveApiCollection.getCollection().getApiName(DriveChangesApiMethod.class).getName();

    @Test
    public void testGet() throws Exception {
        final com.google.api.services.drive.model.ChangeList list = requestBody("direct://LIST", null);
        List<Change> items = list.getItems();
        if (!items.isEmpty()) {
            Change change = items.get(0);
            Long id = change.getId();
    
            // using String message body for single parameter "changeId"
            final com.google.api.services.drive.model.Change result = requestBody("direct://GET", id);
    
            assertNotNull("get result", result);
            LOG.debug("get: " + result);
        }
    }

    @Test
    public void testList() throws Exception {
        final com.google.api.services.drive.model.ChangeList result = requestBody("direct://LIST", null);

        assertNotNull("list result", result);
        LOG.debug("list: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for get
                from("direct://GET")
                    .to("google-drive://" + PATH_PREFIX + "/get?inBody=changeId");

                // test route for list
                from("direct://LIST")
                    .to("google-drive://" + PATH_PREFIX + "/list");

                // test route for watch
                from("direct://WATCH")
                    .to("google-drive://" + PATH_PREFIX + "/watch?inBody=contentChannel");

            }
        };
    }
}
