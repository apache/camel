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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxSharedLink;
import com.box.sdk.sharedlink.BoxSharedLinkRequest;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides operations to manage Box folders.
 */
public class BoxFoldersManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxFoldersManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create folder manager to manage folders of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxFoldersManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Return the root folder of authenticated user.
     *
     * @return The root folder of authenticated user.
     */
    public BoxFolder getRootFolder() {
        try {
            LOG.debug("Getting root folder");
            return BoxFolder.getRootFolder(boxConnection);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Return the Box folder referenced by <code>path</code>.
     *
     * @param  path - Sequence of Box folder names from root folder to returned folder.
     *
     * @return      The Box folder referenced by <code>path</code> or <code>null</code> if folder is not found.
     */
    public BoxFolder getFolder(String... path) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting folder at path={}", Arrays.toString(path));
            }

            BoxFolder folder = BoxFolder.getRootFolder(boxConnection);
            if (path == null || path.length == 0) {
                // Return root folder if path is null or empty.
                return folder;
            }

            searchPath: for (int folderIndex = 0; folderIndex < path.length; folderIndex++) {
                for (BoxItem.Info itemInfo : folder) {
                    if (itemInfo instanceof BoxFolder.Info && itemInfo.getName().equals(path[folderIndex])) {
                        folder = (BoxFolder) itemInfo.getResource();
                        continue searchPath;
                    }
                }
                // Failed to find named folder in path: return null
                return null;
            }
            return folder;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Returns a specific range of child items in folder and specifies which fields of each item to retrieve.
     *
     * @param  folderId - the id of folder.
     * @param  offset   - the index of first child item to retrieve; if <code>null</code> all child items are retrieved.
     * @param  limit    - the maximum number of children to retrieve after the offset; if <code>null</code> all child
     *                  items are retrieved.
     * @param  fields   - the item fields to retrieve for each child item; if <code>null</code> all item fields are
     *                  retrieved.
     * @return          The Items in folder
     */
    public Collection<BoxItem.Info> getFolderItems(String folderId, Long offset, Long limit, String... fields) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting folder items in folder(id={}) at offset={} and limit={} with fields={}",
                        folderId, offset, limit, Arrays.toString(fields));
            }
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            if (fields == null) {
                fields = new String[0];
            }
            if (offset != null && limit != null) {
                return folder.getChildrenRange(offset, limit, fields);
            } else {
                Collection<BoxItem.Info> folderItems = new ArrayList<>();
                Iterable<BoxItem.Info> iterable;
                if (fields.length > 0) {
                    iterable = folder.getChildren(fields);
                } else {
                    iterable = folder.getChildren();
                }
                for (BoxItem.Info itemInfo : iterable) {
                    folderItems.add(itemInfo);
                }

                return folderItems;
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create a folder in parent folder with given <code>parentFolderId</code>.
     *
     * @param  parentFolderId - the id of parent folder.
     * @param  folderName     the name of created folder.
     * @return                The created folder.
     */
    public BoxFolder createFolder(String parentFolderId, String folderName) {
        try {
            LOG.debug("Creating folder with name '{}' in parent_folder(id={})", folderName, parentFolderId);
            if (parentFolderId == null) {
                throw new IllegalArgumentException("Parameter 'parentFolderId' can not be null");
            }
            if (folderName == null) {
                throw new IllegalArgumentException("Parameter 'folderName' can not be null");
            }
            BoxFolder parentFolder = new BoxFolder(boxConnection, parentFolderId);
            return parentFolder.createFolder(folderName).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create a folder specified by path from parent folder with given <code>parentFolderId</code>, creating
     * intermediate directories as required.
     *
     * @param  parentFolderId - the id of parent folder.
     * @param  path           - Sequence of Box folder names from parent folder to returned folder.
     * @return                The last folder in path, no fault will be thrown if it already exists.
     */
    public BoxFolder createFolder(String parentFolderId, String... path) {
        try {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating folder with path '{}' in parent_folder(id={})", Arrays.toString(path), parentFolderId);
            }

            if (parentFolderId == null) {
                throw new IllegalArgumentException("Parameter 'parentFolderId' can not be null");
            }
            if (path == null) {
                throw new IllegalArgumentException("Paramerer 'path' can not be null");
            }
            BoxFolder folder = new BoxFolder(boxConnection, parentFolderId);
            searchPath: for (int folderIndex = 0; folderIndex < path.length; folderIndex++) {
                for (BoxItem.Info itemInfo : folder) {
                    if (itemInfo instanceof BoxFolder.Info && itemInfo.getName().equals(path[folderIndex])) {
                        folder = (BoxFolder) itemInfo.getResource();
                        continue searchPath;
                    }
                }
                folder = folder.createFolder(path[folderIndex]).getResource();
            }
            return folder;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Copy folder to destination folder while optionally giving it a new name.
     *
     * @param  folderId            - the id of folder to copy.
     * @param  destinationFolderId - the id of the destination folder.
     * @param  newName             - the new name for copied folder; if <code>newName</code> is <code>null</code>, the
     *                             copied folder has same name as the original.
     * @return                     The copied folder.
     */
    public BoxFolder copyFolder(String folderId, String destinationFolderId, String newName) {
        try {
            LOG.debug("Copying folder(id={}) to destination_folder(id={}) {}",
                    folderId, destinationFolderId, newName == null ? "" : " with new name '" + newName + "'");
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (destinationFolderId == null) {
                throw new IllegalArgumentException("Parameter 'destinationFolderId' can not be null");
            }
            BoxFolder folderToCopy = new BoxFolder(boxConnection, folderId);
            BoxFolder destinationFolder = new BoxFolder(boxConnection, destinationFolderId);
            if (newName == null) {
                return folderToCopy.copy(destinationFolder).getResource();
            } else {
                return folderToCopy.copy(destinationFolder, newName).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Move folder to destination folder while optionally giving it a new name.
     *
     * @param  folderId            - the id of folder to move.
     * @param  destinationFolderId - the id of the destination folder.
     * @param  newName             - the new name of moved folder; if <code>newName</code> is <code>null</code>, the
     *                             moved folder has same name as the original.
     * @return                     The moved folder.
     */
    public BoxFolder moveFolder(String folderId, String destinationFolderId, String newName) {
        try {
            LOG.debug("Moving folder(id={}) to destination_folder(id={}) {}",
                    folderId, destinationFolderId, newName == null ? "" : " with new name '" + newName + "'");
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (destinationFolderId == null) {
                throw new IllegalArgumentException("Parameter 'destinationFolderId' can not be null");
            }
            BoxFolder folderToMove = new BoxFolder(boxConnection, folderId);
            BoxFolder destinationFolder = new BoxFolder(boxConnection, destinationFolderId);
            if (newName == null) {
                return (BoxFolder) folderToMove.move(destinationFolder).getResource();
            } else {
                return (BoxFolder) folderToMove.move(destinationFolder, newName).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Rename folder giving it the name <code>newName</code>
     *
     * @param  folderId      - the id of folder to rename.
     * @param  newFolderName - the new name of folder.
     * @return               The renamed folder.
     */
    public BoxFolder renameFolder(String folderId, String newFolderName) {
        try {
            LOG.debug("Renaming folder(id={}}) to '{}'", folderId, newFolderName);
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (newFolderName == null) {
                throw new IllegalArgumentException("Parameter 'newFolderName' can not be null");
            }
            BoxFolder folderToRename = new BoxFolder(boxConnection, folderId);
            folderToRename.rename(newFolderName);
            return folderToRename;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete folder.
     *
     * @param folderId - the id of folder to delete.
     */
    public void deleteFolder(String folderId) {
        try {
            LOG.debug("Deleting folder(id={})", folderId);
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            folder.delete(true);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get folder information.
     *
     * @param  folderId - the id of folder.
     * @param  fields   - the information fields to retrieve; if <code>null</code> all information fields are retrieved.
     * @return          The folder information.
     */
    public BoxFolder.Info getFolderInfo(String folderId, String... fields) {
        try {
            LOG.debug("Getting info for folder(id={})", folderId);
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }

            BoxFolder folder = new BoxFolder(boxConnection, folderId);

            if (fields == null || fields.length == 0) {
                return folder.getInfo();
            } else {
                return folder.getInfo(fields);
            }
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Update folder information.
     *
     * @param  folderId - the id of folder to update.
     * @param  info     - the updated information
     * @return          The updated folder.
     */
    public BoxFolder updateFolderInfo(String folderId, BoxFolder.Info info) {
        try {
            LOG.debug("Updating info for folder(id={})", folderId);
            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (info == null) {
                throw new IllegalArgumentException("Parameter 'info' can not be null");
            }
            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            folder.updateInfo(info);
            return folder;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create a shared link to folder.
     *
     * @param  folderId    - the id of folder to create shared link on.
     * @param  access      - the access level of the shared link.
     * @param  unshareDate - the date and time at which time the created shared link will expire; if
     *                     <code>unsharedDate</code> is <code>null</code> then a non-expiring link is created.
     * @param  permissions - the permissions of the created link; if <code>permissions</code> is <code>null</code> then
     *                     the created shared link is create with default permissions.
     * @return             The created shared link.
     */
    public BoxSharedLink createFolderSharedLink(
            String folderId, BoxSharedLink.Access access, Date unshareDate,
            BoxSharedLink.Permissions permissions) {
        try {
            LOG.debug("Creating shared link for folder(id={}) with access={} {}",
                    folderId, access, unshareDate == null
                            ? ""
                            : " unsharedDate=" + DateFormat.getDateTimeInstance().format(unshareDate)
                              + " permissions=" + permissions);

            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (access == null) {
                throw new IllegalArgumentException("Parameter 'access' can not be null");
            }

            BoxFolder folder = new BoxFolder(boxConnection, folderId);
            BoxSharedLinkRequest request = new BoxSharedLinkRequest();
            request.access(access).unsharedDate(unshareDate)
                    .permissions(permissions.getCanDownload(), permissions.getCanPreview(), permissions.getCanEdit());
            return folder.createSharedLink(request);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API returned the error code %d%n%n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

}
