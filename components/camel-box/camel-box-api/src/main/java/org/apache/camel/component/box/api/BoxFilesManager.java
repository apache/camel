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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFileVersion;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxSharedLink;
import com.box.sdk.FileUploadParams;
import com.box.sdk.Metadata;
import com.box.sdk.ProgressListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Box Files Manager
 * 
 * <p>
 * Provides operations to manage Box files.
 * 
 * 
 *
 */
public class BoxFilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxFilesManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create files manager to manage the files of Box connection's
     * authenticated user.
     * 
     * @param boxConnection
     *            - Box connection to authenticated user account.
     */
    public BoxFilesManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get file information.
     * 
     * @param fileId
     *            - the id of file.
     * @param fields
     *            - the information fields to retrieve; if <code>null</code> all
     *            information fields are retrieved.
     * @return The file information.
     */
    public BoxFile.Info getFileInfo(String fileId, String... fields) {
        try {
            LOG.debug("Getting info for file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            if (fields == null || fields.length == 0) {
                return file.getInfo();
            } else {
                return file.getInfo(fields);
            }
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Update file information.
     * 
     * @param fileId
     *            - the id of file to update.
     * @param info
     *            - the updated information
     * @return The updated file.
     */
    public BoxFile updateFileInfo(String fileId, BoxFile.Info info) {
        try {
            LOG.debug("Updating info for file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (info == null) {
                throw new IllegalArgumentException("Parameter 'info' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);
            file.updateInfo(info);
            return file;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Upload a new file to parent folder.
     * 
     * @param parentFolderId
     *            - the id of parent folder.
     * @param content
     *            - a stream containing contents of the file to upload.
     * @param fileName
     *            the name to give the uploaded file.
     * @param created
     *            - the content created date that will be given to the uploaded
     *            file.
     * @param modified
     *            - the content modified date that will be given to the uploaded
     *            file.
     * @param size
     *            - the size of the file's content used for monitoring the
     *            upload's progress.
     * @param listener
     *            - a listener for monitoring the upload's progress.
     * @return The uploaded file.
     */
    public BoxFile uploadFile(String parentFolderId, InputStream content, String fileName, Date created, Date modified,
            Long size, ProgressListener listener) {
        try {
            LOG.debug("Uploading file with name '" + fileName + "' to parent_folder(id=" + parentFolderId + ")");
            if (parentFolderId == null) {
                throw new IllegalArgumentException("Parameter 'parentFolderId' can not be null");
            }
            if (content == null) {
                throw new IllegalArgumentException("Paramerer 'content' can not be null");
            }
            if (fileName == null) {
                throw new IllegalArgumentException("Paramerer 'fileName' can not be null");
            }
            BoxFolder parentFolder = new BoxFolder(boxConnection, parentFolderId);

            FileUploadParams uploadParams = new FileUploadParams();
            uploadParams.setName(fileName);
            uploadParams.setContent(content);
            if (created != null) {
                uploadParams.setCreated(created);
            }
            if (modified != null) {
                uploadParams.setModified(modified);
            }
            if (size != null) {
                uploadParams.setSize(size);
            }
            if (listener != null) {
                uploadParams.setProgressListener(listener);
            }

            return parentFolder.uploadFile(uploadParams).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Upload a new version of file.
     * 
     * @param fileId
     *            - the id of file.
     * @param fileContent
     *            - a stream containing contents of the file to upload.
     * @param modified
     *            - the content modified date that will be given to the uploaded
     *            file.
     * @param fileSize
     *            - the size of the file's content used for monitoring the
     *            upload's progress.
     * @param listener
     *            - a listener for monitoring the upload's progress.
     * @return The uploaded file.
     */
    public BoxFile uploadNewFileVersion(String fileId, InputStream fileContent, Date modified, Long fileSize,
            ProgressListener listener) {
        try {
            LOG.debug("Uploading new version of file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (fileContent == null) {
                throw new IllegalArgumentException("Paramerer 'fileContent' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            if (modified != null) {
                if (fileSize != null) {
                    file.uploadVersion(fileContent, modified, fileSize, listener);
                } else {
                    file.uploadVersion(fileContent, modified, 0, listener);
                }
            } else {
                file.uploadVersion(fileContent);
            }

            return file;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get any previous versions of file.
     * 
     * @param fileId
     *            - the id of file.
     * @return The list of previous file versions.
     */
    public Collection<BoxFileVersion> getFileVersions(String fileId) {
        try {
            LOG.debug("Getting versions of file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            return file.getVersions();

        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Download a file.
     * 
     * @param fileId
     *            - the id of file.
     * @param output
     *            - the stream to which the file contents will be written.
     * @param rangeStart
     *            - the byte offset in file at which to start the download; if
     *            <code>null</code> the entire contents of file will be
     *            downloaded.
     * @param rangeEnd
     *            - the byte offset in file at which to stop the download; if
     *            <code>null</code> the entire contents of file will be
     *            downloaded.
     * @param listener
     *            - a listener for monitoring the download's progress; if
     *            <code>null</code> the download's progress will not be
     *            monitored.
     * @return The stream containing the contents of the downloaded file.
     */
    public OutputStream downloadFile(String fileId, OutputStream output, Long rangeStart, Long rangeEnd,
            ProgressListener listener) {
        try {
            LOG.debug("Downloading file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (output == null) {
                throw new IllegalArgumentException("Parameter 'output' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);

            if (listener != null) {
                if (rangeStart != null && rangeEnd != null) {
                    file.downloadRange(output, rangeStart, rangeEnd, listener);
                } else {
                    file.download(output, listener);
                }
            } else {
                if (rangeStart != null && rangeEnd != null) {
                    file.downloadRange(output, rangeStart, rangeEnd);
                } else {
                    file.download(output);
                }
            }
            return output;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Download a previous version of file.
     * 
     * @param fileId
     *            - the id of file.
     * @param version
     *            - the version of file to download; initial version of file has
     *            value of <code>0</code>, second version of file is
     *            <code>1</code> and so on.
     * @param output
     *            - the stream to which the version contents will be written.
     * @param listener
     *            - a listener for monitoring the download's progress; if
     *            <code>null</code> the download's progress will not be
     *            monitored.
     * @return The stream containing the contents of the downloaded file
     *         version.
     */
    public OutputStream downloadPreviousFileVersion(String fileId, Integer version, OutputStream output,
            ProgressListener listener) {
        try {
            LOG.debug("Downloading file(id=" + fileId + ", version=" + version + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (version == null) {
                throw new IllegalArgumentException("Parameter 'version' can not be null");
            }
            if (output == null) {
                throw new IllegalArgumentException("Parameter 'output' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);

            List<BoxFileVersion> fileVersions = (List<BoxFileVersion>) file.getVersions();
            BoxFileVersion fileVersion = fileVersions.get(version);

            if (listener != null) {
                fileVersion.download(output, listener);
            } else {
                fileVersion.download(output);
            }
            return output;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Promote a previous version of file.
     * 
     * @param fileId
     *            - the id of file.
     * @param version
     *            - the version of file to promote; initial version of file has
     *            value of <code>0</code>, second version of file is
     *            <code>1</code> and so on.
     * @return The promoted version of file.
     */
    public BoxFileVersion promoteFileVersion(String fileId, Integer version) {
        try {
            LOG.debug("Promoting file(id=" + fileId + ", version=" + version + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (version == null) {
                throw new IllegalArgumentException("Parameter 'version' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);

            List<BoxFileVersion> fileVersions = (List<BoxFileVersion>) file.getVersions();
            BoxFileVersion fileVersion = fileVersions.get(version);

            fileVersion.promote();
            return fileVersion;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Copy file to destination folder while optionally giving it a new name.
     * 
     * @param fileId
     *            - the id of file to copy.
     * @param destinationFolderId
     *            - the id of the destination folder.
     * @param newName
     *            - the new name for copied file; if <code>newName</code> is
     *            <code>null</code>, the copied file has same name as the
     *            original.
     * @return The copied file.
     */
    public BoxFile copyFile(String fileId, String destinationFolderId, String newName) {
        try {
            LOG.debug("Copying file(id=" + fileId + ") to destination_folder(id=" + destinationFolderId + ")"
                    + (newName == null ? "" : " with new name '" + newName + "'"));
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (destinationFolderId == null) {
                throw new IllegalArgumentException("Parameter 'destinationFolderId' can not be null");
            }
            BoxFile fileToCopy = new BoxFile(boxConnection, fileId);
            BoxFolder destinationFolder = new BoxFolder(boxConnection, destinationFolderId);
            if (newName == null) {
                return fileToCopy.copy(destinationFolder).getResource();
            } else {
                return fileToCopy.copy(destinationFolder, newName).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Move file to destination folder while optionally giving it a new name.
     * 
     * @param fileId
     *            - the id of file to move.
     * @param destinationFolderId
     *            - the id of the destination folder.
     * @param newName
     *            - the new name of moved file; if <code>newName</code> is
     *            <code>null</code>, the moved file has same name as the
     *            original.
     * @return The moved file.
     */
    public BoxFile moveFile(String fileId, String destinationFolderId, String newName) {
        try {
            LOG.debug("Moving file(id=" + fileId + ") to destination_folder(id=" + destinationFolderId + ")"
                    + (newName == null ? "" : " with new name '" + newName + "'"));
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (destinationFolderId == null) {
                throw new IllegalArgumentException("Parameter 'destinationFolderId' can not be null");
            }
            BoxFile fileToMove = new BoxFile(boxConnection, fileId);
            BoxFolder destinationFolder = new BoxFolder(boxConnection, destinationFolderId);
            if (newName == null) {
                return (BoxFile) fileToMove.move(destinationFolder).getResource();
            } else {
                return (BoxFile) fileToMove.move(destinationFolder, newName).getResource();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Rename file giving it the name <code>newName</code>
     * 
     * @param fileId
     *            - the id of file to rename.
     * @param newFileName
     *            - the new name of file.
     * @return The renamed file.
     */
    public BoxFile renameFile(String fileId, String newFileName) {
        try {
            LOG.debug("Renaming file(id=" + fileId + ") to '" + newFileName + "'");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (newFileName == null) {
                throw new IllegalArgumentException("Parameter 'newName' can not be null");
            }
            BoxFile fileToRename = new BoxFile(boxConnection, fileId);
            fileToRename.rename(newFileName);
            return fileToRename;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete the file.
     * 
     * @param fileId
     *            - the id of file to delete.
     */
    public void deleteFile(String fileId) {
        try {
            LOG.debug("Deleting file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);
            file.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete a file version.
     * 
     * @param fileId
     *            - the id of file with version to delete.
     * @param version
     *            - the version of file to delete; initial version of file has
     *            value of <code>0</code>, second version of file is
     *            <code>1</code> and so on.
     */
    public void deleteFileVersion(String fileId, Integer version) {
        try {
            LOG.debug("Deleting file(id=" + fileId + ", version=" + version + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (version == null) {
                throw new IllegalArgumentException("Parameter 'version' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);
            List<BoxFileVersion> versions = (List<BoxFileVersion>) file.getVersions();
            BoxFileVersion fileVersion = versions.get(version);

            fileVersion.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create a shared link to file.
     * 
     * @param fileId
     *            - the id of the file to create shared link on.
     * @param access
     *            - the access level of the shared link.
     * @param unshareDate
     *            - the date and time at which time the created shared link will
     *            expire; if <code>unsharedDate</code> is <code>null</code> then
     *            a non-expiring link is created.
     * @param permissions
     *            - the permissions of the created link; if
     *            <code>permissions</code> is <code>null</code> then the created
     *            shared link is create with default permissions.
     * @return The created shared link.
     */
    public BoxSharedLink createFileSharedLink(String fileId, BoxSharedLink.Access access, Date unshareDate,
            BoxSharedLink.Permissions permissions) {
        try {
            LOG.debug("Creating shared link for file(id=" + fileId + ") with access=" + access
                    + (unshareDate == null ? ""
                            : " unsharedDate=" + SimpleDateFormat.getDateTimeInstance().format(unshareDate)
                                    + " permissions=" + permissions));

            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (access == null) {
                throw new IllegalArgumentException("Parameter 'access' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);
            return file.createSharedLink(access, unshareDate, permissions);
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get an expiring URL for downloading a file directly from Box. This can be
     * user, for example, for sending as a redirect to a browser to cause the
     * browser to download the file directly from Box.
     * 
     * @param fileId
     *            - the id of file.
     * @return The temporary download URL
     */
    public URL getDownloadURL(String fileId) {
        try {
            LOG.debug("Getting download URL for file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            return file.getDownloadURL();

        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get an expiring URL for creating an embedded preview session. The URL
     * will expire after 60 seconds and the preview session will expire after 60
     * minutes.
     * 
     * @param fileId
     *            - the id of the file to get preview link on.
     * @return The preview link.
     */
    public URL getFilePreviewLink(String fileId) {
        try {
            LOG.debug("Getting preview link for file(id=" + fileId + ")");

            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);
            return file.getPreviewLink();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Get an expiring URL for creating an embedded preview session. The URL
     * will expire after 60 seconds and the preview session will expire after 60
     * minutes.
     * 
     * @param fileId
     *            - the id of the file to get preview link on.
     * @param fileType
     *            - either PNG of JPG.
     * @param minWidth
     *            - minimum width.
     * @param minHeight
     *            - minimum height.
     * @param maxWidth
     *            - maximum width.
     * @param maxHeight
     *            - maximum height.
     * @return The byte array of the thumbnail image.
     */
    public byte[] getFileThumbnail(String fileId, BoxFile.ThumbnailFileType fileType, Integer minWidth,
            Integer minHeight, Integer maxWidth, Integer maxHeight) {
        try {
            LOG.debug("Getting thumbnail for file(id=" + fileId + ") fileType=" + fileType + " minWidth=" + minWidth
                    + " minHeight=" + minHeight + " maxWidth=" + maxWidth + " maxHeight=" + maxHeight);

            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (fileType == null) {
                throw new IllegalArgumentException("Parameter 'fileType' can not be null");
            }
            if (minWidth == null) {
                throw new IllegalArgumentException("Parameter 'minWidth' can not be null");
            }
            if (minHeight == null) {
                throw new IllegalArgumentException("Parameter 'minHeight' can not be null");
            }
            if (maxWidth == null) {
                throw new IllegalArgumentException("Parameter 'maxWidth' can not be null");
            }
            if (maxHeight == null) {
                throw new IllegalArgumentException("Parameter 'maxHeight' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);
            return file.getThumbnail(fileType, minWidth, minHeight, maxWidth, maxHeight);
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Create metadata for file in either the global properties template or the
     * specified template type.
     * 
     * @param fileId
     *            - the id of the file to create metadata for.
     * @param metadata
     *            - the new metadata values.
     * @param typeName
     *            - the metadata template type name; if <code>null</code> the
     *            global properties template type is used.
     * @return The metadata returned from the server.
     */
    public Metadata createFileMetadata(String fileId, Metadata metadata, String typeName) {
        try {
            LOG.debug("Creating metadata for file(id=" + fileId + ")");

            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (metadata == null) {
                throw new IllegalArgumentException("Parameter 'metadata' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            if (typeName != null) {
                return file.createMetadata(typeName, metadata);
            } else {
                return file.createMetadata(metadata);
            }
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Gets the file properties metadata.
     * 
     * @param fileId
     *            - the id of the file to retrieve metadata for.
     * @param typeName
     *            - the metadata template type name; if <code>null</code> the
     *            global properties template type is used.
     * @return The metadata returned from the server.
     */
    public Metadata getFileMetadata(String fileId, String typeName) {
        try {
            LOG.debug("Get metadata for file(id=" + fileId + ")");

            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }

            BoxFile file = new BoxFile(boxConnection, fileId);

            if (typeName != null) {
                return file.getMetadata(typeName);
            } else {
                return file.getMetadata();
            }
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }

    }

    /**
     * Update the file properties metadata.
     * 
     * @param fileId
     *            - the id of file to delete.
     * @param metadata
     *            - the new metadata values.
     * @return The metadata returned from the server.
     */
    public Metadata updateFileMetadata(String fileId, Metadata metadata) {
        try {
            LOG.debug("Updating metadata for file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            if (metadata == null) {
                throw new IllegalArgumentException("Parameter 'metadata' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);
            return file.updateMetadata(metadata);
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

    /**
     * Delete the file properties metadata.
     * 
     * @param fileId
     *            - the id of file to delete.
     */
    public void deleteFileMetadata(String fileId) {
        try {
            LOG.debug("Deleting metadata for file(id=" + fileId + ")");
            if (fileId == null) {
                throw new IllegalArgumentException("Parameter 'fileId' can not be null");
            }
            BoxFile file = new BoxFile(boxConnection, fileId);
            file.deleteMetadata();
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }
}
