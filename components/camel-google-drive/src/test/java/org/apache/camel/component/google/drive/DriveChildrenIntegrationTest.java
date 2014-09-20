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
package org.apache.camel.component.google.drive;

import java.util.HashMap;
import java.util.Map;

import com.google.api.services.drive.model.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveChildrenApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.drive.Drive$Children} APIs.
 */
public class DriveChildrenIntegrationTest extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveChildrenIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleDriveApiCollection.getCollection().getApiName(DriveChildrenApiMethod.class).getName();

    @Test
    public void testUploadFileToFolder() throws Exception {
        File folder = uploadTestFolder();        
        File file = uploadTestFile();
        
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.folderId", folder.getId());
        
        com.google.api.services.drive.model.ChildReference child = new com.google.api.services.drive.model.ChildReference();
        child.setId(file.getId());
        // parameter type is com.google.api.services.drive.model.ChildReference
        headers.put("CamelGoogleDrive.content", child);

        requestBodyAndHeaders("direct://INSERT", null, headers);

        final com.google.api.services.drive.model.ChildList result = requestBody("direct://LIST", folder.getId());
        assertNotNull("insert result", result);
        LOG.debug("insert: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for delete
                from("direct://DELETE")
                    .to("google-drive://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET")
                    .to("google-drive://" + PATH_PREFIX + "/get");

                // test route for insert
                from("direct://INSERT")
                    .to("google-drive://" + PATH_PREFIX + "/insert");

                // test route for list
                from("direct://LIST")
                    .to("google-drive://" + PATH_PREFIX + "/list?inBody=folderId");

            }
        };
    }
}
