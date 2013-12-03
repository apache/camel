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
import com.dropbox.core.DbxWriteMode;
import org.apache.camel.component.dropbox.dto.*;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxResultCode;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import static org.apache.camel.component.dropbox.util.DropboxConstants.DROPBOX_FILE_SEPARATOR;

public class DropboxAPIFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxAPIFacade.class);

    private static DropboxAPIFacade instance;
    private static DbxClient client;

    private DropboxAPIFacade() {
    }

    public static DropboxAPIFacade getInstance(DbxClient client) {
        if (instance == null) {
            instance = new DropboxAPIFacade();
            instance.client = client;
        }
        return instance;
    }

    public DropboxResult put(String localPath, String remotePath, DropboxUploadMode mode) throws Exception {
        DropboxResult result = new DropboxFileUploadResult();
        //a map representing for each path the result of the put operation
        Map<String, DropboxResultCode> resultEntries = null;
        //in case the remote path is not specified, the remotePath = localPath
        String dropboxPath = remotePath == null ? localPath : remotePath;
        DbxEntry entry = instance.client.getMetadata(dropboxPath);
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
        }
        //verify uploading of a list of files inside a dir
        else {
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
                    instance.client.uploadFile(dropboxPath,
                            uploadMode, inputFile.length(), inputStream);
            return uploadedFile;
        } finally {
            inputStream.close();
        }
    }

    public DropboxResult search(String remotePath, String query) throws Exception {
        DropboxResult result = new DropboxSearchResult();
        DbxEntry.WithChildren listing = null;
        if (query == null) {
            LOG.info("search no query");
            listing = instance.client.getMetadataWithChildren(remotePath);
            result.setResultEntries(listing.children);
        } else {
            LOG.info("search by query:" + query);
            List<DbxEntry> entries = instance.client.searchFileAndFolderNames(remotePath, query);
            result.setResultEntries(entries);
        }
        return result;
    }

    public DropboxResult del(String remotePath) throws Exception {
        DropboxResult result = null;
        instance.client.delete(remotePath);
        result = new DropboxDelResult();
        result.setResultEntries(remotePath);
        return result;
    }

    public DropboxResult move(String remotePath, String newRemotePath) throws Exception {
        DropboxResult result = null;
        instance.client.move(remotePath, newRemotePath);
        result = new DropboxMoveResult();
        result.setResultEntries(remotePath + "-" + newRemotePath);
        return result;
    }

    public DropboxResult get(String remotePath) throws Exception {
        DropboxResult result = new DropboxFileDownloadResult();
        //a map representing for each path the result of the baos
        Map<String, ByteArrayOutputStream> resultEntries = new HashMap<String, ByteArrayOutputStream>();
        //iterate from the remotePath
        downloadFilesInFolder(remotePath, resultEntries);
        //put the map of baos as result
        result.setResultEntries(resultEntries);
        return result;
    }

    private void downloadFilesInFolder(String path, Map<String, ByteArrayOutputStream> resultEntries) throws Exception {
        DbxEntry.WithChildren listing = instance.client.getMetadataWithChildren(path);
        if(listing.children == null) {
            LOG.info("downloading a single file...");
            downloadSingleFile(path,resultEntries);
            return;
        }
        for (DbxEntry entry : listing.children) {
            if (entry.isFile()) {
                //get the baos of the file
                downloadSingleFile(entry.path, resultEntries);
            }
            else {
                //iterate on folder
                downloadFilesInFolder(entry.path, resultEntries);
            }
        }
    }

    private void downloadSingleFile(String path, Map<String, ByteArrayOutputStream> resultEntries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DbxEntry.File downloadedFile = instance.client.getFile(path, null, baos);
        if (downloadedFile != null) {
            resultEntries.put(path, baos);
            LOG.info("downloaded path:"+path+" - baos size:" + baos.size());
        }

    }
}
