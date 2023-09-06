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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.http.FileContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveFilesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test class for com.google.api.services.drive.Drive$Files APIs.
 */
@EnabledIf(value = "org.apache.camel.component.google.drive.AbstractGoogleDriveTestSupport#hasCredentials",
           disabledReason = "Google Drive credentials were not provided")
public class DriveFilesIT extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveFilesIT.class);
    private static final String PATH_PREFIX
            = GoogleDriveApiCollection.getCollection().getApiName(DriveFilesApiMethod.class).getName();

    @Test
    public void testCopy() {
        File testFile = uploadTestFile();
        String fromFileId = testFile.getId();

        File toFile = new File();
        toFile.setName(UPLOAD_FILE.getName() + "_copy");

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fromFileId);
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", toFile);

        final File result = requestBodyAndHeaders("direct://COPY", null, headers);

        assertNotNull(result, "copy result");
        assertEquals(toFile.getName(), result.getName());
        LOG.debug("copy: {}", result);
    }

    @Test
    public void testDelete() {
        File testFile = uploadTestFile();
        String fileId = testFile.getId();

        // using String message body for single parameter "fileId"
        sendBody("direct://DELETE", fileId);

        try {
            // the file should be gone now
            final File result = requestBody("direct://GET", fileId);
            fail("Should have not found deleted file.");
        } catch (Exception e) {
            // Likely safe to ignore in this context
            LOG.debug("Unhandled exception (probably safe to ignore): {}", e.getMessage(), e);
        }
    }

    @Test
    public void testGet() {
        File testFile = uploadTestFile();
        String fileId = testFile.getId();

        // using String message body for single parameter "fileId"
        final File result = requestBody("direct://GET", fileId);

        assertNotNull(result, "get result");
        LOG.debug("get: {}", result);
    }

    @Test
    public void testInsert() {
        File file = new File();
        file.setName(UPLOAD_FILE.getName());
        // using com.google.api.services.drive.model.File message body for single parameter "content"
        File result = requestBody("direct://INSERT", file);
        assertNotNull(result, "insert result");
        LOG.debug("insert: {}", result);
    }

    @Test
    public void testInsert1() {
        File result = uploadTestFile();

        assertNotNull(result, "insert result");
        LOG.debug("insert: {}", result);
    }

    @Test
    public void testList() {
        // upload a test file
        File testFile = uploadTestFile();

        FileList result = requestBody("direct://LIST", null);
        assertNotNull(result, "list result");
        assertTrue(result.getFiles().size() >= 1);

        File testFile2 = uploadTestFile();

        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelGoogleDrive.maxResults", 1);

        result = requestBodyAndHeaders("direct://LIST", null, headers);
        assertNotNull(result, "list result");
        assertEquals(1, result.getFiles().size());

        // test paging the list
        List<File> resultList = new ArrayList<>();
        String pageToken;
        int i = 0;
        do {
            result = requestBodyAndHeaders("direct://LIST", null, headers);

            resultList.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
            headers.put("CamelGoogleDrive.pageToken", pageToken);

            i++;
        } while (pageToken != null && pageToken.length() > 0 && i < 2);

        // we should have 2 files in result (one file for each of the 2 pages)
        assertEquals(2, resultList.size());
        // they should be different files
        assertNotEquals(resultList.get(1).getId(), resultList.get(0).getId());
    }

    @Test
    public void testPatch() {
        File file = uploadTestFile();

        // lets update the filename
        file.setName(UPLOAD_FILE.getName() + "PATCHED");

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", file.getId());
        // parameter type is String
        headers.put("CamelGoogleDrive.fields", "title");
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", file);

        File result = requestBodyAndHeaders("direct://PATCH", null, headers);

        assertNotNull(result, "patch result");
        assertEquals(UPLOAD_FILE.getName() + "PATCHED", result.getName());
        LOG.debug("patch: {}", result);
    }

    @Test
    public void testTouch() {
        File theTestFile = uploadTestFile();
        DateTime createdDate = theTestFile.getModifiedByMeTime();
        // using String message body for single parameter "fileId"
        File result = requestBody("direct://TOUCH", theTestFile.getId());

        assertNotNull(result, "touch result");
        assertTrue(result.getModifiedByMeTime().getValue() > createdDate.getValue());
    }

    @Test
    public void testTrash() {
        File testFile = uploadTestFile();
        String fileId = testFile.getId();

        assertNotNull(requestBody("direct://TRASH", fileId), "trash result");
        assertNotNull(requestBody("direct://UNTRASH", fileId), "untrash result");

    }

    @Test
    public void testUpdate() {
        File theTestFile = uploadTestFile();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", theTestFile.getId());
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", theTestFile);

        File result = requestBodyAndHeaders("direct://UPDATE", null, headers);

        assertNotNull(result, "update result");
        LOG.debug("update: {}", result);
    }

    @Test
    public void testUpdate1() {

        // First retrieve the file from the API.
        File testFile = uploadTestFile();
        String fileId = testFile.getId();

        // using String message body for single parameter "fileId"
        final File file = requestBody("direct://GET", fileId);

        // File's new metadata.
        file.setName("camel.png");

        // File's new content.
        java.io.File fileContent = new java.io.File(TEST_UPLOAD_IMG);
        FileContent mediaContent = new FileContent(null, fileContent);

        // Send the request to the API.

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fileId);
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", file);
        // parameter type is com.google.api.client.http.AbstractInputStreamContent
        headers.put("CamelGoogleDrive.mediaContent", mediaContent);

        File result = requestBodyAndHeaders("direct://UPDATE_1", null, headers);

        assertNotNull(result, "update result");
        LOG.debug("update: {}", result);
    }

    // TODO provide parameter values for watch
    @Disabled
    @Test
    public void testWatch() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", null);
        // parameter type is com.google.api.services.drive.model.Channel
        headers.put("CamelGoogleDrive.contentChannel", null);

        final com.google.api.services.drive.Drive.Files.Watch result = requestBodyAndHeaders("direct://WATCH", null, headers);

        assertNotNull(result, "watch result");
        LOG.debug("watch: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for copy
                from("direct://COPY")
                        .to("google-drive://" + PATH_PREFIX + "/copy");

                // test route for delete
                from("direct://DELETE")
                        .to("google-drive://" + PATH_PREFIX + "/delete?inBody=fileId");

                // test route for get
                from("direct://GET")
                        .to("google-drive://" + PATH_PREFIX + "/get?inBody=fileId");

                // test route for insert
                from("direct://INSERT")
                        .to("google-drive://" + PATH_PREFIX + "/insert?inBody=content");

                // test route for insert
                from("direct://INSERT_1")
                        .to("google-drive://" + PATH_PREFIX + "/insert");

                // test route for list
                from("direct://LIST")
                        .to("google-drive://" + PATH_PREFIX + "/list");

                // test route for patch
                from("direct://PATCH")
                        .to("google-drive://" + PATH_PREFIX + "/patch");

                // test route for touch
                from("direct://TOUCH")
                        .to("google-drive://" + PATH_PREFIX + "/touch?inBody=fileId");

                // test route for trash
                from("direct://TRASH")
                        .to("google-drive://" + PATH_PREFIX + "/trash?inBody=fileId");

                // test route for untrash
                from("direct://UNTRASH")
                        .to("google-drive://" + PATH_PREFIX + "/untrash?inBody=fileId");

                // test route for update
                from("direct://UPDATE")
                        .to("google-drive://" + PATH_PREFIX + "/update");

                // test route for update
                from("direct://UPDATE_1")
                        .to("google-drive://" + PATH_PREFIX + "/update");

                // test route for watch
                from("direct://WATCH")
                        .to("google-drive://" + PATH_PREFIX + "/watch");

            }
        };
    }
}
