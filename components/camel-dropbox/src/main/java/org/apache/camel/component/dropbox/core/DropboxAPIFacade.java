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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.SearchMatch;
import com.dropbox.core.v2.files.SearchResult;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.files.WriteMode;
import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.dto.DropboxDelResult;
import org.apache.camel.component.dropbox.dto.DropboxFileDownloadResult;
import org.apache.camel.component.dropbox.dto.DropboxFileUploadResult;
import org.apache.camel.component.dropbox.dto.DropboxMoveResult;
import org.apache.camel.component.dropbox.dto.DropboxSearchResult;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxResultCode;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dropbox.util.DropboxConstants.HEADER_PUT_FILE_NAME;

public final class DropboxAPIFacade {

    private static final Logger LOG = LoggerFactory.getLogger(DropboxAPIFacade.class);

    private final DbxClientV2 client;

    private final Exchange exchange;

    /**
     * @param client the DbxClient performing dropbox low level operations
     * @param exchange the current Exchange
     */
    public DropboxAPIFacade(DbxClientV2 client, Exchange exchange) {
        this.client = client;
        this.exchange = exchange;
    }

    /**
     * Put or upload a new file or an entire directory to dropbox
     *
     * @param localPath the file path or the dir path on the local filesystem
     * @param remotePath the remote path destination on dropbox
     * @param mode how a file should be saved on dropbox; in case of "add" the
     *            new file will be renamed in case a file with the same name
     *            already exists on dropbox. in case of "force" the file already
     *            existing with the same name will be overridden.
     * @return a result object reporting for each remote path the result of the
     *         operation.
     * @throws DropboxException
     */
    public DropboxFileUploadResult put(String localPath, String remotePath, DropboxUploadMode mode) throws DropboxException {
        // in case the remote path is not specified, the remotePath = localPath
        String dropboxPath = remotePath == null ? localPath : remotePath;

        UploadUploader entry;
        try {
            entry = client.files().upload(dropboxPath);
        } catch (DbxException e) {
            throw new DropboxException(dropboxPath + " does not exist or can't obtain metadata");
        }

        if (localPath != null) {
            return putFile(localPath, mode, dropboxPath, entry);
        } else {
            return putBody(exchange, mode, dropboxPath, entry);
        }
    }

    private DropboxFileUploadResult putFile(String localPath, DropboxUploadMode mode, String dropboxPath, UploadUploader entry) throws DropboxException {
        File fileLocalPath = new File(localPath);
        // verify uploading of a single file
        if (fileLocalPath.isFile()) {
            // check if dropbox file exists
            if (entry != null) {
                throw new DropboxException(dropboxPath + " exists on dropbox and is not a file!");
            }
            // in case the entry not exists on dropbox check if the filename
            // should be appended
            if (entry == null) {
                if (dropboxPath.endsWith(DropboxConstants.DROPBOX_FILE_SEPARATOR)) {
                    dropboxPath = dropboxPath + fileLocalPath.getName();
                }
            }

            LOG.debug("Uploading: {},{}", fileLocalPath, dropboxPath);
            DropboxFileUploadResult result;
            try {
                FileMetadata uploadedFile = putSingleFile(fileLocalPath, dropboxPath, mode);
                if (uploadedFile == null) {
                    result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
                } else {
                    result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.OK);
                }
            } catch (Exception ex) {
                result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
            }
            return result;
        } else if (fileLocalPath.isDirectory()) {
            // verify uploading of a list of files inside a dir
            LOG.debug("Uploading a dir...");
            // check if dropbox folder exists
            if (entry != null) {
                throw new DropboxException(dropboxPath + " exists on dropbox and is not a folder!");
            }
            if (!dropboxPath.endsWith(DropboxConstants.DROPBOX_FILE_SEPARATOR)) {
                dropboxPath = dropboxPath + DropboxConstants.DROPBOX_FILE_SEPARATOR;
            }
            // revert to old path
            String oldDropboxPath = dropboxPath;
            // list all files in a dir
            Collection<File> listFiles = FileUtils.listFiles(fileLocalPath, null, true);
            if (listFiles.isEmpty()) {
                throw new DropboxException(localPath + " doesn't contain any files");
            }

            HashMap<String, DropboxResultCode> resultMap = new HashMap<>(listFiles.size());
            for (File file : listFiles) {
                String absPath = file.getAbsolutePath();
                int indexRemainingPath = localPath.length();
                if (!localPath.endsWith("/")) {
                    indexRemainingPath += 1;
                }
                String remainingPath = absPath.substring(indexRemainingPath);
                dropboxPath = dropboxPath + remainingPath;
                try {
                    LOG.debug("Uploading: {},{}", fileLocalPath, dropboxPath);
                    FileMetadata uploadedFile = putSingleFile(file, dropboxPath, mode);
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
        } else {
            return null;
        }
    }

    private DropboxFileUploadResult putBody(Exchange exchange, DropboxUploadMode mode, String dropboxPath, UploadUploader entry) throws DropboxException {
        String name = exchange.getIn().getHeader(HEADER_PUT_FILE_NAME, String.class);
        if (name == null) {
            // fallback to use CamelFileName
            name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        }
        if (name == null) {
            // use message id as file name
            name = exchange.getIn().getMessageId();
        }

        // in case the entry not exists on dropbox check if the filename should
        // be appended
        if (entry == null) {
            if (dropboxPath.endsWith(DropboxConstants.DROPBOX_FILE_SEPARATOR)) {
                dropboxPath = dropboxPath + name;
            }
        }

        LOG.debug("Uploading message body: {}", dropboxPath);

        DropboxFileUploadResult result;
        try {
            FileMetadata uploadedFile = putSingleBody(exchange, dropboxPath, mode);
            if (uploadedFile == null) {
                result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
            } else {
                result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.OK);
            }
        } catch (Exception ex) {
            result = new DropboxFileUploadResult(dropboxPath, DropboxResultCode.KO);
        }
        return result;
    }

    private FileMetadata putSingleFile(File inputFile, String dropboxPath, DropboxUploadMode mode) throws Exception {
        FileInputStream inputStream = new FileInputStream(inputFile);
        FileMetadata uploadedFile;
        try {
            WriteMode uploadMode;
            if (mode == DropboxUploadMode.force) {
                uploadMode = WriteMode.OVERWRITE;
            } else {
                uploadMode = WriteMode.ADD;
            }
            uploadedFile = client.files().uploadBuilder(dropboxPath).withMode(uploadMode).uploadAndFinish(inputStream, inputFile.length());
            return uploadedFile;
        } finally {
            IOHelper.close(inputStream);
        }
    }

    private FileMetadata putSingleBody(Exchange exchange, String dropboxPath, DropboxUploadMode mode) throws Exception {
        byte[] data = exchange.getIn().getMandatoryBody(byte[].class);
        InputStream is = new ByteArrayInputStream(data);
        try {
            FileMetadata uploadedFile;
            WriteMode uploadMode;
            if (mode == DropboxUploadMode.force) {
                uploadMode = WriteMode.OVERWRITE;
            } else {
                uploadMode = WriteMode.ADD;
            }
            uploadedFile = client.files().uploadBuilder(dropboxPath).withMode(uploadMode).uploadAndFinish(is, data.length);
            return uploadedFile;
        } finally {
            IOHelper.close(is);
        }
    }

    /**
     * Search inside a remote path including its sub directories. The query
     * param can be null.
     *
     * @param remotePath the remote path where starting the search from
     * @param query a space-separated list of substrings to search for. A file
     *            matches only if it contains all the substrings
     * @return a result object containing all the files found.
     * @throws DropboxException
     */
    public DropboxSearchResult search(String remotePath, String query) throws DropboxException {
        SearchResult listing;
        List<SearchMatch> searchMatches;
        if (query == null) {
            LOG.debug("Search no query");
            try {
                listing = client.files().search(remotePath, null);
                searchMatches = listing.getMatches();
                return new DropboxSearchResult(searchMatches);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
        } else {
            LOG.debug("Search by query: {}", query);
            try {
                listing = client.files().search(remotePath, query);
                searchMatches = listing.getMatches();
                return new DropboxSearchResult(searchMatches);
            } catch (DbxException e) {
                throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
            }
        }
    }

    /**
     * Delete every files and subdirectories inside the remote directory. In
     * case the remotePath is a file, delete the file.
     *
     * @param remotePath the remote location to delete
     * @return a result object with the result of the delete operation.
     * @throws DropboxException
     */
    public DropboxDelResult del(String remotePath) throws DropboxException {
        try {
            client.files().deleteV2(remotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
        return new DropboxDelResult(remotePath);
    }

    /**
     * Rename a remote path with the new path location.
     *
     * @param remotePath the existing remote path to be renamed
     * @param newRemotePath the new remote path substituting the old one
     * @return a result object with the result of the move operation.
     * @throws DropboxException
     */
    public DropboxMoveResult move(String remotePath, String newRemotePath) throws DropboxException {
        try {
            client.files().moveV2(remotePath, newRemotePath);
            return new DropboxMoveResult(remotePath, newRemotePath);
        } catch (DbxException e) {
            throw new DropboxException(remotePath + " does not exist or can't obtain metadata");
        }
    }

    /**
     * Get the content of every file inside the remote path.
     *
     * @param remotePath the remote path where to download from
     * @return a result object with the content (ByteArrayOutputStream) of every
     *         files inside the remote path.
     * @throws DropboxException
     */
    public DropboxFileDownloadResult get(String remotePath) throws DropboxException {
        return new DropboxFileDownloadResult(downloadFilesInFolder(remotePath));
    }

    private Map<String, Object> downloadFilesInFolder(String path) throws DropboxException {
        try {
            ListFolderResult folderResult = client.files().listFolder(path.equals("/") ? "" : path);
            Map<String, Object> returnMap = new LinkedHashMap<>();
            for (Metadata entry : folderResult.getEntries()) {
                returnMap.put(entry.getPathDisplay(), downloadSingleFile(entry.getPathDisplay()).getValue());
            }
            return returnMap;
        } catch (ListFolderErrorException e) {
            try {
                DbxDownloader<FileMetadata> listing = client.files().download(path);
                if (listing == null) {
                    return Collections.emptyMap();
                } else {
                    LOG.debug("downloading a single file...");
                    Map.Entry<String, Object> entry = downloadSingleFile(path);
                    return Collections.singletonMap(entry.getKey(), entry.getValue());
                }
            } catch (DbxException dbxException) {
                throw new DropboxException(dbxException);
            }
        } catch (DbxException e) {
            throw new DropboxException(e);
        }
    }

    private Map.Entry<String, Object> downloadSingleFile(String path) throws DropboxException {
        try {
            OutputStreamBuilder target = OutputStreamBuilder.withExchange(exchange);
            DbxDownloader<FileMetadata> downloadedFile = client.files().download(path);
            if (downloadedFile != null) {
                downloadedFile.download(target);
                LOG.debug("downloaded path={}", path);
                return new AbstractMap.SimpleEntry<>(path, target.build());
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
