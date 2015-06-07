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
package org.apache.camel.component.file.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FTP remote file operations
 */
public class FtpOperations implements RemoteFileOperations<FTPFile> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final FTPClient client;
    protected final FTPClientConfig clientConfig;
    protected RemoteFileEndpoint<FTPFile> endpoint;

    public FtpOperations(FTPClient client, FTPClientConfig clientConfig) {
        this.client = client;
        this.clientConfig = clientConfig;
    }

    public void setEndpoint(GenericFileEndpoint<FTPFile> endpoint) {
        this.endpoint = (RemoteFileEndpoint<FTPFile>) endpoint;
    }

    public boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException {
        log.trace("Connecting using FTPClient: {}", client);

        String host = configuration.getHost();
        int port = configuration.getPort();
        String username = configuration.getUsername();
        String account = ((FtpConfiguration) configuration).getAccount();

        if (clientConfig != null) {
            log.trace("Configuring FTPClient with config: {}", clientConfig);
            client.configure(clientConfig);
        }

        if (log.isTraceEnabled()) {
            log.trace("Connecting to {} using connection timeout: {}",
                    configuration.remoteServerInformation(), client.getConnectTimeout());
        }

        boolean connected = false;
        int attempt = 0;

        while (!connected) {
            try {
                if (log.isTraceEnabled() && attempt > 0) {
                    log.trace("Reconnect attempt #{} connecting to {}", attempt, configuration.remoteServerInformation());
                }
                client.connect(host, port);
                // must check reply code if we are connected
                int reply = client.getReplyCode();

                if (FTPReply.isPositiveCompletion(reply)) {
                    // yes we could connect
                    connected = true;
                } else {
                    // throw an exception to force the retry logic in the catch exception block
                    throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), "Server refused connection");
                }
            } catch (Exception e) {
                // check if we are interrupted so we can break out
                if (Thread.currentThread().isInterrupted()) {
                    throw new GenericFileOperationFailedException("Interrupted during connecting", new InterruptedException("Interrupted during connecting"));
                }

                GenericFileOperationFailedException failed;
                if (e instanceof GenericFileOperationFailedException) {
                    failed = (GenericFileOperationFailedException) e;
                } else {
                    failed = new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
                }

                log.trace("Cannot connect due: {}", failed.getMessage());
                attempt++;
                if (attempt > endpoint.getMaximumReconnectAttempts()) {
                    throw failed;
                }
                if (endpoint.getReconnectDelay() > 0) {
                    try {
                        Thread.sleep(endpoint.getReconnectDelay());
                    } catch (InterruptedException ie) {
                        // we could potentially also be interrupted during sleep
                        Thread.currentThread().interrupt();
                        throw new GenericFileOperationFailedException("Interrupted during sleeping", ie);
                    }
                }
            }
        }

        // must enter passive mode directly after connect
        if (configuration.isPassiveMode()) {
            log.trace("Using passive mode connections");
            client.enterLocalPassiveMode();
        }

        // must set soTimeout after connect
        if (endpoint instanceof FtpEndpoint) {
            FtpEndpoint<?> ftpEndpoint = (FtpEndpoint<?>) endpoint;
            if (ftpEndpoint.getSoTimeout() > 0) {
                log.trace("Using SoTimeout=" + ftpEndpoint.getSoTimeout());
                try {
                    client.setSoTimeout(ftpEndpoint.getSoTimeout());
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
                }
            }
        }

        try {
            boolean login;
            if (username != null) {
                if (account != null) {
                    log.trace("Attempting to login user: {} using password: {} and account: {}", new Object[]{username, configuration.getPassword(), account});
                    login = client.login(username, configuration.getPassword(), account);
                } else {
                    log.trace("Attempting to login user: {} using password: {}", username, configuration.getPassword());
                    login = client.login(username, configuration.getPassword());
                }
            } else {
                if (account != null) {
                    // not sure if it makes sense to login anonymous with account?
                    log.trace("Attempting to login anonymous using account: {}", account);
                    login = client.login("anonymous", "", account);
                } else {
                    log.trace("Attempting to login anonymous");
                    login = client.login("anonymous", "");
                }
            }
            log.trace("User {} logged in: {}", username != null ? username : "anonymous", login);
            if (!login) {
                // disconnect to prevent connection leaks
                client.disconnect();
                throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString());
            }
            client.setFileType(configuration.isBinary() ? FTP.BINARY_FILE_TYPE : FTP.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        // site commands
        if (endpoint.getConfiguration().getSiteCommand() != null) {
            // commands can be separated using new line
            Iterator<?> it = ObjectHelper.createIterator(endpoint.getConfiguration().getSiteCommand(), "\n");
            while (it.hasNext()) {
                Object next = it.next();
                String command = endpoint.getCamelContext().getTypeConverter().convertTo(String.class, next);
                log.trace("Site command to send: {}", command);
                if (command != null) {
                    boolean result = sendSiteCommand(command);
                    if (!result) {
                        throw new GenericFileOperationFailedException("Site command: " + command + " returned false");
                    }
                }
            }
        }

        return true;
    }

    public boolean isConnected() throws GenericFileOperationFailedException {
        return client.isConnected();
    }

    public void disconnect() throws GenericFileOperationFailedException {
        // logout before disconnecting
        try {
            log.trace("Client logout");
            client.logout();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            try {
                log.trace("Client disconnect");
                client.disconnect();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        }
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        log.debug("Deleting file: {}", name);

        boolean result;
        String target = name;
        String currentDir = null;

        try {
            if (endpoint.getConfiguration().isStepwise()) {
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
            }

            // delete the file
            log.trace("Client deleteFile: {}", target);
            result = client.deleteFile(target);

            // change back to previous directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        return result;
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        log.debug("Renaming file: {} to: {}", from, to);
        try {
            return client.rename(from, to);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        // must normalize directory first
        directory = endpoint.getConfiguration().normalizePath(directory);

        log.trace("buildDirectory({})", directory);
        try {
            String originalDirectory = client.printWorkingDirectory();

            boolean success;
            try {
                // maybe the full directory already exists
                success = client.changeWorkingDirectory(directory);
                if (!success) {
                    log.trace("Trying to build remote directory: {}", directory);
                    success = client.makeDirectory(directory);
                    if (!success) {
                        // we are here if the server side doesn't create intermediate folders so create the folder one by one
                        success = buildDirectoryChunks(directory);
                    }
                }

                return success;
            } finally {
                // change back to original directory
                if (originalDirectory != null) {
                    changeCurrentDirectory(originalDirectory);
                }
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean retrieveFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("retrieveFile({})", name);
        if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
            // local work directory is configured so we should store file content as files in this local directory
            return retrieveFileToFileInLocalWorkDirectory(name, exchange);
        } else {
            // store file content directory as stream on the body
            return retrieveFileToStreamInBody(name, exchange);
        }
    }
    
    @Override
    public void releaseRetreivedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, InputStream.class);
        
        if (is != null) {
            try {
                is.close();
                client.completePendingCommand();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        OutputStream os = null;
        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            
            String remoteName = name;
            String currentDir = null;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed directory
                remoteName = FileUtil.stripPath(name);
            }

            log.trace("Client retrieveFile: {}", remoteName);
            
            if (endpoint.getConfiguration().isStreamDownload()) {
                InputStream is = client.retrieveFileStream(remoteName); 
                target.setBody(is);
                exchange.getIn().setHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, is);
                result = true;
            } else {
                os = new ByteArrayOutputStream();
                target.setBody(os);
                result = client.retrieveFile(remoteName, os);
            }

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

            // change back to current directory
            if (endpoint.getConfiguration().isStepwise()) {
                changeCurrentDirectory(currentDir);
            }

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            IOHelper.close(os, "retrieve: " + name, log);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange) throws GenericFileOperationFailedException {
        File temp;
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        try {
            // use relative filename in local work directory
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            String relativeName = target.getRelativeFilePath();

            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            local.mkdirs();

            // delete any existing files
            if (temp.exists()) {
                if (!FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
            }
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // create new temp local work file
            if (!temp.createNewFile()) {
                throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
            }

            // store content as a file in the local work directory in the temp handle
            os = new FileOutputStream(temp);

            // set header with the path to the local work file            
            exchange.getIn().setHeader(Exchange.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local);
        }

        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            // store the java.io.File handle as the body
            target.setBody(local);

            String remoteName = name;
            String currentDir = null;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed directory
                remoteName = FileUtil.stripPath(name);
            }

            log.trace("Client retrieveFile: {}", remoteName);
            result = client.retrieveFile(remoteName, os);

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

            // change back to current directory
            if (endpoint.getConfiguration().isStepwise()) {
                changeCurrentDirectory(currentDir);
            }

        } catch (IOException e) {
            log.trace("Error occurred during retrieving file: {} to local directory. Deleting local work file: {}", name, temp);
            // failed to retrieve the file so we need to close streams and delete in progress file
            // must close stream before deleting file
            IOHelper.close(os, "retrieve: " + name, log);
            boolean deleted = FileUtil.deleteFile(temp);
            if (!deleted) {
                log.warn("Error occurred during retrieving file: " + name + " to local directory. Cannot delete local work file: " + temp);
            }
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            // need to close the stream before rename it
            IOHelper.close(os, "retrieve: " + name, log);
        }

        log.debug("Retrieve file to local work file result: {}", result);

        if (result) {
            log.trace("Renaming local in progress file from: {} to: {}", temp, local);
            // operation went okay so rename temp to local after we have retrieved the data
            try {
                if (!FileUtil.renameFile(temp, local, false)) {
                    throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local, e);
            }
        }

        return result;
    }

    public boolean storeFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        // must normalize name first
        name = endpoint.getConfiguration().normalizePath(name);

        log.trace("storeFile({})", name);

        boolean answer = false;
        String currentDir = null;
        String path = FileUtil.onlyPath(name);
        String targetName = name;

        try {
            if (path != null && endpoint.getConfiguration().isStepwise()) {
                // must remember current dir so we stay in that directory after the write
                currentDir = getCurrentDirectory();

                // change to path of name
                changeCurrentDirectory(path);

                // the target name should be without path, as we have changed directory
                targetName = FileUtil.stripPath(name);
            }

            // store the file
            answer = doStoreFile(name, targetName, exchange);
        } finally {
            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
        }

        return answer;
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("doStoreFile({})", targetName);

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore
                || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                log.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                doMoveExistingFile(name, targetName);
            }
        }

        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                log.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[]{});
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
                    is = exchange.getIn().getMandatoryBody(InputStream.class);
                }
            }

            final StopWatch watch = new StopWatch();
            boolean answer;
            log.debug("About to store file: {} using stream: {}", targetName, is);
            if (endpoint.getFileExist() == GenericFileExist.Append) {
                log.trace("Client appendFile: {}", targetName);
                answer = client.appendFile(targetName, is);
            } else {
                log.trace("Client storeFile: {}", targetName);
                answer = client.storeFile(targetName, is);
            }
            watch.stop();
            if (log.isDebugEnabled()) {
                log.debug("Took {} ({} millis) to store file: {} and FTP client returned: {}",
                        new Object[]{TimeUtils.printDuration(watch.taken()), watch.taken(), targetName, answer});
            }

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

            return answer;

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    /**
     * Moves any existing file due fileExists=Move is in use.
     */
    private void doMoveExistingFile(String name, String targetName) throws GenericFileOperationFailedException {
        // need to evaluate using a dummy and simulate the file first, to have access to all the file attributes
        // create a dummy exchange as Exchange is needed for expression evaluation
        // we support only the following 3 tokens.
        Exchange dummy = endpoint.createExchange();
        // we only support relative paths for the ftp component, so dont provide any parent
        String parent = null;
        String onlyName = FileUtil.stripPath(targetName);
        dummy.getIn().setHeader(Exchange.FILE_NAME, targetName);
        dummy.getIn().setHeader(Exchange.FILE_NAME_ONLY, onlyName);
        dummy.getIn().setHeader(Exchange.FILE_PARENT, parent);

        String to = endpoint.getMoveExisting().evaluate(dummy, String.class);
        // we only support relative paths for the ftp component, so strip any leading paths
        to = FileUtil.stripLeadingSeparator(to);
        // normalize accordingly to configuration
        to = endpoint.getConfiguration().normalizePath(to);
        if (ObjectHelper.isEmpty(to)) {
            throw new GenericFileOperationFailedException("moveExisting evaluated as empty String, cannot move existing file: " + name);
        }

        // do we have a sub directory
        String dir = FileUtil.onlyPath(to);
        if (dir != null) {
            // ensure directory exists
            buildDirectory(dir, false);
        }

        // deal if there already exists a file
        if (existsFile(to)) {
            if (endpoint.isEagerDeleteTargetFile()) {
                log.trace("Deleting existing file: {}", to);
                boolean result;
                try {
                    result = client.deleteFile(to);
                    if (!result) {
                        throw new GenericFileOperationFailedException("Cannot delete file: " + to);
                    }
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), "Cannot delete file: " + to, e);
                }
            } else {
                throw new GenericFileOperationFailedException("Cannot moved existing file from: " + name + " to: " + to + " as there already exists a file: " + to);
            }
        }

        log.trace("Moving existing file: {} to: {}", name, to);
        if (!renameFile(targetName, to)) {
            throw new GenericFileOperationFailedException("Cannot rename file from: " + name + " to: " + to);
        }
    }

    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        log.trace("existsFile({})", name);
        if (endpoint.isFastExistsCheck()) {
            return fastExistsFile(name);
        }
        // check whether a file already exists
        String directory = FileUtil.onlyPath(name);
        String onlyName = FileUtil.stripPath(name);
        try {
            String[] names;
            if (directory != null) {
                names = client.listNames(directory);
            } else {
                names = client.listNames();
            }
            // can return either null or an empty list depending on FTP servers
            if (names == null) {
                return false;
            }
            for (String existing : names) {
                log.trace("Existing file: {}, target file: {}", existing, name);
                existing = FileUtil.stripPath(existing);
                if (existing != null && existing.equals(onlyName)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    protected boolean fastExistsFile(String name) throws GenericFileOperationFailedException {
        log.trace("fastExistsFile({})", name);
        try {
            String[] names = client.listNames(name);
            if (names == null) {
                return false;
            }
            return names.length >= 1;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        log.trace("getCurrentDirectory()");
        try {
            String answer = client.printWorkingDirectory();
            log.trace("Current dir: {}", answer);
            return answer;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        log.trace("changeCurrentDirectory({})", path);
        if (ObjectHelper.isEmpty(path)) {
            return;
        }

        // must compact path so FTP server can traverse correctly
        // use the ftp utils implementation of the compact path
        path = FtpUtils.compactPath(path);

        // not stepwise should change directory in one operation
        if (!endpoint.getConfiguration().isStepwise()) {
            doChangeDirectory(path);
            return;
        }

        // if it starts with the root path then a little special handling for that
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

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || ObjectHelper.isEmpty(path)) {
            return;
        }

        log.trace("Changing directory: {}", path);
        boolean success;
        try {
            if ("..".equals(path)) {
                changeToParentDirectory();
                success = true;
            } else {
                success = client.changeWorkingDirectory(path);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
        if (!success) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), "Cannot change directory to: " + path);
        }
    }

    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        try {
            client.changeToParentDirectory();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public List<FTPFile> listFiles() throws GenericFileOperationFailedException {
        log.trace("listFiles()");
        try {
            final List<FTPFile> list = new ArrayList<FTPFile>();
            FTPFile[] files = client.listFiles();
            // can return either null or an empty list depending on FTP servers
            if (files != null) {
                list.addAll(Arrays.asList(files));
            }
            return list;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public List<FTPFile> listFiles(String path) throws GenericFileOperationFailedException {
        log.trace("listFiles({})", path);
        // use current directory if path not given
        if (ObjectHelper.isEmpty(path)) {
            path = ".";
        }

        try {
            final List<FTPFile> list = new ArrayList<FTPFile>();
            FTPFile[] files = client.listFiles(path);
            // can return either null or an empty list depending on FTP servers
            if (files != null) {
                list.addAll(Arrays.asList(files));
            }
            return list;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean sendNoop() throws GenericFileOperationFailedException {
        log.trace("sendNoOp");
        try {
            return client.sendNoOp();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        log.trace("sendSiteCommand({})", command);
        try {
            return client.sendSiteCommand(command);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    protected FTPClient getFtpClient() {
        return client;
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

                success = client.makeDirectory(directory);
            }
        }

        return success;
    }

}
