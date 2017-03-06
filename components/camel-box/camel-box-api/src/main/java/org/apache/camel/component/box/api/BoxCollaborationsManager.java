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

import java.util.Collection;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxCollaborator;
import com.box.sdk.BoxFolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Box Collaborations Manager
 * <p>
 * Provides operations to manage Box collaborations.
 *
 */
public class BoxCollaborationsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxCollaborationsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create collaborations manager to manage the comments of Box connection's
     * authenticated user.
     * 
     * @param boxConnection
     *            - Box connection to authenticated user account.
     */
    public BoxCollaborationsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get information about all of the collaborations for folder.
     * 
     * @param folderId
     *            - the id of folder to get collaborations information on.
     * 
     * @return The collection of collaboration information for folder.
     */
    public Collection<BoxCollaboration.Info> getFolderCollaborations(String folderId) {
        try {
            LOG.debug("Getting collaborations for folder(id=" + folderId + ")");
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.getCollaborations();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Add a collaboration to this folder.
     * 
     * @param folderId
     *            - the id of folder to add collaboration to.
     * @param collaborator
     *            - the collaborator to add.
     * @param role
     *            - the role of the collaborator.
     * 
     * @return The new collaboration.
     */
    @SuppressWarnings("unused") // compiler for some reason thinks 'if
                                // (collaborator == null)' clause is dead code.
    public BoxCollaboration addFolderCollaboration(String folderId, BoxCollaborator collaborator,
            BoxCollaboration.Role role) {
        try {
            LOG.debug("Creating  collaborations for folder(id=" + folderId + ") with collaborator("
                    + collaborator.getID() + ")");
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (collaborator == null) {
                throw new IllegalArgumentException("Parameter 'collaborator' can not be null");
            }
            if (role == null) {
                throw new IllegalArgumentException("Parameter 'role' can not be null");
            }

            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.collaborate(collaborator, role).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Add a collaboration to this folder. An email will be sent to the
     * collaborator if they don't already have a Box account.
     * 
     * @param folderId
     *            - the id of folder to add collaboration to.
     * @param email
     *            - the email address of the collaborator to add.
     * @param role
     *            - the role of the collaborator.
     * 
     * @return The new collaboration.
     */
    public BoxCollaboration addFolderCollaborationByEmail(String folderId, String email, BoxCollaboration.Role role) {
        try {
            LOG.debug("Creating  collaborations for folder(id=" + folderId + ") with collaborator(" + email + ")");
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (email == null) {
                throw new IllegalArgumentException("Parameter 'email' can not be null");
            }
            if (role == null) {
                throw new IllegalArgumentException("Parameter 'role' can not be null");
            }

            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            return folder.collaborate(email, role).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get collaboration information.
     * 
     * @param collaborationId
     *            - the id of collaboration.
     * @return The collaboration information.
     */
    public BoxCollaboration.Info getCollaborationInfo(String collaborationId) {
        try {
            LOG.debug("Getting info for collaboration(id=" + collaborationId + ")");
            if (collaborationId == null) {
                throw new IllegalArgumentException("Parameter 'collaborationId' can not be null");
            }

            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);

            return collaboration.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Update collaboration information.
     * 
     * @param collaborationId
     *            - the id of collaboration.
     * @return The collaboration with updated information.
     */
    public BoxCollaboration updateCollaborationInfo(String collaborationId, BoxCollaboration.Info info) {
        try {
            LOG.debug("Updating info for collaboration(id=" + collaborationId + ")");
            if (collaborationId == null) {
                throw new IllegalArgumentException("Parameter 'collaborationId' can not be null");
            }

            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);

            collaboration.updateInfo(info);
            return collaboration;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete collaboration.
     * 
     * @param collaborationId
     *            - the id of comment to change.
     * @return The comment with changed message.
     */
    public void deleteCollaboration(String collaborationId) {
        try {
            LOG.debug("Deleting collaboration(id=" + collaborationId + ")");
            if (collaborationId == null) {
                throw new IllegalArgumentException("Parameter 'collaborationId' can not be null");
            }
            BoxCollaboration collaboration = new BoxCollaboration(boxConnection, collaborationId);
            collaboration.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
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
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

}
