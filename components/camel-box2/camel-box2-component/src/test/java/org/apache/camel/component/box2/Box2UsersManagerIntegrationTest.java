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
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;
import com.box.sdk.EmailAlias;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2UsersManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.box2.api.Box2UsersManager}
 * APIs.
 */
public class Box2UsersManagerIntegrationTest extends AbstractBox2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Box2UsersManagerIntegrationTest.class);
    private static final String PATH_PREFIX = Box2ApiCollection.getCollection()
            .getApiName(Box2UsersManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_USER_EMAIL_ALIAS = "camel@example.com";
    private static final String CAMEL_TEST_USER_JOB_TITLE = "Camel Tester";
    private static final String CAMEL_TEST_CREATE_APP_USER_NAME = "Wilma";
    private static final String CAMEL_TEST_CREATE_ENTERPRISE_USER_NAME = "fred";
    private static final String CAMEL_TEST_CREATE_ENTERPRISE_USER_LOGIN = "fred@example.com";

    private BoxUser testUser;

    @Ignore
    @Test
    public void testAddUserEmailAlias() throws Exception {
        com.box.sdk.EmailAlias result = null;
        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.userId", testUser.getID());
            // parameter type is String
            headers.put("CamelBox2.email", CAMEL_TEST_USER_EMAIL_ALIAS);
            result = requestBodyAndHeaders("direct://ADDUSEREMAILALIAS", null, headers);
            assertNotNull("addUserEmailAlias result", result);
            LOG.debug("addUserEmailAlias: " + result);
        } finally {
            if (result != null) {
                try {
                    testUser.deleteEmailAlias(result.getID());
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testCreateAppUser() throws Exception {
        com.box.sdk.BoxUser result = null;

        try {
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB

            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.name", CAMEL_TEST_CREATE_APP_USER_NAME);
            // parameter type is com.box.sdk.CreateUserParams
            headers.put("CamelBox2.params", params);

            result = requestBodyAndHeaders("direct://CREATEAPPUSER", null, headers);

            assertNotNull("createAppUser result", result);
            LOG.debug("createAppUser: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete(false, true);
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testCreateEnterpriseUser() throws Exception {
        com.box.sdk.BoxUser result = null;

        try {
            CreateUserParams params = new CreateUserParams();
            params.setSpaceAmount(1073741824); // 1 GB

            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.login", CAMEL_TEST_CREATE_ENTERPRISE_USER_LOGIN);
            // parameter type is String
            headers.put("CamelBox2.name", CAMEL_TEST_CREATE_ENTERPRISE_USER_NAME);
            // parameter type is com.box.sdk.CreateUserParams
            headers.put("CamelBox2.params", params);

            result = requestBodyAndHeaders("direct://CREATEENTERPRISEUSER", null, headers);

            assertNotNull("createEnterpriseUser result", result);
            LOG.debug("createEnterpriseUser: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete(false, true);
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testDeleteUser() throws Exception {
        BoxUser.Info info = BoxUser.createAppUser(getConnection(), CAMEL_TEST_CREATE_APP_USER_NAME);

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.userId", info.getID());
        headers.put("CamelBox2.notifyUser", Boolean.FALSE);
        headers.put("CamelBox2.force", Boolean.FALSE);

        requestBodyAndHeaders("direct://DELETEUSER", null, headers);

        Iterable<BoxUser.Info> it = BoxUser.getAllEnterpriseUsers(getConnection(), CAMEL_TEST_CREATE_APP_USER_NAME);
        int searchResults = sizeOfIterable(it);
        boolean exists = searchResults > 0 ? true : false;
        assertEquals("deleteUser exists", false, exists);
        LOG.debug("deleteUser: exists? " + exists);
    }

    @Ignore
    @Test
    public void testDeleteUserEmailAlias() throws Exception {
        EmailAlias emailAlias = null;
        try {
            emailAlias = testUser.addEmailAlias(CAMEL_TEST_USER_EMAIL_ALIAS);
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.userId", testUser.getID());
        // parameter type is String
        headers.put("CamelBox2.emailAliasId", emailAlias.getID());

        requestBodyAndHeaders("direct://DELETEUSEREMAILALIAS", null, headers);

        assertNotNull("deleteUserEmailAlias email aliases", testUser.getEmailAliases());
        assertEquals("deleteUserEmailAlias email aliases", 0, testUser.getEmailAliases().size());
    }

    @Test
    public void testGetAllEnterpriseOrExternalUsers() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox2.filterTerm", null);
        // parameter type is String[]
        headers.put("CamelBox2.fields", null);

        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBodyAndHeaders("direct://GETALLENTERPRISEOREXTERNALUSERS", null, headers);

        assertNotNull("getAllEnterpriseOrExternalUsers result", result);
        LOG.debug("getAllEnterpriseOrExternalUsers: " + result);
    }

    @Test
    public void testGetCurrentUser() throws Exception {
        final com.box.sdk.BoxUser result = requestBody("direct://GETCURRENTUSER", testUser.getID());

        assertNotNull("getCurrentUser result", result);
        LOG.debug("getCurrentUser: " + result);
    }

    @Test
    public void testGetUserEmailAlias() throws Exception {
        // using String message body for single parameter "userId"
        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBody("direct://GETUSEREMAILALIAS", testUser.getID());

        assertNotNull("getUserEmailAlias result", result);
        LOG.debug("getUserEmailAlias: " + result);
    }

    @Test
    public void testGetUserInfo() throws Exception {
        // using String message body for single parameter "userId"
        final com.box.sdk.BoxUser.Info result = requestBody("direct://GETUSERINFO", testUser.getID());

        assertNotNull("getUserInfo result", result);
        LOG.debug("getUserInfo: " + result);
    }

    @Test
    public void testUpdateUserInfo() throws Exception {
        BoxUser.Info info = testUser.getInfo();
        info.setJobTitle(CAMEL_TEST_USER_JOB_TITLE);

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox2.userId", testUser.getID());
            // parameter type is com.box.sdk.BoxUser.Info
            headers.put("CamelBox2.info", info);
            final com.box.sdk.BoxUser result = requestBodyAndHeaders("direct://UPDATEUSERINFO", null, headers);
            assertNotNull("updateUserInfo result", result);
            LOG.debug("updateUserInfo: " + result);
        } finally {
            info = testUser.getInfo();
            info.setJobTitle("");
            testUser.updateInfo(info);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for addUserEmailAlias
                from("direct://ADDUSEREMAILALIAS").to("box2://" + PATH_PREFIX + "/addUserEmailAlias");

                // test route for createAppUser
                from("direct://CREATEAPPUSER").to("box2://" + PATH_PREFIX + "/createAppUser");

                // test route for createEnterpriseUser
                from("direct://CREATEENTERPRISEUSER").to("box2://" + PATH_PREFIX + "/createEnterpriseUser");

                // test route for deleteUser
                from("direct://DELETEUSER").to("box2://" + PATH_PREFIX + "/deleteUser");

                // test route for deleteUserEmailAlias
                from("direct://DELETEUSEREMAILALIAS").to("box2://" + PATH_PREFIX + "/deleteUserEmailAlias");

                // test route for getAllEnterpriseOrExternalUsers
                from("direct://GETALLENTERPRISEOREXTERNALUSERS")
                        .to("box2://" + PATH_PREFIX + "/getAllEnterpriseOrExternalUsers");

                // test route for getCurrentUser
                from("direct://GETCURRENTUSER").to("box2://" + PATH_PREFIX + "/getCurrentUser");

                // test route for getUserEmailAlias
                from("direct://GETUSEREMAILALIAS").to("box2://" + PATH_PREFIX + "/getUserEmailAlias?inBody=userId");

                // test route for getUserInfo
                from("direct://GETUSERINFO").to("box2://" + PATH_PREFIX + "/getUserInfo?inBody=userId");

                // test route for updateUserInfo
                from("direct://UPDATEUSERINFO").to("box2://" + PATH_PREFIX + "/updateUserInfo");

            }
        };
    }

    @Before
    public void setupTest() throws Exception {
        createTestUser();
    }

    @After
    public void teardownTest() {
        deleteTestUser();
    }

    public BoxAPIConnection getConnection() {
        Box2Endpoint endpoint = (Box2Endpoint) context().getEndpoint("box2://" + PATH_PREFIX + "/addUserEmailAlias");
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
