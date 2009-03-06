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
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP remote file operations
 */
public class FtpOperations implements RemoteFileOperations<FTPFile> {
    private static final transient Log LOG = LogFactory.getLog(FtpOperations.class);
    private final FTPClient client;
    private GenericFileEndpoint endpoint;

    public FtpOperations() {
        this.client = new FTPClient();
    }

    public FtpOperations(FTPClient client) {
        this.client = client;
    }

    public void setEndpoint(GenericFileEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public boolean connect(RemoteFileConfiguration config) throws GenericFileOperationFailedException {
        String host = config.getHost();
        int port = config.getPort();
        String username = config.getUsername();

        FtpConfiguration ftpConfig = (FtpConfiguration) config;

        if (ftpConfig.getFtpClientConfig() != null) {
            LOG.trace("Configuring FTPFile with config: " + ftpConfig.getFtpClientConfig());
            client.configure(ftpConfig.getFtpClientConfig());
        }

        LOG.trace("Connecting to " + config.remoteServerInformation());
        try {
            client.connect(host, port);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        // must enter passive mode directly after connect
        if (config.isPassiveMode()) {
            LOG.trace("Using passive mode connections");
            client.enterLocalPassiveMode();
        }

        try {
            boolean login;
            if (username != null) {
                LOG.trace("Attempting to login user: " + username);
                login = client.login(username, config.getPassword());
            } else {
                LOG.trace("Attempting to login anonymousl");
                login = client.login("anonymous", null);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("User " + (username != null ? username : "anonymous") + " logged in: " + login);
            }
            if (!login) {
                throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString());
            }
            client.setFileType(config.isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        return true;
    }

    public boolean isConnected() throws GenericFileOperationFailedException {
        return client.isConnected();
    }

    public void disconnect() throws GenericFileOperationFailedException {
        try {
            client.disconnect();
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean deleteFile(FTPClient client, String name) throws GenericFileOperationFailedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleteing file: " + name);
        }
        try {
            return client.deleteFile(name);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + from + " to: " + to);
        }
        try {
            return client.rename(from, to);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Building directory: " + directory);
        }
        try {
            String originalDirectory = client.printWorkingDirectory();

            boolean success;
            try {
                // maybe the full directory already exsits
                success = client.changeWorkingDirectory(directory);
                if (!success) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Trying to build remote directory: " + directory);
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
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean retrieveFile(String name, GenericFileExchange<FTPFile> exchange) throws GenericFileOperationFailedException {
        if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
            // local work directory is configured so we should store file content as files in this local directory
            return retrieveFileToFileInLocalWorkDirectory(name, exchange);
        } else {
            // store file content directory as stream on the body
            return retrieveFileToStreamInBody(name, exchange);
        }
    }

    private boolean retrieveFileToStreamInBody(String name, GenericFileExchange<FTPFile> exchange) throws GenericFileOperationFailedException {
        OutputStream os = null;
        try {
            os = new ByteArrayOutputStream();
            GenericFile<FTPFile> target = exchange.getGenericFile();
            target.setBody(os);
            return client.retrieveFile(name, os);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            ObjectHelper.close(os, "retrieve: " + name, LOG);
        }
    }

    private boolean retrieveFileToFileInLocalWorkDirectory(String name, GenericFileExchange<FTPFile> exchange) throws GenericFileOperationFailedException {
        File temp;        
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        try {
            // use relative filename in local work directory
            String relativeName = exchange.getGenericFile().getRelativeFileName();
            
            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            local.mkdirs();

            // delete any existing files
            if (temp.exists()) {
                if (!temp.delete()) {
                    throw new RemoteFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
            }
            if (local.exists()) {
                if (!local.delete()) {
                    throw new RemoteFileOperationFailedException("Cannot delete existing local work file: " + local);
                }                
            }
           

            // create new temp local work file
            if (!temp.createNewFile()) {
                throw new RemoteFileOperationFailedException("Cannot create new local work file: " + temp);
            }

            // store content as a file in the local work directory in the temp handle
            os = new FileOutputStream(temp);

            // set header with the path to the local work file            
            exchange.getIn().setHeader(Exchange.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {            
            throw new RemoteFileOperationFailedException("Cannot create new local work file: " + local);
        }

        boolean result;
        try {
            GenericFile<FTPFile> target = exchange.getGenericFile();
            // store the java.io.File handle as the body
            target.setBody(local);            
            result = client.retrieveFile(name, os);
            
        } catch (IOException e) {            
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }  finally {
            // need to close the stream before rename it
            ObjectHelper.close(os, "retrieve: " + name, LOG);
        }   
            
        // rename temp to local after we have retrieved the data
        if (!temp.renameTo(local)) {                
            throw new RemoteFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
        }
        

        return result;
    }

    public boolean storeFile(String name, GenericFileExchange<FTPFile> exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getBody(InputStream.class);
        try {
            return client.storeFile(name, is);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            ObjectHelper.close(is, "store: " + name, LOG);
        }
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        try {
            return client.printWorkingDirectory();
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public void changeCurrentDirectory(String newDirectory) throws GenericFileOperationFailedException {
        try {
            client.changeWorkingDirectory(newDirectory);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public List<FTPFile> listFiles() throws GenericFileOperationFailedException {
        return listFiles(".");
    }

    public List<FTPFile> listFiles(String path) throws GenericFileOperationFailedException {
        // use current directory if path not given
        if (ObjectHelper.isEmpty(path)) {
            path = ".";
        }

        try {
            final List<FTPFile> list = new ArrayList<FTPFile>();
            FTPFile[] files = client.listFiles(path);
            list.addAll(Arrays.asList(files));
            return list;
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        try {
            return this.client.deleteFile(name);
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
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
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Trying to build remote directory by chunk: " + directory);
                }

                success = client.makeDirectory(directory);
            }
        }

        return success;
    }

}
