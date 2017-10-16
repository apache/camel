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
package org.apache.camel.component.box;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxGroup;
import com.box.sdk.BoxGroupMembership;
import com.box.sdk.BoxUser;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxGroupsManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxGroupsManagerApiMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link BoxGroupsManager} APIs.
 */
public class BoxGroupsManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxGroupsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
        .getApiName(BoxGroupsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_GROUP_DESCRIPTION = "CamelTestGroupDescription";
    private static final String CAMEL_TEST_GROUP_NAME = "CamelTestGroup";
    private static final String CAMEL_TEST_CREATE_GROUP_NAME = "CamelTestCreateGroup";

    private BoxGroup testGroup;
    private BoxUser testUser;

    @Test
    public void testAddGroupMembership() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox.groupId", testGroup.getID());
        // parameter type is String
        headers.put("CamelBox.userId", testUser.getID());
        // parameter type is com.box.sdk.BoxGroupMembership.Role
        headers.put("CamelBox.role", null);

        final com.box.sdk.BoxGroupMembership result = requestBodyAndHeaders("direct://ADDGROUPMEMBERSHIP", null,
            headers);

        assertNotNull("addGroupMembership result", result);
        LOG.debug("addGroupMembership: " + result);
    }

    @Test
    public void testCreateGroup() throws Exception {
        com.box.sdk.BoxGroup result = null;

        try {
            // using String message body for single parameter "name"
            result = requestBody("direct://CREATEGROUP", CAMEL_TEST_CREATE_GROUP_NAME);
            assertNotNull("createGroup result", result);
            assertEquals(CAMEL_TEST_CREATE_GROUP_NAME, result.getInfo().getName());
            LOG.debug("createGroup: " + result);
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
    public void testDeleteGroup() throws Exception {
        // using String message body for single parameter "groupId"
        requestBody("direct://DELETEGROUP", testGroup.getID());

        testGroup = null;

        Iterable<BoxGroup.Info> it = BoxGroup.getAllGroups(getConnection());
        int searchResults = sizeOfIterable(it);
        boolean exists = searchResults > 0 ? true : false;
        assertEquals("deleteGroup exists", false, exists);
        LOG.debug("deleteGroup: exists? " + exists);
    }

    @Test
    public void testDeleteGroupMembership() throws Exception {
        BoxGroupMembership.Info info = testGroup.addMembership(testUser, BoxGroupMembership.Role.MEMBER);

        // using String message body for single parameter "groupMembershipId"
        requestBody("direct://DELETEGROUPMEMBERSHIP", info.getID());

        Collection<BoxGroupMembership.Info> memberships = testGroup.getMemberships();
        assertNotNull("deleteGroupMemberships memberships", memberships);
        assertEquals("deleteGroupMemberships memberships exists", 0, memberships.size());
    }

    @Test
    public void testGetAllGroups() throws Exception {
        @SuppressWarnings("rawtypes") final java.util.Collection result = requestBody("direct://GETALLGROUPS", null);

        assertNotNull("getAllGroups result", result);
        LOG.debug("getAllGroups: " + result);
    }

    @Test
    public void testGetGroupInfo() throws Exception {
        // using String message body for single parameter "groupId"
        final com.box.sdk.BoxGroup.Info result = requestBody("direct://GETGROUPINFO", testGroup.getID());

        assertNotNull("getGroupInfo result", result);
        LOG.debug("getGroupInfo: " + result);
    }

    @Test
    public void testUpdateGroupInfo() throws Exception {
        BoxGroup.Info info = testGroup.getInfo();
        info.setDescription(CAMEL_TEST_GROUP_DESCRIPTION);

        try {
            final Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelBox.groupId", testGroup.getID());
            // parameter type is com.box.sdk.BoxGroup.Info
            headers.put("CamelBox.groupInfo", info);
            final com.box.sdk.BoxGroup result = requestBodyAndHeaders("direct://UPDATEGROUPINFO", null, headers);
            assertNotNull("updateGroupInfo result", result);
            LOG.debug("updateGroupInfo: " + result);
        } finally {
            info = testGroup.getInfo();
            info.setDescription("");
            testGroup.updateInfo(info);
        }
    }

    @Test
    public void testGetGroupMembershipInfo() throws Exception {
        BoxGroupMembership.Info info = testGroup.addMembership(testUser, BoxGroupMembership.Role.MEMBER);

        // using String message body for single parameter "groupMemebershipId"
        final com.box.sdk.BoxGroupMembership.Info result = requestBody("direct://GETGROUPMEMBERSHIPINFO", info.getID());

        assertNotNull("getGroupMembershipInfo result", result);
        LOG.debug("getGroupMembershipInfo: " + result);
    }

    @Test
    public void testGetGroupMemberships() throws Exception {
        // using String message body for single parameter "groupId"
        @SuppressWarnings("rawtypes") final java.util.Collection result = requestBody("direct://GETGROUPMEMBERSHIPS", testGroup.getID());

        assertNotNull("getGroupMemberships result", result);
        LOG.debug("getGroupMemberships: " + result);
    }

    @Test
    public void testUpdateGroupMembershipInfo() throws Exception {
        BoxGroupMembership.Info info = testGroup.addMembership(testUser, BoxGroupMembership.Role.MEMBER);
        info.setRole(BoxGroupMembership.Role.ADMIN);

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox.groupMemebershipId", info.getID());
        // parameter type is com.box.sdk.BoxGroupMembership.Info
        headers.put("CamelBox.info", info);

        final com.box.sdk.BoxGroupMembership result = requestBodyAndHeaders("direct://UPDATEGROUPMEMBERSHIPINFO", null,
            headers);

        assertNotNull("updateGroupMembershipInfo result", result);
        LOG.debug("updateGroupMembershipInfo: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for addGroupMembership
                from("direct://ADDGROUPMEMBERSHIP").to("box://" + PATH_PREFIX + "/addGroupMembership");

                // test route for createGroup
                from("direct://CREATEGROUP").to("box://" + PATH_PREFIX + "/createGroup?inBody=name");

                // test route for deleteGroup
                from("direct://DELETEGROUP").to("box://" + PATH_PREFIX + "/deleteGroup?inBody=groupId");

                // test route for deleteGroupMembership
                from("direct://DELETEGROUPMEMBERSHIP")
                    .to("box://" + PATH_PREFIX + "/deleteGroupMembership?inBody=groupMembershipId");

                // test route for getAllGroups
                from("direct://GETALLGROUPS").to("box://" + PATH_PREFIX + "/getAllGroups");

                // test route for getGroupInfo
                from("direct://GETGROUPINFO").to("box://" + PATH_PREFIX + "/getGroupInfo?inBody=groupId");

                // test route for getGroupMembershipInfo
                from("direct://GETGROUPMEMBERSHIPINFO")
                    .to("box://" + PATH_PREFIX + "/getGroupMembershipInfo?inBody=groupMemebershipId");

                // test route for getGroupMemberships
                from("direct://GETGROUPMEMBERSHIPS")
                    .to("box://" + PATH_PREFIX + "/getGroupMemberships?inBody=groupId");

                // test route for updateGroupMembershipInfo
                from("direct://UPDATEGROUPMEMBERSHIPINFO").to("box://" + PATH_PREFIX + "/updateGroupMembershipInfo");

            }
        };
    }

    @Before
    public void setupTest() throws Exception {
        createTestGroup();
        createTestUser();
    }

    @After
    public void teardownTest() {
        deleteTestGroup();
        deleteTestUser();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/addGroupMembership");
        return endpoint.getBoxConnection();
    }

    private void createTestGroup() {
        testGroup = BoxGroup.createGroup(getConnection(), CAMEL_TEST_GROUP_NAME).getResource();
    }

    private void deleteTestGroup() {
        if (testGroup != null) {
            try {
                testGroup.delete();
            } catch (Throwable t) {
            }
            testGroup = null;
        }
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
