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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFile.ThumbnailFileType;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxSharedLink;
import com.box.sdk.Metadata;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2FilesManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.box2.api.Box2FilesManager}
 * APIs.
 */
public class Box2FilesManagerIntegrationTest extends AbstractBox2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Box2FilesManagerIntegrationTest.class);
    private static final String PATH_PREFIX = Box2ApiCollection.getCollection()
            .getApiName(Box2FilesManagerApiMethod.class).getName();

    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_DESCRIPTION = "CamelTestFile.txt description";
    private static final String CAMEL_TEST_COPY_FILE_NAME = "CamelTestFile_Copy.txt";
    private static final String CAMEL_TEST_MOVE_FILE_NAME = "CamelTestFile_Move.txt";
    private static final String CAMEL_TEST_RENAME_FILE_NAME = "CamelTestFile_Rename.txt";
    private static final String CAMEL_TEST_UPLOAD_FILE_NAME = "CamelTestFile_Upload.txt";

    private BoxFile testFile;

    @Test
    public void testCopyFile() throws Exception {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox2.destinationFolderId", "0");
            // parameter type is String
            headers.put("CamelBox2.newName", CAMEL_TEST_COPY_FILE_NAME);

            result = requestBodyAndHeaders("direct://COPYFILE", null, headers);

            assertNotNull("copyFile result", result);
            assertEquals("copyFile name", CAMEL_TEST_COPY_FILE_NAME, result.getInfo().getName());
            LOG.debug("copyFile: " + result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Test
    public void testCreateFileMetadata() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("/foo", "bar");

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is com.box.sdk.Metadata
        headers.put("CamelBox2.metadata", metadata);
        // parameter type is String
        headers.put("CamelBox2.typeName", null);

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://CREATEFILEMETADATA", null, headers);

        assertNotNull("createFileMetadata result", result);
        assertEquals("createFileMetadata result", "bar", result.get("/foo"));
        LOG.debug("createFileMetadata: " + result);
    }

    @Test
    public void testCreateFileSharedLink() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxSharedLink.Access
        headers.put("CamelBox2.access", BoxSharedLink.Access.DEFAULT);
        // parameter type is java.util.Date
        headers.put("CamelBox2.unshareDate", null);
        // parameter type is com.box.sdk.BoxSharedLink.Permissions
        headers.put("CamelBox2.permissions", null);

        final com.box.sdk.BoxSharedLink result = requestBodyAndHeaders("direct://CREATEFILESHAREDLINK", null, headers);

        assertNotNull("createFileSharedLink result", result);
        LOG.debug("createFileSharedLink: " + result);
    }

    @Test
    public void testDeleteFile() throws Exception {
        // using String message body for single parameter "fileId"
        requestBody("direct://DELETEFILE", testFile.getID());

        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        Iterable<BoxItem.Info> it = rootFolder.search("^" + CAMEL_TEST_FILE + "$");
        int searchResults = sizeOfIterable(it);
        boolean exists = searchResults > 0 ? true : false;
        assertEquals("deleteFile exists", false, exists);
        LOG.debug("deleteFile: exists? " + exists);

    }

    @Test
    public void testDeleteFileMetadata() throws Exception {
        testFile.createMetadata(new Metadata());

        // using String message body for single parameter "fileId"
        requestBody("direct://DELETEFILEMETADATA", testFile.getID());

        try {
            testFile.getMetadata();
        } catch (BoxAPIException e) {
            if (e.getResponseCode() == 404) {
                // Box API should return a
                return;
            }
        }
        fail("deleteFileMetadata metadata");

    }

    @Ignore // Requires premium user account to test.
    @Test
    public void testDeleteFileVersion() throws Exception {
        testFile.uploadVersion(getClass().getResourceAsStream(CAMEL_TEST_FILE));

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox2.version", 0);

        requestBodyAndHeaders("direct://DELETEFILEVERSION", null, headers);
        boolean onlyOneVersion = testFile.getVersions().size() == 1;
        assertTrue("deleteFileVersion version deleted", onlyOneVersion);
    }

    @Test
    public void testDownloadFile() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is java.io.OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        headers.put("CamelBox2.output", output);
        // parameter type is Long
        headers.put("CamelBox2.rangeStart", null);
        // parameter type is Long
        headers.put("CamelBox2.rangeEnd", null);
        // parameter type is com.box.sdk.ProgressListener
        headers.put("CamelBox2.listener", null);

        final java.io.OutputStream result = requestBodyAndHeaders("direct://DOWNLOADFILE", null, headers);

        assertNotNull("downloadFile result", result);
        LOG.debug("downloadFile: " + result);
    }

    @Ignore // Requires premium user account to test
    @Test
    public void testDownloadPreviousFileVersion() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox2.version", 0);
        // parameter type is java.io.OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        headers.put("CamelBox2.output", output);
        // parameter type is com.box.sdk.ProgressListener
        headers.put("CamelBox2.listener", null);

        final java.io.OutputStream result = requestBodyAndHeaders("direct://DOWNLOADPREVIOUSFILEVERSION", null,
                headers);

        assertNotNull("downloadPreviousFileVersion result", result);
        LOG.debug("downloadPreviousFileVersion: " + result);
    }

    @Test
    public void testGetDownloadURL() throws Exception {
        // using String message body for single parameter "fileId"
        final java.net.URL result = requestBody("direct://GETDOWNLOADURL", testFile.getID());

        assertNotNull("getDownloadURL result", result);
        LOG.debug("getDownloadURL: " + result);
    }

    @Test
    public void testGetFileInfo() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is String[]
        headers.put("CamelBox2.fields", null);

        final com.box.sdk.BoxFile.Info result = requestBodyAndHeaders("direct://GETFILEINFO", null, headers);

        assertNotNull("getFileInfo result", result);
        LOG.debug("getFileInfo: " + result);
    }

    @Test
    public void testGetFileMetadata() throws Exception {
        testFile.createMetadata(new Metadata());

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is String
        headers.put("CamelBox2.typeName", null);

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://GETFILEMETADATA", null, headers);

        assertNotNull("getFileMetadata result", result);
        LOG.debug("getFileMetadata: " + result);
    }

    @Test
    public void testGetFilePreviewLink() throws Exception {
        // using String message body for single parameter "fileId"
        final java.net.URL result = requestBody("direct://GETFILEPREVIEWLINK", testFile.getID());

        assertNotNull("getFilePreviewLink result", result);
        LOG.debug("getFilePreviewLink: " + result);
    }

    @Test
    public void testGetFileThumbnail() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxFile.ThumbnailFileType
        headers.put("CamelBox2.fileType", ThumbnailFileType.JPG);
        // parameter type is Integer
        headers.put("CamelBox2.minWidth", 32);
        // parameter type is Integer
        headers.put("CamelBox2.minHeight", 32);
        // parameter type is Integer
        headers.put("CamelBox2.maxWidth", 32);
        // parameter type is Integer
        headers.put("CamelBox2.maxHeight", 32);

        final byte[] result = requestBodyAndHeaders("direct://GETFILETHUMBNAIL", null, headers);

        assertNotNull("getFileThumbnail result", result);
        LOG.debug("getFileThumbnail: " + result);
    }

    @Test
    public void testGetFileVersions() throws Exception {
        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETFILEVERSIONS", testFile.getID());

        assertNotNull("getFileVersions result", result);
        LOG.debug("getFileVersions: " + result);
    }

    @Test
    public void testMoveFile() throws Exception {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox2.destinationFolderId", "0");
            // parameter type is String
            headers.put("CamelBox2.newName", CAMEL_TEST_MOVE_FILE_NAME);

            result = requestBodyAndHeaders("direct://MOVEFILE", null, headers);

            assertNotNull("moveFile result", result);
            assertEquals("moveFile name", CAMEL_TEST_MOVE_FILE_NAME, result.getInfo().getName());
            LOG.debug("moveFile: " + result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Ignore // Requires premium user account to test
    @Test
    public void testPromoteFileVersion() throws Exception {
        testFile.uploadVersion(getClass().getResourceAsStream(CAMEL_TEST_FILE));

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox2.version", 1);

        final com.box.sdk.BoxFileVersion result = requestBodyAndHeaders("direct://PROMOTEFILEVERSION", null, headers);

        assertNotNull("promoteFileVersion result", result);
        LOG.debug("promoteFileVersion: " + result);
    }

    @Test
    public void testRenameFile() throws Exception {

        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox2.newFileName", CAMEL_TEST_RENAME_FILE_NAME);

            result = requestBodyAndHeaders("direct://RENAMEFILE", null, headers);

            assertNotNull("renameFile result", result);
            assertEquals("renameFile name", CAMEL_TEST_RENAME_FILE_NAME, result.getInfo().getName());
            LOG.debug("renameFile: " + result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Test
    public void testUpdateFileInfo() throws Exception {
        BoxFile.Info info = testFile.getInfo();
        info.setDescription(CAMEL_TEST_FILE_DESCRIPTION);

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxFile.Info
        headers.put("CamelBox2.info", info);

        final com.box.sdk.BoxFile result = requestBodyAndHeaders("direct://UPDATEFILEINFO", null, headers);

        assertNotNull("updateFileInfo result", result);
        assertEquals("updateFileInfo info", CAMEL_TEST_FILE_DESCRIPTION, result.getInfo().getDescription());
        LOG.debug("updateFileInfo: " + result);
    }

    @Test
    public void testUpdateFileMetadata() throws Exception {
        Metadata metadata = new Metadata();
        // metadata.add("/foo", "bar");
        metadata = testFile.createMetadata(metadata);

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.fileId", testFile.getID());
        // parameter type is com.box.sdk.Metadata
        headers.put("CamelBox2.metadata", metadata);

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://UPDATEFILEMETADATA", null, headers);

        assertNotNull("updateFileMetadata result", result);
        LOG.debug("updateFileMetadata: " + result);
    }

    @Ignore
    @Test
    public void testUploadFile() throws Exception {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            headers.put("CamelBox2.parentFolderId", "0");
            headers.put("CamelBox2.content", getClass().getResourceAsStream(CAMEL_TEST_FILE));
            headers.put("CamelBox2.fileName", CAMEL_TEST_UPLOAD_FILE_NAME);
            headers.put("CamelBox2.created", null);
            headers.put("CamelBox2.modified", null);
            headers.put("CamelBox2.size", null);
            headers.put("CamelBox2.listener", null);

            result = requestBodyAndHeaders("direct://UPLOADFILE", null, headers);

            assertNotNull("uploadFile result", result);
            LOG.debug("uploadFile: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testUploadNewFileVersion() throws Exception {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.fileId", testFile.getID());
            // parameter type is java.io.InputStream
            headers.put("CamelBox2.fileContent", getClass().getResourceAsStream(CAMEL_TEST_FILE));
            // parameter type is java.util.Date
            headers.put("CamelBox2.modified", null);
            // parameter type is Long
            headers.put("CamelBox2.fileSize", null);
            // parameter type is com.box.sdk.ProgressListener
            headers.put("CamelBox2.listener", null);

            result = requestBodyAndHeaders("direct://UPLOADNEWFILEVERSION", null, headers);

            assertNotNull("uploadNewFileVersion result", result);
            LOG.debug("uploadNewFileVersion: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Throwable t) {
                }
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for copyFile
                from("direct://COPYFILE").to("box2://" + PATH_PREFIX + "/copyFile");

                // test route for createFileMetadata
                from("direct://CREATEFILEMETADATA").to("box2://" + PATH_PREFIX + "/createFileMetadata");

                // test route for createFileSharedLink
                from("direct://CREATEFILESHAREDLINK").to("box2://" + PATH_PREFIX + "/createFileSharedLink");

                // test route for deleteFile
                from("direct://DELETEFILE").to("box2://" + PATH_PREFIX + "/deleteFile?inBody=fileId");

                // test route for deleteFileMetadata
                from("direct://DELETEFILEMETADATA").to("box2://" + PATH_PREFIX + "/deleteFileMetadata?inBody=fileId");

                // test route for deleteFileVersion
                from("direct://DELETEFILEVERSION").to("box2://" + PATH_PREFIX + "/deleteFileVersion");

                // test route for downloadFile
                from("direct://DOWNLOADFILE").to("box2://" + PATH_PREFIX + "/downloadFile");

                // test route for downloadPreviousFileVersion
                from("direct://DOWNLOADPREVIOUSFILEVERSION")
                        .to("box2://" + PATH_PREFIX + "/downloadPreviousFileVersion");

                // test route for getDownloadURL
                from("direct://GETDOWNLOADURL").to("box2://" + PATH_PREFIX + "/getDownloadURL?inBody=fileId");

                // test route for getFileInfo
                from("direct://GETFILEINFO").to("box2://" + PATH_PREFIX + "/getFileInfo");

                // test route for getFileMetadata
                from("direct://GETFILEMETADATA").to("box2://" + PATH_PREFIX + "/getFileMetadata");

                // test route for getFilePreviewLink
                from("direct://GETFILEPREVIEWLINK").to("box2://" + PATH_PREFIX + "/getFilePreviewLink?inBody=fileId");

                // test route for getFileThumbnail
                from("direct://GETFILETHUMBNAIL").to("box2://" + PATH_PREFIX + "/getFileThumbnail");

                // test route for getFileVersions
                from("direct://GETFILEVERSIONS").to("box2://" + PATH_PREFIX + "/getFileVersions?inBody=fileId");

                // test route for moveFile
                from("direct://MOVEFILE").to("box2://" + PATH_PREFIX + "/moveFile");

                // test route for promoteFileVersion
                from("direct://PROMOTEFILEVERSION").to("box2://" + PATH_PREFIX + "/promoteFileVersion");

                // test route for renameFile
                from("direct://RENAMEFILE").to("box2://" + PATH_PREFIX + "/renameFile");

                // test route for updateFileInfo
                from("direct://UPDATEFILEINFO").to("box2://" + PATH_PREFIX + "/updateFileInfo");

                // test route for updateFileMetadata
                from("direct://UPDATEFILEMETADATA").to("box2://" + PATH_PREFIX + "/updateFileMetadata");

                // test route for uploadFile
                from("direct://UPLOADFILE").to("box2://" + PATH_PREFIX + "/uploadFile");

                // test route for uploadNewFileVersion
                from("direct://UPLOADNEWFILEVERSION").to("box2://" + PATH_PREFIX + "/uploadNewFileVersion");

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
        Box2Endpoint endpoint = (Box2Endpoint) context().getEndpoint("box2://" + PATH_PREFIX + "/copyFile");
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

    private int sizeOfIterable(Iterable<?> it) {
        if (it instanceof Collection) {
            return ((Collection<?>) it).size();
        } else {
            int i = 0;
            for (@SuppressWarnings("unused")
            Object obj : it) {
                i++;
            }
            return i;
        }

    }
}
