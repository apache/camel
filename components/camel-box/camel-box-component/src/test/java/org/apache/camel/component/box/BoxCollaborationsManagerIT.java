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

import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxCollaborationsManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxCollaborationsManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link BoxCollaborationsManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxCollaborationsManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxCollaborationsManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxCollaborationsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FOLDER = "CamelTestFolder";
    private static final String CAMEL_TEST_COLLABORATOR_EMAIL = "cameltest@example.com";
    private static final String CAMEL_TEST_COLLABORATOR_NAME = "cameltest";

    private BoxCollaboration testCollaboration;

    @Test
    public void testAddFolderCollaborationByEmail() {
        // delete collaborator created by setupTest
        deleteTestCollaborator();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", testFolder.getID());
        // parameter type is String
        headers.put("CamelBox.email", CAMEL_TEST_COLLABORATOR_EMAIL);
        // parameter type is com.box.sdk.BoxCollaboration.Role
        headers.put("CamelBox.role", BoxCollaboration.Role.EDITOR);

        final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://ADDFOLDERCOLLABORATIONBYEMAIL",
                testFolder.getID(), headers);

        assertNotNull(result, "addFolderCollaboration result");
        LOG.debug("addFolderCollaboration: {}", result);
    }

    @Disabled //creation of app users could be used only with JWT authentication, which is not possible in this time
    @Test
    public void testAddFolderCollaboration() {
        // delete collaborator created by setupTest
        deleteTestCollaborator();
        BoxUser user = null;
        try {
            // create test collaborator
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB
            user = BoxUser.createAppUser(getConnection(), CAMEL_TEST_COLLABORATOR_NAME, params).getResource();

            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.folderId", testFolder.getID());
            // parameter type is String
            headers.put("CamelBox.collaborator", user);
            // parameter type is com.box.sdk.BoxCollaboration.Role
            headers.put("CamelBox.role", BoxCollaboration.Role.EDITOR);

            final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://ADDFOLDERCOLLABORATION",
                    testFolder.getID(), headers);
            assertNotNull(result, "addFolderCollaboration result");
            LOG.debug("addFolderCollaboration: {}", result);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        } finally {
            if (user != null) {
                user.delete(false, true);
            }
        }
    }

    @Test
    public void testGetCollaborationInfo() {
        // using String message body for single parameter "collaborationId"
        final com.box.sdk.BoxCollaboration.Info result = requestBody("direct://GETCOLLABORATIONINFO",
                testCollaboration.getID());

        assertNotNull(result, "getCollaborationInfo result");
        LOG.debug("getCollaborationInfo: {}", result);
    }

    @Test
    public void testGetFolderCollaborations() {
        // using String message body for single parameter "folderId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETFOLDERCOLLABORATIONS", testFolder.getID());

        assertNotNull(result, "getFolderCollaborations result");
        LOG.debug("getFolderCollaborations: {}", result);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testGetPendingCollaborations() {
        final java.util.Collection result = requestBody("direct://GETPENDINGCOLLABORATIONS", null);

        assertNotNull(result, "getPendingCollaborations result");
        LOG.debug("getPendingCollaborations: {}", result);
    }

    @Test
    public void testUpdateCollaborationInfo() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.collaborationId", testCollaboration.getID());
        // parameter type is com.box.sdk.BoxCollaboration.Info

        BoxCollaboration.Info info = testCollaboration.getInfo();
        info.setRole(BoxCollaboration.Role.PREVIEWER);
        headers.put("CamelBox.info", info);

        final com.box.sdk.BoxCollaboration result = requestBodyAndHeaders("direct://UPDATECOLLABORATIONINFO", null,
                headers);

        assertNotNull(result, "updateCollaborationInfo result");
        assertNotNull(result.getInfo(), "updateCollaborationInfo info");
        assertEquals(BoxCollaboration.Role.PREVIEWER, result.getInfo().getRole(), "updateCollaborationInfo info");
        LOG.debug("updateCollaborationInfo: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // test route for addFolderCollaboration
                from("direct://ADDFOLDERCOLLABORATIONBYEMAIL")
                        .to("box://" + PATH_PREFIX + "/addFolderCollaborationByEmail");

                // test route for addFolderCollaboration
                from("direct://ADDFOLDERCOLLABORATION").to("box://" + PATH_PREFIX + "/addFolderCollaboration");

                // test route for deleteCollaboration
                from("direct://DELETECOLLABORATION")
                        .to("box://" + PATH_PREFIX + "/deleteCollaboration?inBody=collaborationId");

                // test route for getCollaborationInfo
                from("direct://GETCOLLABORATIONINFO")
                        .to("box://" + PATH_PREFIX + "/getCollaborationInfo?inBody=collaborationId");

                // test route for getFolderCollaborations
                from("direct://GETFOLDERCOLLABORATIONS")
                        .to("box://" + PATH_PREFIX + "/getFolderCollaborations?inBody=folderId");

                // test route for getPendingCollaborations
                from("direct://GETPENDINGCOLLABORATIONS").to("box://" + PATH_PREFIX + "/getPendingCollaborations");

                // test route for updateCollaborationInfo
                from("direct://UPDATECOLLABORATIONINFO").to("box://" + PATH_PREFIX + "/updateCollaborationInfo");

            }
        };
    }

    @BeforeEach
    public void setupTest() throws Exception {
        createTestFolder();
        createTestCollaborator();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestCollaborator();
        deleteTestFolder();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context()
                .getEndpoint("box://" + PATH_PREFIX + "/addFolderCollaboration");
        return endpoint.getBoxConnection();
    }

    private void createTestFolder() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        testFolder = rootFolder.createFolder(CAMEL_TEST_FOLDER).getResource();
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
