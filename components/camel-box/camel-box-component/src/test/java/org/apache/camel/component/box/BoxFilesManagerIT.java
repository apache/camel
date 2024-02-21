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
package org.apache.camel.component.box;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFile.ThumbnailFileType;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxSharedLink;
import com.box.sdk.Metadata;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxFilesManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxFilesManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test class for {@link BoxFilesManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxFilesManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxFilesManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxFilesManagerApiMethod.class).getName();

    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_DESCRIPTION = "CamelTestFile.txt description";
    private static final String CAMEL_TEST_COPY_FILE_NAME = "CamelTestFile_Copy.txt";
    private static final String CAMEL_TEST_MOVE_FILE_NAME = "CamelTestFile_Move.txt";
    private static final String CAMEL_TEST_RENAME_FILE_NAME = "CamelTestFile_Rename.txt";
    private static final String CAMEL_TEST_UPLOAD_FILE_NAME = "CamelTestFile_Upload.txt";

    @Test
    public void testCopyFile() {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox.destinationFolderId", "0");
            // parameter type is String
            headers.put("CamelBox.newName", CAMEL_TEST_COPY_FILE_NAME);

            result = requestBodyAndHeaders("direct://COPYFILE", null, headers);

            assertNotNull(result, "copyFile result");
            assertEquals(CAMEL_TEST_COPY_FILE_NAME, result.getInfo().getName(), "copyFile name");
            LOG.debug("copyFile: {}", result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Test
    public void testCreateFileMetadata() {
        Metadata metadata = new Metadata();
        metadata.add("/foo", "bar");

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is com.box.sdk.Metadata
        headers.put("CamelBox.metadata", metadata);
        // parameter type is String
        headers.put("CamelBox.typeName", null);

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://CREATEFILEMETADATA", null, headers);

        assertNotNull(result, "createFileMetadata result");
        assertEquals("bar", result.getString("/foo"), "createFileMetadata result");
        LOG.debug("createFileMetadata: {}", result);
    }

    @Test
    public void testCreateFileSharedLink() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxSharedLink.Access
        headers.put("CamelBox.access", BoxSharedLink.Access.DEFAULT);
        // parameter type is java.util.Date
        headers.put("CamelBox.unshareDate", null);
        // parameter type is com.box.sdk.BoxSharedLink.Permissions
        headers.put("CamelBox.permissions", null);

        final com.box.sdk.BoxSharedLink result = requestBodyAndHeaders("direct://CREATEFILESHAREDLINK", null, headers);

        assertNotNull(result, "createFileSharedLink result");
        LOG.debug("createFileSharedLink: {}", result);
    }

    @Test
    public void testDeleteFile() {
        // using String message body for single parameter "fileId"
        requestBody("direct://DELETEFILE", testFile.getID());
    }

    @Test
    public void testDeleteFileMetadata() {
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

    @Disabled // Requires premium user account to test.
    @Test
    public void testDeleteFileVersion() {
        testFile.uploadNewVersion(getClass().getResourceAsStream(CAMEL_TEST_FILE));

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox.version", 0);

        requestBodyAndHeaders("direct://DELETEFILEVERSION", null, headers);
        boolean onlyOneVersion = testFile.getVersions().size() == 1;
        assertTrue(onlyOneVersion, "deleteFileVersion version deleted");
    }

    @Test
    public void testDownloadFile() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is java.io.OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        headers.put("CamelBox.output", output);
        // parameter type is Long
        headers.put("CamelBox.rangeStart", null);
        // parameter type is Long
        headers.put("CamelBox.rangeEnd", null);
        // parameter type is com.box.sdk.ProgressListener
        headers.put("CamelBox.listener", null);

        final java.io.OutputStream result = requestBodyAndHeaders("direct://DOWNLOADFILE", null, headers);

        assertNotNull(result, "downloadFile result");
        LOG.debug("downloadFile: {}", result);
    }

    @Disabled // Requires premium user account to test
    @Test
    public void testDownloadPreviousFileVersion() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox.version", 0);
        // parameter type is java.io.OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        headers.put("CamelBox.output", output);
        // parameter type is com.box.sdk.ProgressListener
        headers.put("CamelBox.listener", null);

        final java.io.OutputStream result = requestBodyAndHeaders("direct://DOWNLOADPREVIOUSFILEVERSION", null,
                headers);

        assertNotNull(result, "downloadPreviousFileVersion result");
        LOG.debug("downloadPreviousFileVersion: {}", result);
    }

    @Test
    public void testGetDownloadURL() {
        // using String message body for single parameter "fileId"
        final java.net.URL result = requestBody("direct://GETDOWNLOADURL", testFile.getID());

        assertNotNull(result, "getDownloadURL result");
        LOG.debug("getDownloadURL: {}", result);
    }

    @Test
    public void testGetFileInfo() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is String[]
        headers.put("CamelBox.fields", null);

        final com.box.sdk.BoxFile.Info result = requestBodyAndHeaders("direct://GETFILEINFO", null, headers);

        assertNotNull(result, "getFileInfo result");
        LOG.debug("getFileInfo: {}", result);
    }

    @Test
    public void testGetFileMetadata() {
        testFile.createMetadata(new Metadata());

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is String
        headers.put("CamelBox.typeName", null);

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://GETFILEMETADATA", null, headers);

        assertNotNull(result, "getFileMetadata result");
        LOG.debug("getFileMetadata: {}", result);
    }

    @Test
    public void testGetFilePreviewLink() {
        // using String message body for single parameter "fileId"
        final java.net.URL result = requestBody("direct://GETFILEPREVIEWLINK", testFile.getID());

        assertNotNull(result, "getFilePreviewLink result");
        LOG.debug("getFilePreviewLink: {}", result);
    }

    @Test
    public void testGetFileThumbnail() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxFile.ThumbnailFileType
        headers.put("CamelBox.fileType", ThumbnailFileType.JPG);
        // parameter type is Integer
        headers.put("CamelBox.minWidth", 32);
        // parameter type is Integer
        headers.put("CamelBox.minHeight", 32);
        // parameter type is Integer
        headers.put("CamelBox.maxWidth", 32);
        // parameter type is Integer
        headers.put("CamelBox.maxHeight", 32);

        final byte[] result = requestBodyAndHeaders("direct://GETFILETHUMBNAIL", null, headers);

        assertNotNull(result, "getFileThumbnail result");
        LOG.debug("getFileThumbnail: {}", result);
    }

    @Test
    public void testGetFileVersions() {
        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETFILEVERSIONS", testFile.getID());

        assertNotNull(result, "getFileVersions result");
        LOG.debug("getFileVersions: {}", result);
    }

    @Test
    public void testMoveFile() {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox.destinationFolderId", "0");
            // parameter type is String
            headers.put("CamelBox.newName", CAMEL_TEST_MOVE_FILE_NAME);

            result = requestBodyAndHeaders("direct://MOVEFILE", null, headers);

            assertNotNull(result, "moveFile result");
            assertEquals(CAMEL_TEST_MOVE_FILE_NAME, result.getInfo().getName(), "moveFile name");
            LOG.debug("moveFile: {}", result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Disabled // Requires premium user account to test
    @Test
    public void testPromoteFileVersion() {
        testFile.uploadNewVersion(getClass().getResourceAsStream(CAMEL_TEST_FILE));

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is Integer
        headers.put("CamelBox.version", 1);

        final com.box.sdk.BoxFileVersion result = requestBodyAndHeaders("direct://PROMOTEFILEVERSION", null, headers);

        assertNotNull(result, "promoteFileVersion result");
        LOG.debug("promoteFileVersion: {}", result);
    }

    @Test
    public void testRenameFile() {

        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.fileId", testFile.getID());
            // parameter type is String
            headers.put("CamelBox.newFileName", CAMEL_TEST_RENAME_FILE_NAME);

            result = requestBodyAndHeaders("direct://RENAMEFILE", null, headers);

            assertNotNull(result, "renameFile result");
            assertEquals(CAMEL_TEST_RENAME_FILE_NAME, result.getInfo().getName(), "renameFile name");
            LOG.debug("renameFile: {}", result);
        } finally {
            if (result != null) {
                result.delete();
            }
        }
    }

    @Test
    public void testUpdateFileInfo() {
        BoxFile.Info info = testFile.getInfo();
        info.setDescription(CAMEL_TEST_FILE_DESCRIPTION);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is com.box.sdk.BoxFile.Info
        headers.put("CamelBox.info", info);

        final com.box.sdk.BoxFile result = requestBodyAndHeaders("direct://UPDATEFILEINFO", null, headers);

        assertNotNull(result, "updateFileInfo result");
        assertEquals(CAMEL_TEST_FILE_DESCRIPTION, result.getInfo().getDescription(), "updateFileInfo info");
        LOG.debug("updateFileInfo: {}", result);
    }

    @Test
    public void testUpdateFileMetadata() {
        Metadata metadata = new Metadata();
        metadata = testFile.createMetadata(metadata);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is com.box.sdk.Metadata
        headers.put("CamelBox.metadata", metadata);

        //metada has to contain some value, otherwise response result will be error code 400
        metadata.add("/foo", "bar");

        final com.box.sdk.Metadata result = requestBodyAndHeaders("direct://UPDATEFILEMETADATA", null, headers);

        assertNotNull(result, "updateFileMetadata result");
        assertNotNull(result.getString("/foo"), "updateFileMetadata property foo");
        assertEquals("bar", metadata.getString("/foo"));
        LOG.debug("updateFileMetadata: {}", result);
    }

    @Test
    public void testUploadFile() {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            headers.put("CamelBox.parentFolderId", "0");
            headers.put("CamelBox.content", getClass().getResourceAsStream(CAMEL_TEST_FILE));
            headers.put("CamelBox.fileName", CAMEL_TEST_UPLOAD_FILE_NAME);
            headers.put("CamelBox.created", null);
            headers.put("CamelBox.modified", null);
            headers.put("CamelBox.size", null);
            headers.put("CamelBox.listener", null);

            result = requestBodyAndHeaders("direct://UPLOADFILE", null, headers);

            assertNotNull(result, "uploadFile result");
            LOG.debug("uploadFile: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testUploadOverwriteFile() {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            headers.put("CamelBox.parentFolderId", "0");
            headers.put("CamelBox.content", getClass().getResourceAsStream(CAMEL_TEST_FILE));
            headers.put("CamelBox.fileName", CAMEL_TEST_UPLOAD_FILE_NAME);
            headers.put("CamelBox.created", null);
            headers.put("CamelBox.modified", null);
            headers.put("CamelBox.size", null);
            headers.put("CamelBox.listener", null);

            result = requestBodyAndHeaders("direct://UPLOADFILE", null, headers);
            assertNotNull(result, "uploadFile result");
            result = requestBodyAndHeaders("direct://UPLOADFILEOVERWRITE", null, headers);
            assertNotNull(result, "uploadFile overwrite result");
            LOG.debug("uploadFile: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testUploadNewFileVersion() {
        com.box.sdk.BoxFile result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.fileId", testFile.getID());
            // parameter type is java.io.InputStream
            headers.put("CamelBox.fileContent", getClass().getResourceAsStream(CAMEL_TEST_FILE));
            // parameter type is java.util.Date
            headers.put("CamelBox.modified", null);
            // parameter type is Long
            headers.put("CamelBox.fileSize", null);
            // parameter type is com.box.sdk.ProgressListener
            headers.put("CamelBox.listener", null);

            result = requestBodyAndHeaders("direct://UPLOADNEWFILEVERSION", null, headers);

            assertNotNull(result, "uploadNewFileVersion result");
            LOG.debug("uploadNewFileVersion: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Exception t) {
                }
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for copyFile
                from("direct://COPYFILE").to("box://" + PATH_PREFIX + "/copyFile");

                // test route for createFileMetadata
                from("direct://CREATEFILEMETADATA").to("box://" + PATH_PREFIX + "/createFileMetadata");

                // test route for createFileSharedLink
                from("direct://CREATEFILESHAREDLINK").to("box://" + PATH_PREFIX + "/createFileSharedLink");

                // test route for deleteFile
                from("direct://DELETEFILE").to("box://" + PATH_PREFIX + "/deleteFile?inBody=fileId");

                // test route for deleteFileMetadata
                from("direct://DELETEFILEMETADATA").to("box://" + PATH_PREFIX + "/deleteFileMetadata?inBody=fileId");

                // test route for deleteFileVersion
                from("direct://DELETEFILEVERSION").to("box://" + PATH_PREFIX + "/deleteFileVersion");

                // test route for downloadFile
                from("direct://DOWNLOADFILE").to("box://" + PATH_PREFIX + "/downloadFile");

                // test route for downloadPreviousFileVersion
                from("direct://DOWNLOADPREVIOUSFILEVERSION")
                        .to("box://" + PATH_PREFIX + "/downloadPreviousFileVersion");

                // test route for getDownloadURL
                from("direct://GETDOWNLOADURL").to("box://" + PATH_PREFIX + "/getDownloadURL?inBody=fileId");

                // test route for getFileInfo
                from("direct://GETFILEINFO").to("box://" + PATH_PREFIX + "/getFileInfo");

                // test route for getFileMetadata
                from("direct://GETFILEMETADATA").to("box://" + PATH_PREFIX + "/getFileMetadata");

                // test route for getFilePreviewLink
                from("direct://GETFILEPREVIEWLINK").to("box://" + PATH_PREFIX + "/getFilePreviewLink?inBody=fileId");

                // test route for getFileThumbnail
                from("direct://GETFILETHUMBNAIL").to("box://" + PATH_PREFIX + "/getFileThumbnail");

                // test route for getFileVersions
                from("direct://GETFILEVERSIONS").to("box://" + PATH_PREFIX + "/getFileVersions?inBody=fileId");

                // test route for moveFile
                from("direct://MOVEFILE").to("box://" + PATH_PREFIX + "/moveFile");

                // test route for promoteFileVersion
                from("direct://PROMOTEFILEVERSION").to("box://" + PATH_PREFIX + "/promoteFileVersion");

                // test route for renameFile
                from("direct://RENAMEFILE").to("box://" + PATH_PREFIX + "/renameFile");

                // test route for updateFileInfo
                from("direct://UPDATEFILEINFO").to("box://" + PATH_PREFIX + "/updateFileInfo");

                // test route for updateFileMetadata
                from("direct://UPDATEFILEMETADATA").to("box://" + PATH_PREFIX + "/updateFileMetadata");

                // test route for uploadFile
                from("direct://UPLOADFILE").to("box://" + PATH_PREFIX + "/uploadFile");

                // test route for uploadFile to overwrite
                from("direct://UPLOADFILEOVERWRITE").to("box://" + PATH_PREFIX + "/uploadFile?check=true");

                // test route for uploadNewFileVersion
                from("direct://UPLOADNEWFILEVERSION").to("box://" + PATH_PREFIX + "/uploadNewFileVersion");

            }
        };
    }

    @BeforeEach
    public void setupTest() throws Exception {
        createTestFile();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestFile();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/copyFile");
        return endpoint.getBoxConnection();
    }

    private void createTestFile() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
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
