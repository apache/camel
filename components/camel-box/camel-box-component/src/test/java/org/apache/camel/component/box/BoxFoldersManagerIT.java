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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxSharedLink;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxFoldersManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxFoldersManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link BoxFoldersManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxFoldersManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxFoldersManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxFoldersManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FOLDER = "CamelTestFolder";
    private static final String CAMEL_TEST_FOLDER_DESCRIPTION = "This is a description of CamelTestFolder";
    private static final String CAMEL_TEST_COPY_FOLDER = BoxFoldersManagerIT.CAMEL_TEST_FOLDER + "_Copy";
    private static final String CAMEL_TEST_MOVE_FOLDER = BoxFoldersManagerIT.CAMEL_TEST_FOLDER + "_Move";
    private static final String CAMEL_TEST_RENAME_FOLDER = BoxFoldersManagerIT.CAMEL_TEST_FOLDER
                                                           + "_Rename";
    private static final String CAMEL_TEST_ROOT_FOLDER_ID = "0";
    private static final String CAMEL_TEST_DESTINATION_FOLDER_ID = "0";

    @Test
    public void testCreateFolder() {

        // delete folder created in test setup.
        deleteTestFolder();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.parentFolderId", "0");
        // parameter type is String
        headers.put("CamelBox.folderName", CAMEL_TEST_FOLDER);

        testFolder = requestBodyAndHeaders("direct://CREATEFOLDER", null, headers);

        assertNotNull(testFolder, "createFolder result");
        assertEquals(CAMEL_TEST_FOLDER, testFolder.getInfo().getName(), "createFolder folder name");
        LOG.debug("createFolder: {}", testFolder);
    }

    @Test
    public void testCreateFolderByPath() {

        // delete folder created in test setup.
        deleteTestFolder();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.parentFolderId", "0");
        // parameter type is String[]
        headers.put("CamelBox.path", new String[] { CAMEL_TEST_FOLDER });

        testFolder = requestBodyAndHeaders("direct://CREATEFOLDER", null, headers);

        assertNotNull(testFolder, "createFolder result");
        assertEquals(CAMEL_TEST_FOLDER, testFolder.getInfo().getName(), "createFolder folder name");
        LOG.debug("createFolder: {}", testFolder);
    }

    @Test
    public void testDeleteFolder() {
        // using String message body for single parameter "folderId"
        requestBody("direct://DELETEFOLDER", testFolder.getID());
    }

    @Test
    public void testCopyFolder() {
        com.box.sdk.BoxFolder result = null;
        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.folderId", testFolder.getID());
            // parameter type is String
            headers.put("CamelBox.destinationFolderId", CAMEL_TEST_DESTINATION_FOLDER_ID);
            // parameter type is String
            headers.put("CamelBox.newName", CAMEL_TEST_COPY_FOLDER);
            result = requestBodyAndHeaders("direct://COPYFOLDER", null, headers);
            assertNotNull(result, "copyFolder result");
            assertEquals(CAMEL_TEST_COPY_FOLDER, result.getInfo().getName(), "copyFolder folder name");
            LOG.debug("copyFolder: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete(true);
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testCreateSharedLink() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is com.box.sdk.BoxSharedLink.Access
        headers.put("CamelBox.access", BoxSharedLink.Access.COLLABORATORS);
        // parameter type is java.util.Date
        headers.put("CamelBox.unshareDate", null);
        // parameter type is com.box.sdk.BoxSharedLink.Permissions
        headers.put("CamelBox.permissions", new BoxSharedLink.Permissions());

        final com.box.sdk.BoxSharedLink result = requestBodyAndHeaders("direct://CREATEFOLDERSHAREDLINK", null,
                headers);

        assertNotNull(result, "createFolderSharedLink result");
        LOG.debug("createFolderSharedLink: {}", result);
    }

    @Test
    public void testGetFolder() {
        // using String[] message body for single parameter "path"
        final com.box.sdk.BoxFolder result = requestBody("direct://GETFOLDER", new String[] { CAMEL_TEST_FOLDER });

        assertNotNull(result, "getFolder result");
        assertEquals(testFolder.getID(), result.getID(), "getFolder folder id");
        LOG.debug("getFolder: {}", result);
    }

    @Test
    public void testGetFolderInfo() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is String[]
        headers.put("CamelBox.fields", new String[] { "name" });

        final com.box.sdk.BoxFolder.Info result = requestBodyAndHeaders("direct://GETFOLDERINFO", null, headers);

        assertNotNull(result, "getFolderInfo result");
        assertNotNull(result.getName(), "getFolderInfo result.getName()");
        assertEquals(CAMEL_TEST_FOLDER, result.getName(), "getFolderInfo info name");
        LOG.debug("getFolderInfo: {}", result);
    }

    @Test
    public void testGetFolderItems() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", CAMEL_TEST_ROOT_FOLDER_ID);
        // parameter type is Long
        headers.put("CamelBox.offset", null);
        // parameter type is Long
        headers.put("CamelBox.limit", null);
        // parameter type is String[]
        headers.put("CamelBox.fields", null);

        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBodyAndHeaders("direct://GETFOLDERITEMS", null, headers);

        assertNotNull(result, "getFolderItems result");
        LOG.debug("getFolderItems: {}", result);
    }

    @Test
    public void testGetRootFolder() {
        final com.box.sdk.BoxFolder result = requestBody("direct://GETROOTFOLDER", null);

        assertNotNull(result, "getRootFolder result");
        LOG.debug("getRootFolder: {}", result);
    }

    @Test
    public void testMoveFolder() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox.destinationFolderId", CAMEL_TEST_DESTINATION_FOLDER_ID);
        // parameter type is String
        headers.put("CamelBox.newName", CAMEL_TEST_MOVE_FOLDER);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://MOVEFOLDER", null, headers);

        assertNotNull(result, "moveFolder result");
        assertEquals(CAMEL_TEST_MOVE_FOLDER, result.getInfo().getName(), "moveFolder folder name");
        LOG.debug("moveFolder: {}", result);
    }

    @Test
    public void testRenameFolder() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox.newFolderName", CAMEL_TEST_RENAME_FOLDER);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://RENAMEFOLDER", null, headers);

        assertNotNull(result, "renameFolder result");
        assertEquals(CAMEL_TEST_RENAME_FOLDER, result.getInfo().getName(), "moveFolder folder name");
        LOG.debug("renameFolder: {}", result);
    }

    @Test
    public void testUpdateInfo() {
        final BoxFolder.Info testFolderInfo = testFolder.getInfo();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is com.box.sdk.BoxFolder.Info
        testFolderInfo.setDescription(CAMEL_TEST_FOLDER_DESCRIPTION);
        headers.put("CamelBox.info", testFolderInfo);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://UPDATEFOLDERINFO", null, headers);

        assertNotNull(result, "updateInfo result");
        assertEquals(CAMEL_TEST_FOLDER_DESCRIPTION, result.getInfo().getDescription(), "update folder info description");
        LOG.debug("updateInfo: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for copyFolder
                from("direct://COPYFOLDER").to("box://" + PATH_PREFIX + "/copyFolder");

                // test route for createFolder
                from("direct://CREATEFOLDER").to("box://" + PATH_PREFIX + "/createFolder");

                // test route for createFolderSharedLink
                from("direct://CREATEFOLDERSHAREDLINK").to("box://" + PATH_PREFIX + "/createFolderSharedLink");

                // test route for deleteFolder
                from("direct://DELETEFOLDER").to("box://" + PATH_PREFIX + "/deleteFolder?inBody=folderId");

                // test route for getFolder
                from("direct://GETFOLDER").to("box://" + PATH_PREFIX + "/getFolder?inBody=path");

                // test route for getFolderInfo
                from("direct://GETFOLDERINFO").to("box://" + PATH_PREFIX + "/getFolderInfo");

                // test route for getFolderItems
                from("direct://GETFOLDERITEMS").to("box://" + PATH_PREFIX + "/getFolderItems");

                // test route for getRootFolder
                from("direct://GETROOTFOLDER").to("box://" + PATH_PREFIX + "/getRootFolder");

                // test route for moveFolder
                from("direct://MOVEFOLDER").to("box://" + PATH_PREFIX + "/moveFolder");

                // test route for renameFolder
                from("direct://RENAMEFOLDER").to("box://" + PATH_PREFIX + "/renameFolder");

                // test route for updateFolderInfo
                from("direct://UPDATEFOLDERINFO").to("box://" + PATH_PREFIX + "/updateFolderInfo");

            }
        };
    }

    @BeforeEach
    public void setupTest() {
        createTestFolder();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestFolder();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/copyFolder");
        return endpoint.getBoxConnection();
    }

    private void createTestFolder() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        testFolder = rootFolder.createFolder(CAMEL_TEST_FOLDER).getResource();
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
