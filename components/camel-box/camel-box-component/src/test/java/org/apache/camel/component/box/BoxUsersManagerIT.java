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
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;
import com.box.sdk.EmailAlias;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxUsersManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxUsersManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test class for {@link BoxUsersManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxUsersManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxUsersManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxUsersManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_USER_EMAIL_ALIAS = "camel@example.com";
    private static final String CAMEL_TEST_USER_JOB_TITLE = "Camel Tester";
    private static final String CAMEL_TEST_CREATE_APP_USER_NAME = "Wilma";
    private static final String CAMEL_TEST_CREATE_ENTERPRISE_USER_NAME = "fred";
    private static final String CAMEL_TEST_CREATE_ENTERPRISE_USER2_NAME = "gregory";
    private static final String CAMEL_TEST_ENTERPRISE_USER_LOGIN_KEY = "enterpriseUser1Login";
    private static final String CAMEL_TEST_ENTERPRISE_USER2_LOGIN_KEY = "enterpriseUser2Login";

    private BoxUser testUser;

    @Disabled
    @Test
    public void testAddUserEmailAlias() {
        com.box.sdk.EmailAlias result = null;
        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.userId", testUser.getID());
            // parameter type is String
            headers.put("CamelBox.email", CAMEL_TEST_USER_EMAIL_ALIAS);
            result = requestBodyAndHeaders("direct://ADDUSEREMAILALIAS", null, headers);
            assertNotNull(result, "addUserEmailAlias result");
            LOG.debug("addUserEmailAlias: {}", result);
        } finally {
            if (result != null) {
                try {
                    testUser.deleteEmailAlias(result.getID());
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testCreateAppUser() {
        //This test makes sense only with JWT authentication. With standard (OAuth) it will always fail.
        assumeTrue(jwtAuthentication, "Test has to be executed with standard authentication.");

        com.box.sdk.BoxUser result = null;

        try {
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB

            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.name", CAMEL_TEST_CREATE_APP_USER_NAME);
            // parameter type is com.box.sdk.CreateUserParams
            headers.put("CamelBox.params", params);

            result = requestBodyAndHeaders("direct://CREATEAPPUSER", null, headers);

            assertNotNull(result, "createAppUser result");
            LOG.debug("createAppUser: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete(false, true);
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testCreateEnterpriseUser() {
        //This test makes sense only with standard (OAuth) authentication, with JWT it will always fail with return code 403
        assumeFalse(jwtAuthentication, "Test has to be executed with standard authentication.");

        String enterpriseUser1Login = (String) options.get(CAMEL_TEST_ENTERPRISE_USER_LOGIN_KEY);
        if (enterpriseUser1Login != null && enterpriseUser1Login.isBlank()) {
            enterpriseUser1Login = null;
        }

        assertNotNull(enterpriseUser1Login,
                "Email for enterprise user has to be defined in test-options.properties for this test to succeed.");

        com.box.sdk.BoxUser result = null;

        try {
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB

            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.login", enterpriseUser1Login);
            // parameter type is String
            headers.put("CamelBox.name", CAMEL_TEST_CREATE_ENTERPRISE_USER_NAME);
            // parameter type is com.box.sdk.CreateUserParams
            headers.put("CamelBox.params", params);

            result = requestBodyAndHeaders("direct://CREATEENTERPRISEUSER", null, headers);

            assertNotNull(result, "createEnterpriseUser result");
            LOG.debug("createEnterpriseUser: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete(false, true);
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testDeleteUser() throws Exception {
        //This test makes sense only with JWT authentication. With standard (OAuth) it will always fail.
        assumeTrue(jwtAuthentication, "Test has to be executed with standard authentication.");

        BoxUser.Info info = BoxUser.createAppUser(getConnection(), CAMEL_TEST_CREATE_APP_USER_NAME);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.userId", info.getID());
        headers.put("CamelBox.notifyUser", Boolean.FALSE);
        headers.put("CamelBox.force", Boolean.FALSE);

        requestBodyAndHeaders("direct://DELETEUSER", null, headers);
        //give some time to delete task to be finished
        Thread.sleep(2000);

        Iterable<BoxUser.Info> it = BoxUser.getAllEnterpriseUsers(getConnection(), CAMEL_TEST_CREATE_APP_USER_NAME);
        int searchResults = sizeOfIterable(it);
        assertFalse(searchResults > 0, "deleteUser exists");
    }

    @Disabled
    @Test
    public void testDeleteUserEmailAlias() {
        EmailAlias emailAlias = null;
        try {
            emailAlias = testUser.addEmailAlias(CAMEL_TEST_USER_EMAIL_ALIAS);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.userId", testUser.getID());
        // parameter type is String
        headers.put("CamelBox.emailAliasId", emailAlias.getID());

        requestBodyAndHeaders("direct://DELETEUSEREMAILALIAS", null, headers);

        assertNotNull(testUser.getEmailAliases(), "deleteUserEmailAlias email aliases");
        assertEquals(0, testUser.getEmailAliases().size(), "deleteUserEmailAlias email aliases");
    }

    @Test
    public void testGetAllEnterpriseOrExternalUsers() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.filterTerm", null);
        // parameter type is String[]
        headers.put("CamelBox.fields", null);

        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBodyAndHeaders("direct://GETALLENTERPRISEOREXTERNALUSERS", null, headers);

        assertNotNull(result, "getAllEnterpriseOrExternalUsers result");
        LOG.debug("getAllEnterpriseOrExternalUsers: {}", result);
    }

    @Test
    public void testGetCurrentUser() {
        final com.box.sdk.BoxUser result = requestBody("direct://GETCURRENTUSER", testUser.getID());

        assertNotNull(result, "getCurrentUser result");
        LOG.debug("getCurrentUser: {}", result);
    }

    @Test
    public void testGetUserEmailAlias() {
        // using String message body for single parameter "userId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETUSEREMAILALIAS", testUser.getID());

        assertNotNull(result, "getUserEmailAlias result");
        LOG.debug("getUserEmailAlias: {}", result);
    }

    @Test
    public void testGetUserInfo() {
        // using String message body for single parameter "userId"
        final com.box.sdk.BoxUser.Info result = requestBody("direct://GETUSERINFO", testUser.getID());

        assertNotNull(result, "getUserInfo result");
        LOG.debug("getUserInfo: {}", result);
    }

    @Test
    public void testUpdateUserInfo() {
        //This test makes sense only with standard (OAuth) authentication, with JWT it will always fail with return code 403
        assumeFalse(jwtAuthentication, "Test has to be executed with standard authentication.");

        BoxUser.Info info = testUser.getInfo();
        info.setJobTitle(CAMEL_TEST_USER_JOB_TITLE);

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.userId", testUser.getID());
            // parameter type is com.box.sdk.BoxUser.Info
            headers.put("CamelBox.info", info);
            final com.box.sdk.BoxUser result = requestBodyAndHeaders("direct://UPDATEUSERINFO", null, headers);
            assertNotNull(result, "updateUserInfo result");
            LOG.debug("updateUserInfo: {}", result);
        } finally {
            info = testUser.getInfo();
            info.setJobTitle("");
            testUser.updateInfo(info);
        }
    }

    @Test
    public void testmMoveFolderToUser() {
        //This test makes sense only with standard (OAuth) authentication, with JWT it will always fail with return code 403
        assumeFalse(jwtAuthentication, "Test has to be executed with standard authentication.");

        String enterpriseUser1Login = (String) options.get(CAMEL_TEST_ENTERPRISE_USER_LOGIN_KEY);
        String enterpriseUser2Login = (String) options.get(CAMEL_TEST_ENTERPRISE_USER2_LOGIN_KEY);
        if (enterpriseUser1Login != null && enterpriseUser1Login.isBlank()) {
            enterpriseUser1Login = null;
        }
        if (enterpriseUser2Login != null && enterpriseUser2Login.isBlank()) {
            enterpriseUser2Login = null;
        }

        assertNotNull(enterpriseUser1Login,
                "Email for enterprise user has to be defined in test-options.properties for this test to succeed.");
        assertNotNull(enterpriseUser2Login,
                "Email for enterprise user2 has to be defined in test-options.properties for this test to succeed.");

        BoxUser.Info user1 = null;
        BoxUser.Info user2 = null;
        try {
            user1 = BoxUser.createEnterpriseUser(getConnection(),
                    enterpriseUser1Login, CAMEL_TEST_CREATE_ENTERPRISE_USER_NAME);
            user2 = BoxUser.createEnterpriseUser(getConnection(),
                    enterpriseUser2Login, CAMEL_TEST_CREATE_ENTERPRISE_USER2_NAME);

            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.userId", user1.getID());
            headers.put("CamelBox.sourceUserId", user2.getID());

            final com.box.sdk.BoxFolder.Info result = requestBodyAndHeaders("direct://MOVEFOLDERTOUSER", null, headers);
            assertNotNull(result, "moveFolderToUser result");
        } finally {
            if (user1 != null) {
                try {
                    user1.getResource().delete(false, true);
                } catch (Exception t) {
                }
            }
            if (user2 != null) {
                try {
                    user2.getResource().delete(false, true);
                } catch (Exception t) {
                }
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for addUserEmailAlias
                from("direct://ADDUSEREMAILALIAS").to("box://" + PATH_PREFIX + "/addUserEmailAlias");

                // test route for createAppUser
                from("direct://CREATEAPPUSER").to("box://" + PATH_PREFIX + "/createAppUser");

                // test route for createEnterpriseUser
                from("direct://CREATEENTERPRISEUSER").to("box://" + PATH_PREFIX + "/createEnterpriseUser");

                // test route for deleteUser
                from("direct://DELETEUSER").to("box://" + PATH_PREFIX + "/deleteUser");

                // test route for deleteUserEmailAlias
                from("direct://DELETEUSEREMAILALIAS").to("box://" + PATH_PREFIX + "/deleteUserEmailAlias");

                // test route for getAllEnterpriseOrExternalUsers
                from("direct://GETALLENTERPRISEOREXTERNALUSERS")
                        .to("box://" + PATH_PREFIX + "/getAllEnterpriseOrExternalUsers");

                // test route for getCurrentUser
                from("direct://GETCURRENTUSER").to("box://" + PATH_PREFIX + "/getCurrentUser");

                // test route for getUserEmailAlias
                from("direct://GETUSEREMAILALIAS").to("box://" + PATH_PREFIX + "/getUserEmailAlias?inBody=userId");

                // test route for getUserInfo
                from("direct://GETUSERINFO").to("box://" + PATH_PREFIX + "/getUserInfo?inBody=userId");

                // test route for updateUserInfo
                from("direct://UPDATEUSERINFO").to("box://" + PATH_PREFIX + "/updateUserInfo");

                // test route for moveFolderToUser
                from("direct://MOVEFOLDERTOUSER").to("box://" + PATH_PREFIX + "/moveFolderToUser");
            }
        };
    }

    @BeforeEach
    public void setupTest() {
        createTestUser();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestUser();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/addUserEmailAlias");
        return endpoint.getBoxConnection();
    }

    private void createTestUser() {
        testUser = getCurrentUser();
    }

    private void deleteTestUser() {
        if (testUser != null) {
            testUser = null;
        }
    }

    private BoxUser getCurrentUser() {
        return BoxUser.getCurrentUser(getConnection());
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
