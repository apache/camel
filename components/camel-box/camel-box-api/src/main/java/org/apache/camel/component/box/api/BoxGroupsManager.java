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
package org.apache.camel.component.box.api;

import java.util.ArrayList;
import java.util.Collection;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxGroup;
import com.box.sdk.BoxGroupMembership;
import com.box.sdk.BoxUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Box Groups Manager
 * 
 * <p>
 * Provides operations to manage Box groups.
 * 
 * 
 *
 */
public class BoxGroupsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxGroupsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create groups manager to manage the users of Box connection's
     * authenticated user.
     * 
     * @param boxConnection
     *            - Box connection to authenticated user account.
     */
    public BoxGroupsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get all the groups in the enterprise.
     * 
     * @return Collection containing all the enterprise's groups.
     */
    public Collection<BoxGroup> getAllGroups() {
        try {
            LOG.debug("Getting all groups");

            Collection<BoxGroup> groups = new ArrayList<>();
            for (BoxGroup.Info groupInfo : BoxGroup.getAllGroups(boxConnection)) {
                groups.add(groupInfo.getResource());
            }
            return groups;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create a new group with a specified name and optional additional parameters.
     * Optional parameters may be null.
     * 
     * @param name
     *            - the name of the new group.
     * @param provenance
     *            - the provenance of the new group.
     * @param externalSyncIdentifier
     *            - the external_sync_identifier of the new group.
     * @param description
     *            - the description of the new group.
     * @param invitabilityLevel
     *            - the invitibility_level of the new group.
     * @param memberViewabilityLevel
     *            - the member_viewability_level of the new group.
     * @return The newly created group.
     */
    public BoxGroup createGroup(String name, String provenance,
        String externalSyncIdentifier, String description,
        String invitabilityLevel, String memberViewabilityLevel) {

        try {
            LOG.debug("Creating group name={}", name);
            if (name == null) {
                throw new IllegalArgumentException("Parameter 'name' can not be null");
            }

            return BoxGroup.createGroup(boxConnection, name, provenance, externalSyncIdentifier, description,
                invitabilityLevel, memberViewabilityLevel).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete group.
     * 
     * @param groupId
     *            - the id of group to delete.
     */
    public void deleteGroup(String groupId) {
        try {
            LOG.debug("Deleting group({})", groupId);
            if (groupId == null) {
                throw new IllegalArgumentException("Parameter 'groupId' can not be null");
            }

            BoxGroup group = new BoxGroup(boxConnection, groupId);
            group.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get group information.
     * 
     * @param groupId
     *            - the id of group.
     * @return The group information.
     */
    public BoxGroup.Info getGroupInfo(String groupId) {
        try {
            LOG.debug("Getting info for group(id={})", groupId);
            if (groupId == null) {
                throw new IllegalArgumentException("Parameter 'groupId' can not be null");
            }

            BoxGroup group = new BoxGroup(boxConnection, groupId);

            return group.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Update group information.
     *
     * @param groupId
     *            - the id of group to update.
     * @param groupInfo
     *            - the updated information
     * @return The updated group.
     */
    public BoxGroup updateGroupInfo(String groupId, BoxGroup.Info groupInfo) {
        try {
            LOG.debug("Updating info for group(id={})", groupId);
            if (groupId == null) {
                throw new IllegalArgumentException("Parameter 'groupId' can not be null");
            }
            if (groupInfo == null) {
                throw new IllegalArgumentException("Parameter 'groupInfo' can not be null");
            }

            BoxGroup group = new BoxGroup(boxConnection, groupId);
            group.updateInfo(groupInfo);
            return group;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get information about all of the group memberships for this group.
     * 
     * @param groupId
     *            - the id of group.
     * @return The group information.
     */
    public Collection<BoxGroupMembership.Info> getGroupMemberships(String groupId) {
        try {
            LOG.debug("Getting information about all memberships for group(id={})", groupId);
            if (groupId == null) {
                throw new IllegalArgumentException("Parameter 'groupId' can not be null");
            }

            BoxGroup group = new BoxGroup(boxConnection, groupId);

            return group.getMemberships();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Add a member to group with the specified role.
     * 
     * @param groupId
     *            - the id of group.
     * @param userId
     *            - the id of user to be added to group.
     * @param role
     *            - the role of the user in this group. Can be <code>null</code>
     *            to assign the default role.
     * @return The group information.
     */
    public BoxGroupMembership addGroupMembership(String groupId, String userId, BoxGroupMembership.Role role) {
        try {
            LOG.debug("Adding user(id=" + userId + ") as member to group(id=" + groupId
                    + (role == null ? ")" : ") with role=" + role.name()));
            if (groupId == null) {
                throw new IllegalArgumentException("Parameter 'groupId' can not be null");
            }
            if (userId == null) {
                throw new IllegalArgumentException("Parameter 'userId' can not be null");
            }

            BoxGroup group = new BoxGroup(boxConnection, groupId);
            BoxUser user = new BoxUser(boxConnection, userId);

            return group.addMembership(user, role).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete group membership.
     * 
     * @param groupMembershipId
     *            - the id of group membership to delete.
     */
    public void deleteGroupMembership(String groupMembershipId) {
        try {
            LOG.debug("Deleting groupMembership(id={})", groupMembershipId);
            if (groupMembershipId == null) {
                throw new IllegalArgumentException("Parameter 'groupMemebershipId' can not be null");
            }

            BoxGroupMembership groupMembership = new BoxGroupMembership(boxConnection, groupMembershipId);

            groupMembership.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get group membership information.
     * 
     * @param groupMemebershipId
     *            - the id of group membership.
     * @return The group information.
     */
    public BoxGroupMembership.Info getGroupMembershipInfo(String groupMemebershipId) {
        try {
            LOG.debug("Getting info for groupMemebership(id={})", groupMemebershipId);
            if (groupMemebershipId == null) {
                throw new IllegalArgumentException("Parameter 'groupMemebershipId' can not be null");
            }

            BoxGroupMembership group = new BoxGroupMembership(boxConnection, groupMemebershipId);

            return group.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Update group membership information.
     * 
     * @param groupMembershipId
     *            - the id of group membership to update.
     * @param info
     *            - the updated information.
     * @return The group information.
     */
    public BoxGroupMembership updateGroupMembershipInfo(String groupMembershipId, BoxGroupMembership.Info info) {
        try {
            LOG.debug("Updating info for groupMembership(id={})", groupMembershipId);
            if (groupMembershipId == null) {
                throw new IllegalArgumentException("Parameter 'groupMembershipId' can not be null");
            }
            if (info == null) {
                throw new IllegalArgumentException("Parameter 'info' can not be null");
            }

            BoxGroupMembership groupMembership = new BoxGroupMembership(boxConnection, groupMembershipId);

            groupMembership.updateInfo(info);
            return groupMembership;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }
}
