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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.camel.component.google.drive.internal.DriveFilesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.DriveCommentsApiMethod;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;

/**
 * Test class for com.google.api.services.drive.Drive$Comments APIs.
 * TODO Move the file to src/test/java, populate parameter values, and remove @Ignore annotations.
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
public class DriveCommentsIntegrationTest extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveCommentsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleDriveApiCollection.getCollection().getApiName(DriveCommentsApiMethod.class).getName();
    private static final String TEST_UPLOAD_FILE = "src/test/resources/log4j.properties";
    private static final String TEST_UPLOAD_IMG = "src/test/resources/camel-box-small.png";
    private static final java.io.File UPLOAD_FILE = new java.io.File(TEST_UPLOAD_FILE);
    
    // TODO provide parameter values for delete
    @Ignore
    @Test
    public void testDelete() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", null);

        final com.google.api.services.drive.Drive.Comments.Delete result = requestBodyAndHeaders("direct://DELETE", null, headers);

        assertNotNull("delete result", result);
        LOG.debug("delete: " + result);
    }

    // TODO provide parameter values for get
    @Ignore
    @Test
    public void testGet() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", null);

        final com.google.api.services.drive.Drive.Comments.Get result = requestBodyAndHeaders("direct://GET", null, headers);

        assertNotNull("get result", result);
        LOG.debug("get: " + result);
    }

    // TODO provide parameter values for insert
    @Ignore
    @Test
    public void testInsert() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is com.google.api.services.drive.model.Comment
        headers.put("CamelGoogleDrive.content", null);

        final com.google.api.services.drive.Drive.Comments.Insert result = requestBodyAndHeaders("direct://INSERT", null, headers);

        assertNotNull("insert result", result);
        LOG.debug("insert: " + result);
    }

    private File uploadTestFile() {
        File fileMetadata = new File();
        fileMetadata.setTitle(UPLOAD_FILE.getName());
        FileContent mediaContent = new FileContent(null, UPLOAD_FILE);
        
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", fileMetadata);
        // parameter type is com.google.api.client.http.AbstractInputStreamContent
        headers.put("CamelGoogleDrive.mediaContent", mediaContent);

        File result = requestBodyAndHeaders("direct://INSERT_1", null, headers);
        return result;
    }
    
    @Test
    public void testList() throws Exception {
        File testFile = uploadTestFile();
        String fileId = testFile.getId();
        
        // using String message body for single parameter "fileId"
        final com.google.api.services.drive.model.CommentList result = requestBody("direct://LIST", fileId);

        assertNotNull("list result", result);
        LOG.debug("list: " + result);
    }

    // TODO provide parameter values for patch
    @Ignore
    @Test
    public void testPatch() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", null);
        // parameter type is com.google.api.services.drive.model.Comment
        headers.put("CamelGoogleDrive.content", null);

        final com.google.api.services.drive.Drive.Comments.Patch result = requestBodyAndHeaders("direct://PATCH", null, headers);

        assertNotNull("patch result", result);
        LOG.debug("patch: " + result);
    }

    // TODO provide parameter values for update
    @Ignore
    @Test
    public void testUpdate() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", null);
        // parameter type is com.google.api.services.drive.model.Comment
        headers.put("CamelGoogleDrive.content", null);

        final com.google.api.services.drive.Drive.Comments.Update result = requestBodyAndHeaders("direct://UPDATE", null, headers);

        assertNotNull("update result", result);
        LOG.debug("update: " + result);
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
                  .to("google-drive://" + PATH_PREFIX + "/list?inBody=fileId");

                // test route for patch
                from("direct://PATCH")
                  .to("google-drive://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE")
                  .to("google-drive://" + PATH_PREFIX + "/update");
                
                // just used to upload file for test
                from("direct://INSERT_1")
                  .to("google-drive://" + GoogleDriveApiCollection.getCollection().getApiName(DriveFilesApiMethod.class).getName() + "/insert");

            }
        };
    }
}
