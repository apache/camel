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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 * FTP remote file operations
 */
public class FtpOperations implements RemoteFileOperations<FTPFile> {
    
    protected final transient Log log = LogFactory.getLog(getClass());
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
        if (log.isTraceEnabled()) {
            log.trace("Connecting using FTPClient: " + client);
        }

        String host = configuration.getHost();
        int port = configuration.getPort();
        String username = configuration.getUsername();

        if (clientConfig != null) {
            log.trace("Configuring FTPClient with config: " + clientConfig);
            client.configure(clientConfig);
        }

        if (log.isTraceEnabled()) {
            log.trace("Connecting to " + configuration.remoteServerInformation() + " using connection timeout: " + client.getConnectTimeout());
        }

        boolean connected = false;
        int attempt = 0;

        while (!connected) {
            try {
                if (log.isTraceEnabled() && attempt > 0) {
                    log.trace("Reconnect attempt #" + attempt + " connecting to + " + configuration.remoteServerInformation());
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
                GenericFileOperationFailedException failed;
                if (e instanceof GenericFileOperationFailedException) {
                    failed = (GenericFileOperationFailedException) e;
                } else {
                    failed = new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
                }

                if (log.isTraceEnabled()) {
                    log.trace("Cannot connect due: " + failed.getMessage());
                }
                attempt++;
                if (attempt > endpoint.getMaximumReconnectAttempts()) {
                    throw failed;
                }
                if (endpoint.getReconnectDelay() > 0) {
                    try {
                        Thread.sleep(endpoint.getReconnectDelay());
                    } catch (InterruptedException ie) {
                        // ignore
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
            FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;
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
                if (log.isTraceEnabled()) {
                    log.trace("Attempting to login user: " + username + " using password: " + configuration.getPassword());
                }
                login = client.login(username, configuration.getPassword());
            } else {
                log.trace("Attempting to login anonymous");
                login = client.login("anonymous", null);
            }
            if (log.isTraceEnabled()) {
                log.trace("User " + (username != null ? username : "anonymous") + " logged in: " + login);
            }
            if (!login) {
                throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString());
            }
            client.setFileType(configuration.isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        return true;
    }

    public boolean isConnected() throws GenericFileOperationFailedException {
        return client.isConnected();
    }

    public void disconnect() throws GenericFileOperationFailedException {
        // logout before disconnecting
        try {
            client.logout();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            try {
                client.disconnect();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        }
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        if (log.isDebugEnabled()) {
            log.debug("Deleting file: " + name);
        }
        try {
            return this.client.deleteFile(name);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        if (log.isDebugEnabled()) {
            log.debug("Renaming file: " + from + " to: " + to);
        }
        try {
            return client.rename(from, to);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        if (log.isTraceEnabled()) {
            log.trace("Building directory: " + directory);
        }
        try {
            String originalDirectory = client.printWorkingDirectory();

            boolean success;
            try {
                // maybe the full directory already exists
                success = client.changeWorkingDirectory(directory);
                if (!success) {
                    if (log.isTraceEnabled()) {
                        log.trace("Trying to build remote directory: " + directory);
                    }
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
                    client.changeWorkingDirectory(originalDirectory);
                }
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean retrieveFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
            // local work directory is configured so we should store file content as files in this local directory
            return retrieveFileToFileInLocalWorkDirectory(name, exchange);
        } else {
            // store file content directory as stream on the body
            return retrieveFileToStreamInBody(name, exchange);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        OutputStream os = null;
        boolean result;
        try {
            os = new ByteArrayOutputStream();
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            target.setBody(os);

            // remember current directory
            String currentDir = getCurrentDirectory();

            // change directory to path where the file is to be retrieved
            // (must do this as some FTP servers cannot retrieve using absolute path)
            String path = FileUtil.onlyPath(name);
            if (path != null) {
                changeCurrentDirectory(path);
            }
            String onlyName = FileUtil.stripPath(name);

            result = client.retrieveFile(onlyName, os);

            // change back to current directory
            changeCurrentDirectory(currentDir);

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

            // remember current directory
            String currentDir = getCurrentDirectory();

            // change directory to path where the file is to be retrieved
            // (must do this as some FTP servers cannot retrieve using absolute path)
            String path = FileUtil.onlyPath(name);
            if (path != null) {
                changeCurrentDirectory(path);
            }
            String onlyName = FileUtil.stripPath(name);

            result = client.retrieveFile(onlyName, os);
            
            // change back to current directory
            changeCurrentDirectory(currentDir);

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            // need to close the stream before rename it
            IOHelper.close(os, "retrieve: " + name, log);
        }

        if (log.isDebugEnabled()) {
            log.debug("Retrieve file to local work file result: " + result);
        }

        if (result) {
            if (log.isTraceEnabled()) {
                log.trace("Renaming local in progress file from: " + temp + " to: " + local);
            }
            // operation went okay so rename temp to local after we have retrieved the data
            if (!FileUtil.renameFile(temp, local)) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
            }
        }

        return result;
    }

    public boolean storeFile(String name, Exchange exchange) throws GenericFileOperationFailedException {

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail) {
            boolean existFile = existsFile(name);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                if (log.isTraceEnabled()) {
                    log.trace("An existing file already exists: " + name + ". Ignore and do not override it.");
                }
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            }
        }

        InputStream is = null;
        try {
            is = exchange.getIn().getMandatoryBody(InputStream.class);
            if (endpoint.getFileExist() == GenericFileExist.Append) {
                return client.appendFile(name, is);
            } else {
                return client.storeFile(name, is);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        // check whether a file already exists
        String directory = FileUtil.onlyPath(name);
        if (directory == null) {
            return false;
        }

        String onlyName = FileUtil.stripPath(name);
        try {
            String[] names = client.listNames(directory);
            // can return either null or an empty list depending on FTP servers
            if (names == null) {
                return false;
            }
            for (String existing : names) {
                if (existing.equals(onlyName)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        try {
            return client.printWorkingDirectory();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        if (log.isTraceEnabled()) {
            log.trace("Changing current directory to: " + path);
        }
        try {
            client.changeWorkingDirectory(path);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public List<FTPFile> listFiles() throws GenericFileOperationFailedException {
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
        try {
            return client.sendNoOp();
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
            String directory = sb.toString();

            // do not try to build root / folder
            if (!directory.equals("/")) {
                if (log.isTraceEnabled()) {
                    log.trace("Trying to build remote directory by chunk: " + directory);
                }

                success = client.makeDirectory(directory);
            }
        }

        return success;
    }

}
