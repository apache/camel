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
package org.apache.camel.component.dropbox.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxWriteMode;
import org.apache.camel.component.dropbox.dto.DropboxDelResult;
import org.apache.camel.component.dropbox.dto.DropboxFileDownloadResult;
import org.apache.camel.component.dropbox.dto.DropboxFileUploadResult;
import org.apache.camel.component.dropbox.dto.DropboxMoveResult;
import org.apache.camel.component.dropbox.dto.DropboxResult;
import org.apache.camel.component.dropbox.dto.DropboxSearchResult;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxResultCode;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.apache.camel.component.dropbox.util.DropboxConstants.DROPBOX_FILE_SEPARATOR;


public final class DropboxAPIFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxAPIFacade.class);

    private static DropboxAPIFacade instance;
    private static DbxClient client;

    private DropboxAPIFacade() { }

    /**
     * Return a singleton instance of this class
     * @param client the DbxClient performing dropbox low level operations
     * @return the singleton instance of this class
     */
    public static DropboxAPIFacade getInstance(DbxClient client) {
        if (instance == null) {
            instance = new DropboxAPIFacade();
            DropboxAPIFacade.client = client;
        }
        return instance;
    }

    /**
     * Put or upload a new file or an entire directory to dropbox
     * @param localPath  the file path or the dir path on the local filesystem
     * @param remotePath the remote path destination on dropbox
     * @param mode how a file should be saved on dropbox;
     *             in case of "add" the new file will be renamed in case
     *             a file with the same name already exists on dropbox.
     *             in case of "force" the file already existing with the same name will be overridden.
     * @return a DropboxResult object reporting for each remote path the result of the operation.
     * @throws DropboxException
     */
    public DropboxResult put(String localPath, String remotePath, DropboxUploadMode mode) throws DropboxException {
        DropboxResult result = new DropboxFileUploadResult();
        //a map representing for each path the result of the put operation
        Map<String, DropboxResultCode> resultEntries = null;
        //in case the remote path is not specified, the remotePath = localPath
        String dropboxPath = remotePath == null ? localPath : remotePath;
        DbxEntry entry = null;
        try {
            entry = DropboxAPIFacade.client.getMetadata(dropboxPath);
        } catch (DbxException e) {
            throw new DropboxException(dropboxPath + " does not exist or can't obtain metadata");
        }
        File fileLocalPath = new File(localPath);
        //verify uploading of a single file
        if (fileLocalPath.isFile()) {
            //check if dropbox file exists
            if (entry != null && !entry.isFile()) {
                throw new DropboxException(dropboxPath + " exists on dropbox and is not a file!");
            }
            //in case the entry not exists on dropbox check if the filename should be appended
            if (entry == null) {
                if (dropboxPath.endsWith(DROPBOX_FILE_SEPARATOR)) {
                    dropboxPath = dropboxPath + fileLocalPath.getName();
                }
            }
            resultEntries = new HashMap<String, DropboxResultCode>(1);
            try {
                DbxEntry.File uploadedFile = putSingleFile(fileLocalPath, dropboxPath, mode);
                if (uploadedFile == null) {
                    resultEntries.put(dropboxPath, DropboxResultCode.KO);
                } else {
                    resultEntries.put(dropboxPath, DropboxResultCode.OK);
                }

            } catch (Exception ex) {
                resultEntries.put(dropboxPath, DropboxResultCode.KO);
            } finally {
                result.setResultEntries(resultEntries);
            }
            return result;
        } else {       //verify uploading of a list of files inside a dir
            LOG.info("uploading a dir...");
            //check if dropbox folder exists
            if (entry != null && !entry.isFolder()) {
                throw new DropboxException(dropboxPath + " exists on dropbox and is not a folder!");
            }
            if (!dropboxPath.endsWith(DROPBOX_FILE_SEPARATOR)) {
                dropboxPath = dropboxPath + DROPBOX_FILE_SEPARATOR;
            }
            //revert to old path
            String oldDropboxPath = dropboxPath;
            //list all files in a dir
            Collection<File> listFiles = FileUtils.listFiles(fileLocalPath, null, true);
            if (listFiles == null || listFiles.isEmpty()) {
                throw new DropboxException(localPath + " doesn't contain any files");
            }
            resultEntries = new HashMap<String, DropboxResultCode>(listFiles.size());
            for (File file : listFiles) {
                String absPath = file.getAbsolutePath();
                int indexRemainingPath = localPath.length();
                if (!localPath.endsWith("/")) {
                    indexRemainingPath += 1;
                }
                String remainingPath = absPath.substring(indexRemainingPath);
                dropboxPath = dropboxPath + remainingPath;
                try {
                    LOG.info("uploading:" + fileLocalPath + "," + dropboxPath);
                    DbxEntry.File uploadedFile = putSingleFile(file, dropboxPath, mode);
                    if (uploadedFile == null) {
                        resultEntries.put(dropboxPath, DropboxResultCode.KO);
                    } else {
                        resultEntries.put(dropboxPath, DropboxResultCode.OK);
                    }
                } catch (Exception ex) {
                    resultEntries.put(dropboxPath, DropboxResultCode.KO);
                }
                dropboxPath = oldDropboxPath;
            }
            result.setResultEntries(resultEntries);
            return result;
        }
    }

    private DbxEntry.File putSingleFile(File inputFile, String dropboxPath, DropboxUploadMode mode) throws Exception {
        FileInputStream inputStream = new FileInputStream(inputFile);
        DbxEntry.File uploadedFile = null;
        try {
            DbxWriteMode uploadMode = null;
            if (mode == DropboxUploadMode.force) {
                uploadMode = DbxWriteMode.force();
            } else {
                uploadMode = DbxWriteMode.add();
            }
            uploadedFile =
                DropboxAPIFacade.client.uploadFile(dropboxPath,
                            uploadMode, inputFile.length(), inputStream);
            return uploadedFile;
        } finally {
            inputStream.close();
        }
    }

    /**
     * Search inside a remote path including its sub directories.
     * The query param can be null.
     * @param remotePath  the remote path where starting the search from
     * @param query a space-separated list of substrings to search for. A file matches only if it contains all the substrings
     * @return a DropboxResult object containing all the files found.
     * @throws DropboxException
     */
    public DropboxResult search(String remotePath, String query) throws DropboxException {
        DropboxResult result = new DropboxSearchResult();
        DbxEntry.WithChildren listing = null;
        if (query == null) {
            LOG.info("search no query");
            try {
                listing = DropboxAPIFacade.client.getMetadataWithChildren(remotePath);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
            result.setResultEntries(listing.children);
        } else {
            LOG.info("search by query:" + query);
            List<DbxEntry> entries = null;
            try {
                entries = DropboxAPIFacade.client.searchFileAndFolderNames(remotePath, query);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
            result.setResultEntries(entries);
        }
        return result;
    }

    /**
     * Delete every files and subdirectories inside the remote directory.
     * In case the remotePath is a file, delete the file.
     * @param remotePath  the remote location to delete
     * @return a DropboxResult object with the result of the delete operation.
     * @throws DropboxException
     */
    public DropboxResult del(String remotePath) throws DropboxException {
        DropboxResult result = null;
        try {
            DropboxAPIFacade.client.delete(remotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
        result = new DropboxDelResult();
        result.setResultEntries(remotePath);
        return result;
    }

    /**
     * Rename a remote path with the new path location.
     * @param remotePath the existing remote path to be renamed
     * @param newRemotePath the new remote path substituting the old one
     * @return a DropboxResult object with the result of the move operation.
     * @throws DropboxException
     */
    public DropboxResult move(String remotePath, String newRemotePath) throws DropboxException {
        DropboxResult result = null;
        try {
            DropboxAPIFacade.client.move(remotePath, newRemotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
        result = new DropboxMoveResult();
        result.setResultEntries(remotePath + "-" + newRemotePath);
        return result;
    }

    /**
     * Get the content of every file inside the remote path.
     * @param remotePath the remote path where to download from
     * @return a DropboxResult object with the content (ByteArrayOutputStream) of every files inside the remote path.
     * @throws DropboxException
     */
    public DropboxResult get(String remotePath) throws DropboxException {
        DropboxResult result = new DropboxFileDownloadResult();
        //a map representing for each path the result of the baos
        Map<String, ByteArrayOutputStream> resultEntries = new HashMap<String, ByteArrayOutputStream>();
        //iterate from the remotePath
        downloadFilesInFolder(remotePath, resultEntries);
        //put the map of baos as result
        result.setResultEntries(resultEntries);
        return result;
    }

    private void downloadFilesInFolder(String path, Map<String, ByteArrayOutputStream> resultEntries) throws DropboxException {
        DbxEntry.WithChildren listing = null;
        try {
            listing = DropboxAPIFacade.client.getMetadataWithChildren(path);
        } catch (DbxException e) {
            throw new DropboxException(path + " does not exist or can't obtain metadata");
        }
        if (listing.children == null) {
            LOG.info("downloading a single file...");
            downloadSingleFile(path, resultEntries);
            return;
        }
        for (DbxEntry entry : listing.children) {
            if (entry.isFile()) {
                try {
                    //get the baos of the file
                    downloadSingleFile(entry.path, resultEntries);
                } catch (DropboxException e) {
                    LOG.warn("can't download from " + entry.path);
                }
            } else {
                //iterate on folder
                downloadFilesInFolder(entry.path, resultEntries);
            }
        }
    }

    private void downloadSingleFile(String path, Map<String, ByteArrayOutputStream> resultEntries) throws DropboxException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DbxEntry.File downloadedFile;
        try {
            downloadedFile = DropboxAPIFacade.client.getFile(path, null, baos);
        } catch (DbxException e) {
            throw new DropboxException(path + " does not exist or can't obtain metadata");
        } catch (IOException e) {
            throw new DropboxException(path + " can't obtain a stream");
        }
        if (downloadedFile != null) {
            resultEntries.put(path, baos);
            LOG.info("downloaded path:" + path + " - baos size:" + baos.size());
        }

    }
}
