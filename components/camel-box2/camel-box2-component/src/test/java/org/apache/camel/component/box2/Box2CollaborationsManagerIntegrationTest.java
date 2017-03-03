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
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2CollaborationsManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for
 * {@link org.apache.camel.component.box2.api.Box2CollaborationsManager} APIs.
 */
public class Box2CollaborationsManagerIntegrationTest extends AbstractBox2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Box2CollaborationsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = Box2ApiCollection.getCollection()
            .getApiName(Box2CollaborationsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FOLDER = "CamelTestFolder";
    private static final String CAMEL_TEST_COLLABORATOR_EMAIL = "cameltest@example.com";
    private static final String CAMEL_TEST_COLLABORATOR_NAME = "cameltest";

    private BoxFolder testFolder;
    private BoxCollaboration testCollaboration;

    @Test
    public void testAddFolderCollaborationByEmail() throws Exception {
        // delete collaborator created by setupTest
        deleteTestCollaborator();

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox2.email", CAMEL_TEST_COLLABORATOR_EMAIL);
        // parameter type is com.box.sdk.BoxCollaboration.Role
        headers.put("CamelBox2.role", BoxCollaboration.Role.EDITOR);

        final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://ADDFOLDERCOLLABORATIONBYEMAIL",
                testFolder.getID(), headers);

        assertNotNull("addFolderCollaboration result", result);
        LOG.debug("addFolderCollaboration: " + result);
    }

    @Test
    public void testAddFolderCollaboration() throws Exception {
        // delete collaborator created by setupTest
        deleteTestCollaborator();
        BoxUser user = null;
        try {
            // create test collaborator
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB
            user = BoxUser.createAppUser(getConnection(), CAMEL_TEST_COLLABORATOR_NAME, params).getResource();

            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.folderId", testFolder.getID());
            // parameter type is String
            headers.put("CamelBox2.collaborator", user);
            // parameter type is com.box.sdk.BoxCollaboration.Role
            headers.put("CamelBox2.role", BoxCollaboration.Role.EDITOR);

            final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://ADDFOLDERCOLLABORATION",
                    testFolder.getID(), headers);
            assertNotNull("addFolderCollaboration result", result);
            LOG.debug("addFolderCollaboration: " + result);
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        } finally {
            if (user != null) {
                user.delete(false, true);
            }
        }
    }

    @Test
    public void testGetCollaborationInfo() throws Exception {
        // using String message body for single parameter "collaborationId"
        final com.box.sdk.BoxCollaboration.Info result = requestBody("direct://GETCOLLABORATIONINFO",
                testCollaboration.getID());

        assertNotNull("getCollaborationInfo result", result);
        LOG.debug("getCollaborationInfo: " + result);
    }

    @Test
    public void testGetFolderCollaborations() throws Exception {
        // using String message body for single parameter "folderId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETFOLDERCOLLABORATIONS", testFolder.getID());

        assertNotNull("getFolderCollaborations result", result);
        LOG.debug("getFolderCollaborations: " + result);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testGetPendingCollaborations() throws Exception {
        final java.util.Collection result = requestBody("direct://GETPENDINGCOLLABORATIONS", null);

        assertNotNull("getPendingCollaborations result", result);
        LOG.debug("getPendingCollaborations: " + result);
    }

    @Test
    public void testUpdateCollaborationInfo() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.collaborationId", testCollaboration.getID());
        // parameter type is com.box.sdk.BoxCollaboration.Info

        BoxCollaboration.Info info = testCollaboration.getInfo();
        info.setRole(BoxCollaboration.Role.PREVIEWER);
        headers.put("CamelBox2.info", info);

        final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://UPDATECOLLABORATIONINFO", null,
                headers);

        assertNotNull("updateCollaborationInfo result", result);
        assertNotNull("updateCollaborationInfo info", result.getInfo());
        assertEquals("updateCollaborationInfo info", BoxCollaboration.Role.PREVIEWER, result.getInfo().getRole());
        LOG.debug("updateCollaborationInfo: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                // test route for addFolderCollaboration
                from("direct://ADDFOLDERCOLLABORATIONBYEMAIL")
                        .to("box2://" + PATH_PREFIX + "/addFolderCollaborationByEmail");

                // test route for addFolderCollaboration
                from("direct://ADDFOLDERCOLLABORATION").to("box2://" + PATH_PREFIX + "/addFolderCollaboration");

                // test route for deleteCollaboration
                from("direct://DELETECOLLABORATION")
                        .to("box2://" + PATH_PREFIX + "/deleteCollaboration?inBody=collaborationId");

                // test route for getCollaborationInfo
                from("direct://GETCOLLABORATIONINFO")
                        .to("box2://" + PATH_PREFIX + "/getCollaborationInfo?inBody=collaborationId");

                // test route for getFolderCollaborations
                from("direct://GETFOLDERCOLLABORATIONS")
                        .to("box2://" + PATH_PREFIX + "/getFolderCollaborations?inBody=folderId");

                // test route for getPendingCollaborations
                from("direct://GETPENDINGCOLLABORATIONS").to("box2://" + PATH_PREFIX + "/getPendingCollaborations");

                // test route for updateCollaborationInfo
                from("direct://UPDATECOLLABORATIONINFO").to("box2://" + PATH_PREFIX + "/updateCollaborationInfo");

            }
        };
    }

    @Before
    public void setupTest() throws Exception {
        createTestFolder();
        createTestCollaborator();
    }

    @After
    public void teardownTest() {
        deleteTestCollaborator();
        deleteTestFolder();
    }

    public BoxAPIConnection getConnection() {
        Box2Endpoint endpoint = (Box2Endpoint) context()
                .getEndpoint("box2://" + PATH_PREFIX + "/addFolderCollaboration");
        return endpoint.getBoxConnection();
    }

    private void createTestFolder() throws FileNotFoundException {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        testFolder = rootFolder.createFolder(CAMEL_TEST_FOLDER).getResource();
    }

    private void deleteTestFolder() {
        testFolder.delete(true);
        testFolder = null;
    }

    private void createTestCollaborator() {
        testCollaboration = testFolder.collaborate(CAMEL_TEST_COLLABORATOR_EMAIL, BoxCollaboration.Role.EDITOR)
                .getResource();
    }

    private void deleteTestCollaborator() {
        if (testCollaboration != null) {
            testCollaboration.delete();
            testCollaboration = null;
        }
    }

}
