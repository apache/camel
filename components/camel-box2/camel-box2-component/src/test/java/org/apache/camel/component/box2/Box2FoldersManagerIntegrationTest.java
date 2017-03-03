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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxSharedLink;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2FoldersManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.box2.api.Box2FoldersManager}
 * APIs.
 */
public class Box2FoldersManagerIntegrationTest extends AbstractBox2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Box2FoldersManagerIntegrationTest.class);
    private static final String PATH_PREFIX = Box2ApiCollection.getCollection()
            .getApiName(Box2FoldersManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FOLDER = "CamelTestFolder";
    private static final String CAMEL_TEST_FOLDER_DESCRIPTION = "This is a description of CamelTestFolder";
    private static final String CAMEL_TEST_COPY_FOLDER = Box2FoldersManagerIntegrationTest.CAMEL_TEST_FOLDER + "_Copy";
    private static final String CAMEL_TEST_MOVE_FOLDER = Box2FoldersManagerIntegrationTest.CAMEL_TEST_FOLDER + "_Move";
    private static final String CAMEL_TEST_RENAME_FOLDER = Box2FoldersManagerIntegrationTest.CAMEL_TEST_FOLDER
            + "_Rename";
    private static final String CAMEL_TEST_ROOT_FOLDER_ID = "0";
    private static final String CAMEL_TEST_DESTINATION_FOLDER_ID = "0";

    private BoxFolder testFolder;

    @Test
    public void testCreateFolder() throws Exception {

        // delete folder created in test setup.
        deleteTestFolder();

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.parentFolderId", "0");
        // parameter type is String
        headers.put("CamelBox2.folderName", CAMEL_TEST_FOLDER);

        testFolder = requestBodyAndHeaders("direct://CREATEFOLDER", null, headers);

        assertNotNull("createFolder result", testFolder);
        assertEquals("createFolder folder name", CAMEL_TEST_FOLDER, testFolder.getInfo().getName());
        LOG.debug("createFolder: " + testFolder);
    }

    @Test
    public void testDeleteFolder() throws Exception {
        // using String message body for single parameter "folderId"
        requestBody("direct://DELETEFOLDER", testFolder.getID());

        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        Iterable<BoxItem.Info> it = rootFolder.search("^" + CAMEL_TEST_FOLDER + "$");
        int searchResults = sizeOfIterable(it);
        boolean exists = searchResults > 0 ? true : false;
        assertEquals("deleteFolder exists", false, exists);
        LOG.debug("deleteFolder: exists? " + exists);
    }

    @Test
    public void testCopyFolder() throws Exception {
        com.box.sdk.BoxFolder result = null;
        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.folderId", testFolder.getID());
            // parameter type is String
            headers.put("CamelBox2.destinationFolderId", CAMEL_TEST_DESTINATION_FOLDER_ID);
            // parameter type is String
            headers.put("CamelBox2.newName", CAMEL_TEST_COPY_FOLDER);
            result = requestBodyAndHeaders("direct://COPYFOLDER", null, headers);
            assertNotNull("copyFolder result", result);
            assertEquals("copyFolder folder name", CAMEL_TEST_COPY_FOLDER, result.getInfo().getName());
            LOG.debug("copyFolder: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete(true);
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testCreateSharedLink() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is com.box.sdk.BoxSharedLink.Access
        headers.put("CamelBox2.access", BoxSharedLink.Access.COLLABORATORS);
        // parameter type is java.util.Date
        headers.put("CamelBox2.unshareDate", null);
        // parameter type is com.box.sdk.BoxSharedLink.Permissions
        headers.put("CamelBox2.permissions", new BoxSharedLink.Permissions());

        final com.box.sdk.BoxSharedLink result = requestBodyAndHeaders("direct://CREATEFOLDERSHAREDLINK", null,
                headers);

        assertNotNull("createFolderSharedLink result", result);
        LOG.debug("createFolderSharedLink: " + result);
    }

    @Test
    public void testGetFolder() throws Exception {
        // using String[] message body for single parameter "path"
        final com.box.sdk.BoxFolder result = requestBody("direct://GETFOLDER", new String[] {CAMEL_TEST_FOLDER});

        assertNotNull("getFolder result", result);
        assertEquals("getFolder folder id", testFolder.getID(), result.getID());
        LOG.debug("getFolder: " + result);
    }

    @Test
    public void testGetFolderInfo() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is String[]
        headers.put("CamelBox2.fields", new String[] {"name"});

        final com.box.sdk.BoxFolder.Info result = requestBodyAndHeaders("direct://GETFOLDERINFO", null, headers);

        assertNotNull("getFolderInfo result", result);
        assertNotNull("getFolderInfo result.getName()", result.getName());
        assertEquals("getFolderInfo info name", CAMEL_TEST_FOLDER, result.getName());
        LOG.debug("getFolderInfo: " + result);
    }

    @Test
    public void testGetFolderItems() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", CAMEL_TEST_ROOT_FOLDER_ID);
        // parameter type is Long
        headers.put("CamelBox2.offset", null);
        // parameter type is Long
        headers.put("CamelBox2.limit", null);
        // parameter type is String[]
        headers.put("CamelBox2.fields", null);

        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBodyAndHeaders("direct://GETFOLDERITEMS", null, headers);

        assertNotNull("getFolderItems result", result);
        LOG.debug("getFolderItems: " + result);
    }

    @Test
    public void testGetRootFolder() throws Exception {
        final com.box.sdk.BoxFolder result = requestBody("direct://GETROOTFOLDER", null);

        assertNotNull("getRootFolder result", result);
        LOG.debug("getRootFolder: " + result);
    }

    @Test
    public void testMoveFolder() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox2.destinationFolderId", CAMEL_TEST_DESTINATION_FOLDER_ID);
        // parameter type is String
        headers.put("CamelBox2.newName", CAMEL_TEST_MOVE_FOLDER);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://MOVEFOLDER", null, headers);

        assertNotNull("moveFolder result", result);
        assertEquals("moveFolder folder name", CAMEL_TEST_MOVE_FOLDER, result.getInfo().getName());
        LOG.debug("moveFolder: " + result);
    }

    @Test
    public void testRenameFolder() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox2.newFolderName", CAMEL_TEST_RENAME_FOLDER);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://RENAMEFOLDER", null, headers);

        assertNotNull("renameFolder result", result);
        assertEquals("moveFolder folder name", CAMEL_TEST_RENAME_FOLDER, result.getInfo().getName());
        LOG.debug("renameFolder: " + result);
    }

    @Test
    public void testUpdateInfo() throws Exception {
        final BoxFolder.Info testFolderInfo = testFolder.getInfo();

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is com.box.sdk.BoxFolder.Info
        testFolderInfo.setDescription(CAMEL_TEST_FOLDER_DESCRIPTION);
        headers.put("CamelBox2.info", testFolderInfo);

        final com.box.sdk.BoxFolder result = requestBodyAndHeaders("direct://UPDATEFOLDERINFO", null, headers);

        assertNotNull("updateInfo result", result);
        assertEquals("update folder info description", CAMEL_TEST_FOLDER_DESCRIPTION,
                result.getInfo().getDescription());
        LOG.debug("updateInfo: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for copyFolder
                from("direct://COPYFOLDER").to("box2://" + PATH_PREFIX + "/copyFolder");

                // test route for createFolder
                from("direct://CREATEFOLDER").to("box2://" + PATH_PREFIX + "/createFolder");

                // test route for createFolderSharedLink
                from("direct://CREATEFOLDERSHAREDLINK").to("box2://" + PATH_PREFIX + "/createFolderSharedLink");

                // test route for deleteFolder
                from("direct://DELETEFOLDER").to("box2://" + PATH_PREFIX + "/deleteFolder?inBody=folderId");

                // test route for getFolder
                from("direct://GETFOLDER").to("box2://" + PATH_PREFIX + "/getFolder?inBody=path");

                // test route for getFolderInfo
                from("direct://GETFOLDERINFO").to("box2://" + PATH_PREFIX + "/getFolderInfo");

                // test route for getFolderItems
                from("direct://GETFOLDERITEMS").to("box2://" + PATH_PREFIX + "/getFolderItems");

                // test route for getRootFolder
                from("direct://GETROOTFOLDER").to("box2://" + PATH_PREFIX + "/getRootFolder");

                // test route for moveFolder
                from("direct://MOVEFOLDER").to("box2://" + PATH_PREFIX + "/moveFolder");

                // test route for renameFolder
                from("direct://RENAMEFOLDER").to("box2://" + PATH_PREFIX + "/renameFolder");

                // test route for updateFolderInfo
                from("direct://UPDATEFOLDERINFO").to("box2://" + PATH_PREFIX + "/updateFolderInfo");

            }
        };
    }

    @Before
    public void setupTest() throws Exception {
        createTestFolder();
    }

    @After
    public void teardownTest() {
        deleteTestFolder();
    }

    public BoxAPIConnection getConnection() {
        Box2Endpoint endpoint = (Box2Endpoint) context().getEndpoint("box2://" + PATH_PREFIX + "/copyFolder");
        return endpoint.getBoxConnection();
    }

    private void createTestFolder() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        testFolder = rootFolder.createFolder(CAMEL_TEST_FOLDER).getResource();
    }

    private void deleteTestFolder() {
        if (testFolder != null) {
            try {
                testFolder.delete(true);
            } catch (Throwable t) {
            }
            testFolder = null;
        }
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
