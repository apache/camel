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
package org.apache.camel.component.box.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;
import com.box.sdk.EmailAlias;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.box.api.BoxHelper.buildBoxApiErrorMessage;

/**
 * Provides operations to manage Box users.
 */
public class BoxUsersManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxUsersManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create users manager to manage the users of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxUsersManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get current user.
     *
     * @return The current user.
     */
    public BoxUser getCurrentUser() {
        try {
            LOG.debug("Getting current user");

            return BoxUser.getCurrentUser(boxConnection);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get any managed users that match the filter term as well as any external users that match the filter term. For
     * managed users it matches any users names or emails that start with the term. For external, it only does full
     * match on email. This method is ideal to use in the case where you have a full email for a user and you don't know
     * if they're managed or external.
     *
     * @param  filterTerm - The filter term to lookup users by (login for external, login or name for managed); if
     *                    <code>null</code> all managed users are returned.
     * @param  fields     - the fields to retrieve. Leave this out for the standard fields.
     * @return            All the enterprise users or enterprise users that matches the filter.
     */
    public List<BoxUser.Info> getAllEnterpriseOrExternalUsers(String filterTerm, String... fields) {
        try {
            LOG.debug("Getting all enterprise users matching filterTerm={}", filterTerm);

            List<BoxUser.Info> users = new ArrayList<>();
            Iterable<BoxUser.Info> iterable;
            if (filterTerm == null) {
                iterable = BoxUser.getAllEnterpriseUsers(boxConnection);
            } else {
                iterable = BoxUser.getAllEnterpriseUsers(boxConnection, filterTerm, fields);
            }
            for (BoxUser.Info info : iterable) {
                users.add(info);
            }
            return users;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Provision a new user in an enterprise with additional user information.
     *
     * @param  login  - the email address the user will use to login.
     * @param  name   - the name of the user.
     * @param  params - additional user information.
     * @return        All the enterprise users or enterprise users that matches the filter.
     */
    public BoxUser createEnterpriseUser(String login, String name, CreateUserParams params) {
        try {
            LOG.debug("Creating enterprise user with login={} name={}", login, name);

            BoxHelper.notNull(login, BoxHelper.LOGIN);
            BoxHelper.notNull(name, BoxHelper.NAME);

            if (params != null) {
                return BoxUser.createEnterpriseUser(boxConnection, login, name, params).getResource();
            } else {
                return BoxUser.createEnterpriseUser(boxConnection, login, name).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Provision a new app user in an enterprise with additional user information using Box Developer Edition.
     *
     * @param  name   - the name of the user.
     * @param  params - additional user information.
     * @return        All the enterprise users or enterprise users that matches the filter.
     */
    public BoxUser createAppUser(String name, CreateUserParams params) {
        try {
            LOG.debug("Creating app user with name={}", name);
            BoxHelper.notNull(name, BoxHelper.NAME);

            if (params != null) {
                return BoxUser.createAppUser(boxConnection, name, params).getResource();
            } else {
                return BoxUser.createAppUser(boxConnection, name).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get user information.
     *
     * @param  userId - the id of user.
     * @return        The user information.
     */
    public BoxUser.Info getUserInfo(String userId) {
        try {
            LOG.debug("Getting info for user(id={})", userId);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);

            BoxUser user = new BoxUser(boxConnection, userId);

            return user.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Update user information.
     *
     * @param  userId - the id of user to update.
     * @param  info   - the updated information
     * @return        The updated user.
     */
    public BoxUser updateUserInfo(String userId, BoxUser.Info info) {
        try {
            LOG.debug("Updating info for user(id={})", userId);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);
            BoxHelper.notNull(info, BoxHelper.INFO);

            BoxUser user = new BoxUser(boxConnection, userId);
            user.updateInfo(info);
            return user;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Delete user from an enterprise account.
     *
     * @param userId     - the id of user to delete.
     * @param notifyUser - whether or not to send an email notification to the user that their account has been deleted.
     * @param force      - whether or not this user should be deleted even if they still own files.
     */
    public void deleteUser(String userId, boolean notifyUser, boolean force) {
        try {
            LOG.debug("Deleting user(id={}) notifyUser={} force={}", userId, notifyUser, force);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);

            BoxUser file = new BoxUser(boxConnection, userId);
            file.delete(notifyUser, force);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Add a new email alias to user's account.
     *
     * @param  userId - the id of user.
     * @param  email  - the email address to add as an alias.
     * @return        The newly created email alias.
     */
    public EmailAlias addUserEmailAlias(String userId, String email) {
        try {
            LOG.debug("Adding email alias '{}' to user(id={})", email, userId);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);
            BoxHelper.notNull(email, BoxHelper.EMAIL);

            BoxUser user = new BoxUser(boxConnection, userId);

            return user.addEmailAlias(email);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get a collection of all the email aliases for user.
     *
     * @param  userId - the id of user.
     * @return        A collection of all the email aliases for user.
     */
    public Collection<EmailAlias> getUserEmailAlias(String userId) {
        try {
            LOG.debug("Get email aliases for user(id={})", userId);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);

            BoxUser user = new BoxUser(boxConnection, userId);

            return user.getEmailAliases();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Delete an email alias from user's account.
     *
     * @param userId       - the id of user.
     * @param emailAliasId - the id of the email alias to delete.
     */
    public void deleteUserEmailAlias(String userId, String emailAliasId) {
        try {
            LOG.debug("Deleting email_alias({}) for user(id={})", emailAliasId, userId);
            BoxHelper.notNull(userId, BoxHelper.USER_ID);
            BoxHelper.notNull(emailAliasId, BoxHelper.EMAIL_ALIAS_ID);

            BoxUser user = new BoxUser(boxConnection, userId);

            user.deleteEmailAlias(emailAliasId);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Move root folder for specified user to current user.
     *
     * @param userId       - the id of user.
     * @param sourceUserId - the user id of the user whose files will be the source for this operation.
     */
    public BoxFolder.Info moveFolderToUser(String userId, String sourceUserId) {
        try {
            LOG.debug("Moving root folder for user(id={}) to user(id={})", sourceUserId, userId);

            BoxHelper.notNull(userId, BoxHelper.USER_ID);
            BoxHelper.notNull(sourceUserId, BoxHelper.SOURCE_USER_ID);

            BoxUser user = new BoxUser(boxConnection, sourceUserId);

            return user.transferContent(userId);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }
}
