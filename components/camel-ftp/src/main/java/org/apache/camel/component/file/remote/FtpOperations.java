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
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
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
    protected FtpEndpoint<FTPFile> endpoint;
    protected FtpClientActivityListener clientActivityListener;

    public FtpOperations(FTPClient client, FTPClientConfig clientConfig) {
        this.client = client;
        this.clientConfig = clientConfig;
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<FTPFile> endpoint) {
        this.endpoint = (FtpEndpoint<FTPFile>)endpoint;
        // setup download listener/logger when we have the endpoint configured
        this.clientActivityListener = new DefaultFtpClientActivityListener(this.endpoint, this.endpoint.getConfiguration().remoteServerInformation());
    }

    public FtpClientActivityListener getClientActivityListener() {
        return clientActivityListener;
    }

    public void setClientActivityListener(FtpClientActivityListener clientActivityListener) {
        this.clientActivityListener = clientActivityListener;
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange) throws GenericFileOperationFailedException {
        client.setCopyStreamListener(clientActivityListener);

        try {
            return doConnect(configuration, exchange);
        } catch (GenericFileOperationFailedException e) {
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw e;
        }
    }

    protected boolean doConnect(RemoteFileConfiguration configuration, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("Connecting using FTPClient: {}", client);

        String host = configuration.getHost();
        int port = configuration.getPort();
        String username = configuration.getUsername();
        String account = ((FtpConfiguration)configuration).getAccount();

        if (clientConfig != null) {
            log.trace("Configuring FTPClient with config: {}", clientConfig);
            client.configure(clientConfig);
        }

        if (log.isTraceEnabled()) {
            log.trace("Connecting to {} using connection timeout: {}", configuration.remoteServerInformation(), client.getConnectTimeout());
        }

        boolean connected = false;
        int attempt = 0;

        while (!connected) {
            try {
                if (log.isTraceEnabled() && attempt > 0) {
                    log.trace("Reconnect attempt #{} connecting to {}", attempt, configuration.remoteServerInformation());
                }
                clientActivityListener.onConnecting(host);
                client.connect(host, port);
                // must check reply code if we are connected
                int reply = client.getReplyCode();

                if (FTPReply.isPositiveCompletion(reply)) {
                    // yes we could connect
                    connected = true;
                } else {
                    // throw an exception to force the retry logic in the catch
                    // exception block
                    throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), "Server refused connection");
                }
            } catch (Exception e) {
                if (client.isConnected()) {
                    log.trace("Disconnecting due to exception during connect");
                    try {
                        client.disconnect(); // ensures socket is closed
                    } catch (IOException ignore) {
                        log.trace("Ignore exception during disconnect: {}", ignore.getMessage());
                    }
                }
                // check if we are interrupted so we can break out
                if (Thread.currentThread().isInterrupted()) {
                    throw new GenericFileOperationFailedException("Interrupted during connecting", new InterruptedException("Interrupted during connecting"));
                }

                GenericFileOperationFailedException failed;
                if (e instanceof GenericFileOperationFailedException) {
                    failed = (GenericFileOperationFailedException)e;
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
            } finally {
                if (exchange != null) {
                    // store client reply information after the operation
                    exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
                    exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());
                }
            }
        }

        // we are now connected
        clientActivityListener.onConnected(host);

        // must enter passive mode directly after connect
        if (configuration.isPassiveMode()) {
            log.trace("Using passive mode connections");
            client.enterLocalPassiveMode();
        }

        // must set soTimeout after connect
        if (endpoint.getSoTimeout() > 0) {
            log.trace("Using SoTimeout={}", endpoint.getSoTimeout());
            try {
                client.setSoTimeout(endpoint.getSoTimeout());
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        }

        try {
            clientActivityListener.onLogin(host);
            boolean login;
            if (username != null) {
                if (account != null) {
                    log.trace("Attempting to login user: {} using password: ******** and account: {}", username, account);
                    login = client.login(username, configuration.getPassword(), account);
                } else {
                    log.trace("Attempting to login user: {} using password: ********", username);
                    login = client.login(username, configuration.getPassword());
                }
            } else {
                if (account != null) {
                    // not sure if it makes sense to login anonymous with
                    // account?
                    log.trace("Attempting to login anonymous using account: {}", account);
                    login = client.login("anonymous", "", account);
                } else {
                    log.trace("Attempting to login anonymous");
                    login = client.login("anonymous", "");
                }
            }
            log.trace("User {} logged in: {}", username != null ? username : "anonymous", login);
            if (!login) {
                // store replyString, because disconnect() will reset it
                String replyString = client.getReplyString();
                int replyCode = client.getReplyCode();
                clientActivityListener.onLoginFailed(replyCode, replyString);
                // disconnect to prevent connection leaks
                client.disconnect();
                throw new GenericFileOperationFailedException(replyCode, replyString);
            }
            clientActivityListener.onLoginComplete(host);
            client.setFileType(configuration.isBinary() ? FTP.BINARY_FILE_TYPE : FTP.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            if (exchange != null) {
                // store client reply information after the operation
                exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
                exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());
            }
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

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        return client.isConnected();
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        try {
            doDisconnect();
        } catch (GenericFileOperationFailedException e) {
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw e;
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        doDisconnect();
    }

    protected void doDisconnect() throws GenericFileOperationFailedException {
        // logout before disconnecting
        clientActivityListener.onDisconnecting(endpoint.getConfiguration().remoteServerInformation());
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
        clientActivityListener.onDisconnected(endpoint.getConfiguration().remoteServerInformation());
    }

    @Override
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

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        log.debug("Renaming file: {} to: {}", from, to);
        try {
            return client.rename(from, to);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
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
                        // we are here if the server side doesn't create
                        // intermediate folders so create the folder one by one
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

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        // store the name of the file to download on the listener
        clientActivityListener.setDownload(true);
        clientActivityListener.setRemoteFileName(name);
        clientActivityListener.setRemoteFileSize(size);
        clientActivityListener.onBeginDownloading(endpoint.getConfiguration().remoteServerInformation(), name);

        boolean answer;
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
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw e;
        }

        if (answer) {
            clientActivityListener.onDownloadComplete(endpoint.getConfiguration().remoteServerInformation(), name);
        }
        return answer;
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, InputStream.class);

        if (is != null) {
            try {
                IOHelper.close(is);
                client.completePendingCommand();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        if (endpoint.getConfiguration().isStepwise() && endpoint.getConfiguration().isStreamDownload()) {
            //stepwise and streamDownload are not supported together
            throw new IllegalArgumentException("The option stepwise is not supported for stream downloading");
        }
        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            String remoteName = name;
            String currentDir = null;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using
                // absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed
                // directory
                remoteName = FileUtil.stripPath(name);
            }

            log.trace("Client retrieveFile: {}", remoteName);
            if (endpoint.getConfiguration().isStreamDownload()) {
                InputStream is = client.retrieveFileStream(remoteName);
                target.setBody(is);
                exchange.getIn().setHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, is);
                result = true;
            } else {
                // read the entire file into memory in the byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                result = client.retrieveFile(remoteName, bos);
                // close the stream after done
                IOHelper.close(bos);

                target.setBody(bos.toByteArray());
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
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange, boolean resumeDownload) throws GenericFileOperationFailedException {
        File temp;
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        long existingSize = -1;

        try {
            // use relative filename in local work directory
            GenericFile<FTPFile> target = (GenericFile<FTPFile>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            String relativeName = target.getRelativeFilePath();

            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            local.mkdirs();

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
            exchange.getIn().setHeader(Exchange.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local);
        }

        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            // store the java.io.File handle as the body
            target.setBody(local);

            String remoteName = name;
            String currentDir = null;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using
                // absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed
                // directory
                remoteName = FileUtil.stripPath(name);
            }

            // the file exists so lets try to resume the download
            if (resumeDownload && existingSize > 0) {
                clientActivityListener.onResumeDownloading(endpoint.getConfiguration().remoteServerInformation(), name, existingSize);
                log.trace("Client restartOffset: {}", existingSize);
                log.debug("Resuming download of file: {} at position: {}", remoteName, existingSize);
                client.setRestartOffset(existingSize);
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
                    log.warn("Error occurred during retrieving file: " + name + " to local directory. Cannot delete local work file: " + temp);
                }
            }
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
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
                    throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local, e);
            }
        }

        return result;
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        // must normalize name first
        name = endpoint.getConfiguration().normalizePath(name);

        // store the name of the file to upload on the listener
        clientActivityListener.setDownload(false);
        clientActivityListener.setRemoteFileName(name);
        clientActivityListener.setRemoteFileSize(size);
        clientActivityListener.onBeginUploading(endpoint.getConfiguration().remoteServerInformation(), name);

        log.trace("storeFile({})", name);

        boolean answer;
        String currentDir = null;
        String path = FileUtil.onlyPath(name);
        String targetName = name;

        try {
            if (path != null && endpoint.getConfiguration().isStepwise()) {
                // must remember current dir so we stay in that directory after
                // the write
                currentDir = getCurrentDirectory();

                // change to path of name
                changeCurrentDirectory(path);

                // the target name should be without path, as we have changed
                // directory
                targetName = FileUtil.stripPath(name);
            }

            // store the file
            answer = doStoreFile(name, targetName, exchange);
        } catch (GenericFileOperationFailedException e) {
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw e;
        } finally {
            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
        }

        if (answer) {
            clientActivityListener.onUploadComplete(endpoint.getConfiguration().remoteServerInformation(), name);
        }

        return answer;
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("doStoreFile({})", targetName);

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(targetName);
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
            if (log.isDebugEnabled()) {
                long time = watch.taken();
                log.debug("Took {} ({} millis) to store file: {} and FTP client returned: {}", new Object[] {TimeUtils.printDuration(time), time, targetName, answer});
            }

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

            // after storing file, we may set chmod on the file
            String chmod = endpoint.getConfiguration().getChmod();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(chmod)) {
                log.debug("Setting chmod: {} on file: {}", chmod, targetName);
                String command = "chmod " + chmod + " " + targetName;
                log.trace("Client sendSiteCommand: {}", command);
                boolean success = client.sendSiteCommand(command);
                log.trace("Client sendSiteCommand successful: {}", success);
            }

            return answer;

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    @Override
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

    @Override
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

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        log.trace("changeCurrentDirectory({})", path);
        if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
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

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || org.apache.camel.util.ObjectHelper.isEmpty(path)) {
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

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        try {
            client.changeToParentDirectory();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
    public List<FTPFile> listFiles() throws GenericFileOperationFailedException {
        log.trace("listFiles()");
        clientActivityListener.onScanningForFiles(endpoint.remoteServerInformation(), null);
        try {
            final List<FTPFile> list = new ArrayList<>();
            FTPFile[] files = client.listFiles();
            // can return either null or an empty list depending on FTP servers
            if (files != null) {
                list.addAll(Arrays.asList(files));
            }
            return list;
        } catch (IOException e) {
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
    public List<FTPFile> listFiles(String path) throws GenericFileOperationFailedException {
        log.trace("listFiles({})", path);
        clientActivityListener.onScanningForFiles(endpoint.remoteServerInformation(), path);

        // use current directory if path not given
        if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
            path = ".";
        }

        try {
            final List<FTPFile> list = new ArrayList<>();
            FTPFile[] files = client.listFiles(path);
            // can return either null or an empty list depending on FTP servers
            if (files != null) {
                list.addAll(Arrays.asList(files));
            }
            return list;
        } catch (IOException e) {
            clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        log.trace("sendNoOp");
        try {
            return client.sendNoOp();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
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

                // while creating directory string if directory results in
                // trailing slash, remove it not necessary
                directory = FileUtil.stripTrailingSeparator(directory);

                success = client.makeDirectory(directory);
            }
        }

        return success;
    }

    public FTPClient getClient() {
        return client;
    }

}
