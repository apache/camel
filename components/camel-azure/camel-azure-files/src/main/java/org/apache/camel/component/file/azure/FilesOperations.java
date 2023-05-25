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
package org.apache.camel.component.file.azure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.EmptyStackException;
import java.util.Stack;

import com.azure.core.util.Context;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.models.ShareFileItem;
import com.azure.storage.file.share.models.ShareFileRange;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesOperations implements RemoteFileOperations<ShareFileItem> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ShareServiceClient client;
    protected FilesEndpoint<ShareFileItem> endpoint;
    private ShareDirectoryClient root;
    private Stack<ShareDirectoryClient> dirStack = new Stack<>();

    public FilesOperations(ShareServiceClient client) {
        this.client = client;
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<ShareFileItem> endpoint) {
        this.endpoint = (FilesEndpoint<ShareFileItem>) endpoint;
    }

    @Override
    public GenericFile<ShareFileItem> newGenericFile() {
        return new RemoteFile<>();
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange)
            throws GenericFileOperationFailedException {
        root = client.getShareClient(endpoint.getShare()).getRootDirectoryClient();
        dirStack.push(root);
        // TODO translate runtime exception to Camel one?
        return true;
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        return root != null;
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        root.forceCloseAllHandles(true, Duration.ofSeconds(3), Context.NONE);
        root = null;
        dirStack = new Stack<>();
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        log.debug("Deleting file: {}", name);

        boolean result;
        String target = name;
        String currentDir = null;

        try {
            reconnectIfNecessary(null);

            // remember current directory
            currentDir = getCurrentDirectory();
            target = FileUtil.stripPath(name);

            try {
                changeCurrentDirectory(FileUtil.onlyPath(name));
            } catch (GenericFileOperationFailedException e) {
                // we could not change directory, try to change back before
                changeCurrentDirectory(currentDir);
                throw e;
            }

            // delete the file
            log.trace("Client deleteFile: {}", target);
            result = cwd().deleteFileIfExists(target);

            // change back to previous directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }

        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }

        return result;
    }

    private ShareDirectoryClient cwd() {
        return dirStack.peek();
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        log.debug("Renaming file: {} to: {}", from, to);
        try {
            var file = cwd().getFileClient(from);
            file.rename(to);
            return true;
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        // must normalize directory first
        directory = endpoint.getConfiguration().normalizePath(directory);

        log.trace("buildDirectory({})", directory);
        var backup = dirStack.clone();
        try {

            boolean success;

            // maybe the full directory already exists
            try {
                changeCurrentDirectory(directory);
                success = true;
            } catch (RuntimeException ex) {
                success = false;
            }
            if (!success) {
                if (absolute) {
                    changeCurrentDirectory("/");
                }
                log.trace("Trying to build remote directory: {}", directory);
                success = buildDirectoryChunks(directory);
            }

            return success;

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } finally {
            dirStack = (Stack<ShareDirectoryClient>) backup;
        }

    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        boolean answer;
        var backup = dirStack.clone();

        try {
            log.trace("retrieveFile({})", name);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
                // local work directory is configured so we should store file
                // content as files in this local directory
                answer = retrieveFileToFileInLocalWorkDirectory(name, exchange, endpoint.isResumeDownload());
            } else {
                // store file content directory as stream on the body
                answer = retrieveFileToStreamInBody(name, exchange);
            }
        } catch (GenericFileOperationFailedException e) {
            throw e;
        } finally {
            dirStack = (Stack<ShareDirectoryClient>) backup;
        }

        return answer;
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getHeader(FilesHeaders.REMOTE_FILE_INPUT_STREAM, InputStream.class);

        if (is != null) {
            IOHelper.close(is);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        try {
            GenericFile<ShareFileItem> target
                    = (GenericFile<ShareFileItem>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target,
                    "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            // change directory to path where the file is to be retrieved
            // (must do this as some FTP servers cannot retrieve using
            // absolute path)
            String path = FileUtil.onlyPath(name);
            if (path != null) {
                changeCurrentDirectory(path);
            }
            // remote name is now only the file name as we just changed
            // directory
            String remoteName = FileUtil.stripPath(name);

            log.trace("Client retrieveFile: {}", remoteName);
            if (endpoint.getConfiguration().isStreamDownload()) {
                InputStream is = cwd().getFileClient(remoteName).openInputStream();
                target.setBody(is);
                exchange.getIn().setHeader(FilesHeaders.REMOTE_FILE_INPUT_STREAM, is);
            } else {
                // read the entire file into memory in the byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                cwd().getFileClient(remoteName).download(bos);
                // close the stream after done
                IOHelper.close(bos);

                target.setBody(bos.toByteArray());
            }

        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange, boolean resumeDownload)
            throws GenericFileOperationFailedException {
        File temp;
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        long existingSize = -1;

        try {
            // use relative filename in local work directory
            GenericFile<ShareFileItem> target
                    = (GenericFile<ShareFileItem>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target,
                    "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            String relativeName = target.getRelativeFilePath();

            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            boolean result = local.mkdirs();
            if (!result) {
                log.warn(
                        "Failed to create local directory {} while retrieving file in local work directory. Directory may already exist or have been created externally",
                        local);
            }

            // delete any local file (as its the temp file that is in the
            // in-progress download)
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // if a previous file exists then store its current size as its a
            // partial download
            boolean exists = temp.exists();
            if (exists) {
                existingSize = temp.length();
            }

            // if we do not resume download, then delete any existing temp file
            // and create a new to use for in-progress download
            if (!resumeDownload) {
                // delete any existing files
                if (exists && !FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
                // create new temp local work file
                if (!temp.createNewFile()) {
                    throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
                }
            }

            // store content as a file in the local work directory in the temp
            // handle
            boolean append = resumeDownload && existingSize > 0;
            os = new FileOutputStream(temp, append);

            // set header with the path to the local work file
            exchange.getIn().setHeader(FilesHeaders.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }

        boolean result;
        try {
            GenericFile<ShareFileItem> target
                    = (GenericFile<ShareFileItem>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            // store the java.io.File handle as the body
            target.setBody(local);

            // change directory to path where the file is to be retrieved
            // (must do this as some FTP servers cannot retrieve using
            // absolute path)
            String path = FileUtil.onlyPath(name);
            if (path != null) {
                changeCurrentDirectory(path);
            }
            // remote name is now only the file name as we just changed
            // directory
            String remoteName = FileUtil.stripPath(name);

            ShareFileRange range = new ShareFileRange(0);
            // the file exists so lets try to resume the download
            if (resumeDownload && existingSize > 0) {
                log.trace("Client restartOffset: {}", existingSize);
                log.debug("Resuming download of file: {} at position: {}", remoteName, existingSize);
                range = new ShareFileRange(existingSize);
            }
            log.trace("Client retrieveFile: {}", remoteName);
            var ret = cwd().getFileClient(remoteName).downloadWithResponse(os, range, false, Duration.ofSeconds(300),
                    Context.NONE);
            result = ret.getStatusCode() == 200;

        } catch (RuntimeException e) {
            log.trace("Error occurred during retrieving file: {} to local directory.", name);
            // if we do not attempt to resume download, then attempt to delete
            // the temporary file
            if (!resumeDownload) {
                log.trace("Deleting local work file: {}", name);
                // failed to retrieve the file so we need to close streams and
                // delete in progress file
                // must close stream before deleting file
                IOHelper.close(os, "retrieve: " + name, log);
                boolean deleted = FileUtil.deleteFile(temp);
                if (!deleted) {
                    log.warn("Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                            temp, name);
                }
            }
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } finally {
            // need to close the stream before rename it
            IOHelper.close(os, "retrieve: " + name, log);
        }

        log.debug("Retrieve file to local work file result: {}", result);

        if (result) {
            log.trace("Renaming local in progress file from: {} to: {}", temp, local);
            // operation went okay so rename temp to local after we have
            // retrieved the data
            try {
                if (!FileUtil.renameFile(temp, local, false)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot rename local work file from: " + temp + " to: " + local);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        "Cannot rename local work file from: " + temp + " to: " + local, e);
            }
        }

        return result;
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        // must normalize name first
        name = endpoint.getConfiguration().normalizePath(name);

        log.trace("storeFile({})", name);

        boolean answer;
        String path = FileUtil.onlyPath(name);
        String targetName = name;
        var backup = dirStack.clone();

        try {
            if (path != null) {

                // change to path of name
                changeCurrentDirectory(path);

                // the target name should be without path, as we have changed
                // directory
                targetName = FileUtil.stripPath(name);
            }

            // store the file
            answer = doStoreFile(name, targetName, exchange);
        } catch (GenericFileOperationFailedException e) {
            throw e;
        } finally {
            dirStack = (Stack<ShareDirectoryClient>) backup;
        }

        return answer;
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("doStoreFile({})", targetName);

        boolean existFile = false;
        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move || endpoint.getFileExist() == GenericFileExist.Append) {
            existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                log.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, targetName);
            }
        }

        InputStream is = null;
        int length = 0;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                log.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[] {});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                String charset = endpoint.getCharset();
                if (charset != null) {
                    // charset configured so we must convert to the desired
                    // charset so we can write with encoding
                    is = new ByteArrayInputStream(exchange.getIn().getMandatoryBody(String.class).getBytes(charset));
                    log.trace("Using InputStream {} with charset {}.", is, charset);
                } else {
                    // is = exchange.getIn().getMandatoryBody(InputStream.class);
                    // because we need length
                    var bytes = exchange.getIn().getMandatoryBody(byte[].class);
                    length = bytes.length;
                    is = new ByteArrayInputStream(bytes);
                }
            }

            final StopWatch watch = new StopWatch();
            boolean answer;
            log.debug("About to store file: {} using stream: {}", targetName, is);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Append) {
                log.trace("Client appendFile: {}", targetName);
                // TODO
                answer = false;
            } else {
                log.trace("Client storeFile: {}", targetName);
                var file = cwd().getFileClient(targetName);
                file.deleteIfExists();
                file.create(length);
                is.transferTo(file.getFileOutputStream());
                answer = true;
            }
            if (log.isDebugEnabled()) {
                long time = watch.taken();
                log.debug("Took {} ({} millis) to store file: {} and FTP client returned: {}",
                        TimeUtils.printDuration(time, true), time, targetName, answer);
            }

            // after storing file, we may set chmod on the file

            return answer;

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        log.trace("existsFile({})", name);
        // check whether a file already exists
        String directory = FileUtil.onlyPath(name);
        String onlyName = FileUtil.stripPath(name);
        var backup = dirStack.clone();
        try {
            if (directory != null) {
                changeCurrentDirectory(directory);
            }
            var file = cwd().getFileClient(onlyName);
            return file.exists();
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } finally {
            dirStack = (Stack<ShareDirectoryClient>) backup;
        }
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        log.trace("getCurrentDirectory()");
        String answer = cwd().getDirectoryPath();
        log.trace("Current dir: {}", answer);
        return answer;
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        log.trace("changeCurrentDirectory({})", path);
        if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
            return;
        }

        // must compact path so FTP server can traverse correctly
        // use the ftp utils implementation of the compact path
        path = FtpUtils.compactPath(path);

        // if it starts with the root path then a little special handling for
        // that
        if (FileUtil.hasLeadingSeparator(path)) {
            // change to root path
            doChangeDirectory(path.substring(0, 1));
            path = path.substring(1);
        }

        // split into multiple dirs
        final String[] dirs = path.split("/|\\\\");

        if (dirs == null || dirs.length == 0) {
            // path was just a relative single path
            doChangeDirectory(path);
            return;
        }

        // there are multiple dirs so do this in chunks
        for (String dir : dirs) {
            doChangeDirectory(dir);
        }
    }

    private void doChangeDirectory(String pathStep) {
        if (pathStep == null || ".".equals(pathStep) || org.apache.camel.util.ObjectHelper.isEmpty(pathStep)) {
            return;
        }

        log.trace("Changing directory: {}", pathStep);
        boolean success;
        try {
            if ("..".equals(pathStep)) {
                changeToParentDirectory();
                success = true;
            } else {
                var subDir = cwd().getSubdirectoryClient(pathStep);
                success = subDir != null;
                if (success) {
                    dirStack.push(subDir);
                }
            }
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
        if (!success) {
            throw new GenericFileOperationFailedException(
                    "Cannot change directory to: " + pathStep);
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        try {
            dirStack.pop();
        } catch (EmptyStackException e) {
            throw new GenericFileOperationFailedException("Root dir does not have parent.", e);
        }
    }

    @Override
    public ShareFileItem[] listFiles() throws GenericFileOperationFailedException {
        log.trace("Listing remote files");
        try {
            return (ShareFileItem[]) cwd().listFilesAndDirectories().stream().toArray();
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
    }

    @Override
    public ShareFileItem[] listFiles(String path) throws GenericFileOperationFailedException {
        log.trace("Listing remote files from path {}", path);

        // use current directory if path not given
        if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
            path = ".";
        }

        var backup = dirStack.clone();
        try {
            changeCurrentDirectory(path);
            return listFiles();
        } catch (RuntimeException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } finally {
            dirStack = (Stack<ShareDirectoryClient>) backup;
        }
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        log.trace("sendNoOp");
        return root.exists();
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        log.trace("sendSiteCommand({})", command);
        // TODO any use
        return true;
    }

    private boolean buildDirectoryChunks(String dirName) throws IOException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("/|\\\\");

        boolean success = false;
        for (String dir : dirs) {
            sb.append(dir).append('/');
            // must normalize the directory name
            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            // do not try to build root folder (/ or \)
            if (!(directory.equals("/") || directory.equals("\\"))) {
                log.trace("Trying to build remote directory by chunk: {}", directory);

                // while creating directory string if directory results in
                // trailing slash, remove it not necessary
                directory = FileUtil.stripTrailingSeparator(directory);

                var subDir = cwd().createSubdirectoryIfNotExists(directory);
                success = subDir != null;
                if (success) {
                    dirStack.push(subDir);
                } else {
                    break;
                }
            }
        }

        return success;
    }

    public ShareServiceClient getClient() {
        return client;
    }

    private void reconnectIfNecessary(Exchange exchange) throws GenericFileOperationFailedException {
        boolean reconnectRequired;
        try {
            boolean connected = isConnected();
            if (connected && !sendNoop()) {
                reconnectRequired = true;
            } else {
                reconnectRequired = !connected;
            }
        } catch (GenericFileOperationFailedException e) {
            // Ignore Exception and reconnect the client
            reconnectRequired = true;
        }
        if (reconnectRequired) {
            log.trace("Client is not connected anymore, try to reconnect");
            connect(endpoint.getConfiguration(), exchange);
        }
    }

}
