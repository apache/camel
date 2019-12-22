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
package org.apache.camel.component.atmos.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.emc.atmos.api.AtmosApi;
import com.emc.atmos.api.ObjectId;
import com.emc.atmos.api.ObjectPath;
import com.emc.atmos.api.bean.DirectoryEntry;
import com.emc.atmos.api.request.CreateObjectRequest;
import com.emc.atmos.api.request.ListDirectoryRequest;
import org.apache.camel.component.atmos.dto.AtmosDelResult;
import org.apache.camel.component.atmos.dto.AtmosFileDownloadResult;
import org.apache.camel.component.atmos.dto.AtmosFileUploadResult;
import org.apache.camel.component.atmos.dto.AtmosMoveResult;
import org.apache.camel.component.atmos.dto.AtmosResult;
import org.apache.camel.component.atmos.util.AtmosException;
import org.apache.camel.component.atmos.util.AtmosResultCode;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.atmos.util.AtmosConstants.ATMOS_FILE_SEPARATOR;

public final class AtmosAPIFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(AtmosAPIFacade.class);

    private static AtmosAPIFacade instance;
    private static AtmosApi client;

    private AtmosAPIFacade() {
    }

    /**
     * Return a singleton instance of this class
     *
     * @param client the AtmosClient performing atmos low level operations
     * @return the singleton instance of this class
     */
    public static AtmosAPIFacade getInstance(AtmosApi client) {
        if (instance == null) {
            instance = new AtmosAPIFacade();
            AtmosAPIFacade.client = client;
        }
        return instance;
    }

    /**
     * Put or upload a new file or an entire directory to atmos
     *
     * @param localPath the file path or the dir path on the local filesystem
     * @param remotePath the remote path destination on atmos
     * the file already existing with the same name
     * will be overridden.
     * @return a AtmosResult object reporting for each remote path the result of
     * the operation.
     * @throws AtmosException
     */
    public AtmosResult put(String localPath, String remotePath) throws AtmosException {
        AtmosResult result = new AtmosFileUploadResult();
        //a map representing for each path the result of the put operation
        Map<String, AtmosResultCode> resultEntries = null;
        //in case the remote path is not specified, the remotePath = localPath
        String atmosPath = remotePath == null ? localPath : remotePath;
        if (!atmosPath.endsWith(ATMOS_FILE_SEPARATOR)) {
            atmosPath += ATMOS_FILE_SEPARATOR;
        }
        ObjectPath atmosEntry = new ObjectPath(atmosPath);

        if (!atmosPath.equals(ATMOS_FILE_SEPARATOR)) {
            if (AtmosAPIFacade.client.getSystemMetadata(atmosEntry) == null) {
                throw new AtmosException(atmosPath + " does not exist or cannot obtain metadata");
            }
        }

        File fileLocalPath = new File(localPath);
        //verify uploading of a single file
        if (fileLocalPath.isFile()) {
            //check if atmos file exists
            if (!atmosEntry.isDirectory()) {
                throw new AtmosException(atmosPath + " exists on atmos and is not a folder!");
            }
            atmosPath = atmosPath + fileLocalPath.getName();
            resultEntries = new HashMap<>(1);
            try {
                ObjectId uploadedFile = putSingleFile(fileLocalPath, atmosPath);
                if (uploadedFile == null) {
                    resultEntries.put(atmosPath, AtmosResultCode.KO);
                } else {
                    resultEntries.put(atmosPath, AtmosResultCode.OK);
                }

            } catch (Exception ex) {
                resultEntries.put(atmosPath, AtmosResultCode.KO);
            } finally {
                result.setResultEntries(resultEntries);
            }
            return result;
        } else {       //verify uploading of a list of files inside a dir
            LOG.info("uploading a dir...");
            //check if atmos folder exists
            if (!atmosEntry.isDirectory()) {
                throw new AtmosException(atmosPath + " exists on atmos and is not a folder!");
            }
            //revert to old path
            String oldAtmosPath = atmosPath;
            //list all files in a dir
            Collection<File> listFiles = FileUtils.listFiles(fileLocalPath, null, true);
            if (listFiles == null || listFiles.isEmpty()) {
                throw new AtmosException(localPath + " does not contain any files");
            }
            resultEntries = new HashMap<>(listFiles.size());
            for (File file : listFiles) {
                String absPath = file.getAbsolutePath();
                int indexRemainingPath = localPath.length();
                if (!localPath.endsWith("/")) {
                    indexRemainingPath += 1;
                }
                String remainingPath = absPath.substring(indexRemainingPath);
                atmosPath = atmosPath + remainingPath;
                try {
                    LOG.debug("uploading: {} to {}", fileLocalPath, atmosPath);
                    ObjectId uploadedFile = putSingleFile(file, atmosPath);
                    if (uploadedFile == null) {
                        resultEntries.put(atmosPath, AtmosResultCode.KO);
                    } else {
                        resultEntries.put(atmosPath, AtmosResultCode.OK);
                    }
                } catch (Exception ex) {
                    resultEntries.put(atmosPath, AtmosResultCode.KO);
                }
                atmosPath = oldAtmosPath;
            }
            result.setResultEntries(resultEntries);
            return result;
        }
    }

    private ObjectId putSingleFile(File inputFile, String atmosPath) throws Exception {
        FileInputStream inputStream = new FileInputStream(inputFile);
        ObjectId uploadedFile = null;
        try {
            ObjectPath op = new ObjectPath(atmosPath);
            CreateObjectRequest request = new CreateObjectRequest();
            request.identifier(op).content(inputStream).contentLength(inputFile.length());
            uploadedFile = AtmosAPIFacade.client.createObject(request).getObjectId();
            return uploadedFile;
        } finally {
            inputStream.close();
        }
    }

    /**
     * Delete every files and subdirectories inside the remote directory. In
     * case the remotePath is a file, delete the file.
     *
     * @param remotePath the remote location to delete
     * @return a AtmosResult object with the result of the delete operation.
     * @throws AtmosException
     */
    public AtmosResult del(String remotePath) throws AtmosException {
        AtmosResult result = null;
        ObjectPath op = new ObjectPath(remotePath);
        AtmosAPIFacade.client.delete(op);
        result = new AtmosDelResult();
        result.setResultEntries(remotePath);
        return result;
    }

    /**
     * Rename a remote path with the new path location.
     *
     * @param remotePath the existing remote path to be renamed
     * @param newRemotePath the new remote path substituting the old one
     * @return a AtmosResult object with the result of the move operation.
     * @throws AtmosException
     */
    public AtmosResult move(String remotePath, String newRemotePath) throws AtmosException {
        AtmosResult result = null;
        AtmosAPIFacade.client.move(new ObjectPath(remotePath), new ObjectPath(newRemotePath), true);
        result = new AtmosMoveResult();
        result.setResultEntries(remotePath + "-" + newRemotePath);
        return result;
    }

    /**
     * Get the content of every file inside the remote path.
     *
     * @param remotePath the remote path where to download from
     * @return a AtmosResult object with the content (ByteArrayOutputStream) of
     * every files inside the remote path.
     * @throws AtmosException
     */
    public AtmosResult get(String remotePath) throws AtmosException {
        AtmosResult result = new AtmosFileDownloadResult();
        //a map representing for each path the result of the baos
        Map<String, ByteArrayOutputStream> resultEntries = new HashMap<>();
        //iterate from the remotePath
        downloadFilesInFolder(remotePath, resultEntries);
        //put the map of baos as result
        result.setResultEntries(resultEntries);
        return result;
    }

    private void downloadFilesInFolder(String atmosPath, Map<String, ByteArrayOutputStream> resultEntries) throws AtmosException {
        ObjectPath atmosEntry = new ObjectPath(atmosPath);
        if (AtmosAPIFacade.client.getSystemMetadata(atmosEntry) == null) {
            throw new AtmosException(atmosPath + " does not exist or cannot obtain metadata");
        }
        if (!atmosEntry.isDirectory()) {
            LOG.debug("downloading a single file...");
            downloadSingleFile(atmosPath, resultEntries);
            return;
        }
        ListDirectoryRequest listRequest = new ListDirectoryRequest().path(atmosEntry);
        AtmosAPIFacade.client.listDirectory(listRequest);
        for (DirectoryEntry entry : AtmosAPIFacade.client.listDirectory(listRequest).getEntries()) {
            if (!entry.isDirectory()) {
                try {
                    //get the baos of the file
                    downloadSingleFile(atmosEntry.getPath().concat(entry.getFilename()), resultEntries);
                } catch (AtmosException e) {
                    LOG.warn("Cannot download from {}", entry.getFilename());
                }
            } else {
                //iterate on folder
                downloadFilesInFolder(atmosEntry.getPath().concat(entry.getFilename()), resultEntries);
            }
        }
    }

    private void downloadSingleFile(String path, Map<String, ByteArrayOutputStream> resultEntries) throws AtmosException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] content = null;
        try {
            content = AtmosAPIFacade.client.readObject(new ObjectPath(path), byte[].class);
            baos.write(content);
        } catch (IOException e) {
            throw new AtmosException(path + " cannot obtain a stream", e);
        }
        if (content != null) {
            resultEntries.put(path, baos);
            LOG.debug("Downloaded path: {} size: {}", path, baos.size());
        }

    }
}
