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

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxWriteMode;
import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.dto.*;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxResultCode;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static org.apache.camel.component.dropbox.util.DropboxConstants.DROPBOX_FILE_SEPARATOR;


public final class DropboxAPIFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxAPIFacade.class);

    private static DbxClient client;
    private final Exchange exchange;

    private DropboxAPIFacade(Exchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Return a singleton instance of this class
     * @param client the DbxClient performing dropbox low level operations
     * @return the singleton instance of this class
     */
    public static DropboxAPIFacade getInstance(DbxClient client, Exchange exchange) {
        return new DropboxAPIFacade(exchange);
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
    public DropboxFileUploadResult put(String localPath, String remotePath, DropboxUploadMode mode) throws DropboxException {
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

            DropboxFileUploadResult result;
            try {
                DbxEntry.File uploadedFile = putSingleFile(fileLocalPath, dropboxPath, mode);
                if (uploadedFile == null) {
                    result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
                } else {
                    result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.OK);
                }
            } catch (Exception ex) {
                result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
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
            if (listFiles.isEmpty()) {
                throw new DropboxException(localPath + " doesn't contain any files");
            }

            HashMap<String, DropboxResultCode> resultMap = new HashMap<String, DropboxResultCode>(listFiles.size());
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
                        resultMap.put(dropboxPath, DropboxResultCode.KO);
                    } else {
                        resultMap.put(dropboxPath, DropboxResultCode.OK);
                    }
                } catch (Exception ex) {
                    resultMap.put(dropboxPath, DropboxResultCode.KO);
                }
                dropboxPath = oldDropboxPath;
            }
            return new DropboxFileUploadResult(resultMap);
        }
    }

    private DbxEntry.File putSingleFile(File inputFile, String dropboxPath, DropboxUploadMode mode) throws Exception {
        FileInputStream inputStream = new FileInputStream(inputFile);
        DbxEntry.File uploadedFile;
        try {
            DbxWriteMode uploadMode;
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
    public DropboxSearchResult search(String remotePath, String query) throws DropboxException {
        DbxEntry.WithChildren listing;
        if (query == null) {
            LOG.info("search no query");
            try {
                listing = DropboxAPIFacade.client.getMetadataWithChildren(remotePath);
                return new DropboxSearchResult(listing.children);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
        } else {
            LOG.info("search by query:" + query);
            try {
                List<DbxEntry> entries = DropboxAPIFacade.client.searchFileAndFolderNames(remotePath, query);
                return new DropboxSearchResult(entries);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
        }
    }

    /**
     * Delete every files and subdirectories inside the remote directory.
     * In case the remotePath is a file, delete the file.
     * @param remotePath  the remote location to delete
     * @return a DropboxResult object with the result of the delete operation.
     * @throws DropboxException
     */
    public DropboxDelResult del(String remotePath) throws DropboxException {
        try {
            DropboxAPIFacade.client.delete(remotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
        return new DropboxDelResult(remotePath);
    }

    /**
     * Rename a remote path with the new path location.
     * @param remotePath the existing remote path to be renamed
     * @param newRemotePath the new remote path substituting the old one
     * @return a DropboxResult object with the result of the move operation.
     * @throws DropboxException
     */
    public DropboxMoveResult move(String remotePath, String newRemotePath) throws DropboxException {
        try {
            DropboxAPIFacade.client.move(remotePath, newRemotePath);
            return new DropboxMoveResult(remotePath, newRemotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
    }

    /**
     * Get the content of every file inside the remote path.
     * @param remotePath the remote path where to download from
     * @return a DropboxResult object with the content (ByteArrayOutputStream) of every files inside the remote path.
     * @throws DropboxException
     */
    public DropboxFileDownloadResult get(String remotePath) throws DropboxException {
        return new DropboxFileDownloadResult(downloadFilesInFolder(remotePath));
    }


    public boolean isDirectory(String path) throws DropboxException {
        try {
            DbxEntry.WithChildren listing = DropboxAPIFacade.client.getMetadataWithChildren(path);
            return listing.children != null;
        } catch (DbxException e) {
            throw new DropboxException(path + " does not exist or can't obtain metadata");
        }
    }


    private Map<String, OutputStream> downloadFilesInFolder(String path) throws DropboxException {
        DbxEntry.WithChildren listing;
        try {
            listing = DropboxAPIFacade.client.getMetadataWithChildren(path);
        } catch (DbxException e) {
            throw new DropboxException(path + " does not exist or can't obtain metadata");
        }

        if (listing.children == null) {
            LOG.info("downloading a single file...");
            Map.Entry<String, OutputStream> entry = downloadSingleFile(path);
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        }
        Map<String, OutputStream> result = new HashMap<String, OutputStream>();
        for (DbxEntry entry : listing.children) {
            if (entry.isFile()) {
                try {
                    Map.Entry<String, OutputStream> singleFile = downloadSingleFile(entry.path);
                    result.put(singleFile.getKey(), singleFile.getValue());
                } catch (DropboxException e) {
                    LOG.warn("can't download from " + entry.path);
                }
            } else {
                Map<String, OutputStream> filesInFolder = downloadFilesInFolder(entry.path);
                result.putAll(filesInFolder);
            }
        }
        return result;
    }

    private Map.Entry<String, OutputStream> downloadSingleFile(String path) throws DropboxException {
        try {
            OutputStreamBuilder target = OutputStreamBuilder.withExchange(exchange);
            DbxEntry.File downloadedFile = DropboxAPIFacade.client.getFile(path, null, target);
            if (downloadedFile != null) {
                LOG.info("downloaded path:" + path);
                return new AbstractMap.SimpleEntry<String, OutputStream>(path, target);
            } else {
                return null;
            }
        } catch (DbxException e) {
            throw new DropboxException(path + " does not exist or can't obtain metadata");
        } catch (IOException e) {
            throw new DropboxException(path + " can't obtain a stream");
        }
    }
}
