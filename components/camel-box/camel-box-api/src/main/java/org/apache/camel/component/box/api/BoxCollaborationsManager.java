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

import java.util.Collection;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxCollaborator;
import com.box.sdk.BoxFolder;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.box.api.BoxHelper.buildBoxApiErrorMessage;

/**
 * Provides operations to manage Box collaborations.
 */
public class BoxCollaborationsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxCollaborationsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create collaborations manager to manage the comments of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxCollaborationsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get information about all of the collaborations for folder.
     *
     * @param  folderId - the id of folder to get collaborations information on.
     *
     * @return          The collection of collaboration information for folder.
     */
    public Collection<BoxCollaboration.Info> getFolderCollaborations(String folderId) {
        try {
            LOG.debug("Getting collaborations for folder(id={})", folderId);
            BoxHelper.notNull(folderId, BoxHelper.FOLDER_ID);
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.getCollaborations();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Add a collaboration to this folder.
     *
     * @param  folderId     - the id of folder to add collaboration to.
     * @param  collaborator - the collaborator to add.
     * @param  role         - the role of the collaborator.
     *
     * @return              The new collaboration.
     */
    public BoxCollaboration addFolderCollaboration(
            String folderId, BoxCollaborator collaborator,
            BoxCollaboration.Role role) {
        try {
            BoxHelper.notNull(folderId, BoxHelper.FOLDER_ID);
            BoxHelper.notNull(collaborator, BoxHelper.COLLABORATOR);
            LOG.debug("Creating  collaborations for folder(id={}) with collaborator({})", folderId, collaborator.getID());
            BoxHelper.notNull(role, BoxHelper.ROLE);
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.collaborate(collaborator, role).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Add a collaboration to this folder. An email will be sent to the collaborator if they don't already have a Box
     * account.
     *
     * @param  folderId - the id of folder to add collaboration to.
     * @param  email    - the email address of the collaborator to add.
     * @param  role     - the role of the collaborator.
     *
     * @return          The new collaboration.
     */
    public BoxCollaboration addFolderCollaborationByEmail(String folderId, String email, BoxCollaboration.Role role) {
        try {
            LOG.debug("Creating  collaborations for folder(id={}) with collaborator({})", folderId, email);
            BoxHelper.notNull(folderId, BoxHelper.FOLDER_ID);
            BoxHelper.notNull(email, BoxHelper.EMAIL);
            BoxHelper.notNull(role, BoxHelper.ROLE);

            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.collaborate(email, role).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get collaboration information.
     *
     * @param  collaborationId - the id of collaboration.
     * @return                 The collaboration information.
     */
    public BoxCollaboration.Info getCollaborationInfo(String collaborationId) {
        try {
            LOG.debug("Getting info for collaboration(id={})", collaborationId);
            BoxHelper.notNull(collaborationId, BoxHelper.COLLABORATION_ID);

            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);

            return collaboration.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Update collaboration information.
     *
     * @param  collaborationId - the id of collaboration.
     * @param  info            collaboration information to update.
     * @return                 The collaboration with updated information.
     */
    public BoxCollaboration updateCollaborationInfo(String collaborationId, BoxCollaboration.Info info) {
        try {
            LOG.debug("Updating info for collaboration(id={})", collaborationId);
            BoxHelper.notNull(collaborationId, BoxHelper.COLLABORATION_ID);

            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);

            collaboration.updateInfo(info);
            return collaboration;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Delete collaboration.
     *
     * @param collaborationId - the id of comment to change.
     */
    public void deleteCollaboration(String collaborationId) {
        try {
            LOG.debug("Deleting collaboration(id={})", collaborationId);
            BoxHelper.notNull(collaborationId, BoxHelper.COLLABORATION_ID);
            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);
            collaboration.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get all pending collaboration invites for the current user.
     *
     * @return A collection of pending collaboration information.
     */
    public Collection<BoxCollaboration.Info> getPendingCollaborations() {
        try {
            return BoxCollaboration.getPendingCollaborations(boxConnection);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

}
