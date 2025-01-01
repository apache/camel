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
package org.apache.camel.component.smb2;

import java.io.IOException;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.utils.SmbFiles;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Smb2Operations implements Smb2FileOperations {

    private static final Logger LOG = LoggerFactory.getLogger(Smb2Operations.class);

    private final Smb2Configuration configuration;
    private Smb2Endpoint endpoint;
    private boolean loggedIn;
    private Session session;
    private DiskShare share;
    private SMBClient smbClient;

    public Smb2Operations(Smb2Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean connect(Smb2Configuration configuration, Exchange exchange) throws GenericFileOperationFailedException {
        if (smbClient == null) {
            smbClient = new SMBClient();
        }
        try {
            connectIfNecessary();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(
                    "Cannot connect to: " + configuration.getHostname() + ":" + configuration.getPort() + " due to: "
                                                          + e.getMessage(),
                    e);
        }
        return false;
    }

    protected void connectIfNecessary() throws IOException {
        Connection connection = smbClient.connect(configuration.getHostname(), configuration.getPort());

        if (!loggedIn || !isConnected()) {
            LOG.debug("Not already connected/logged in. Connecting to: {}:{}", configuration.getHostname(),
                    configuration.getPort());

            AuthenticationContext ac = new AuthenticationContext(
                    configuration.getUsername(),
                    configuration.getPassword().toCharArray(),
                    configuration.getDomain());
            session = connection.authenticate(ac);

            share = (DiskShare) session.connectShare(configuration.getShareName());

            LOG.debug("Connected and logged in to: {}:{}", configuration.getHostname(), configuration.getPort());
            loggedIn = true;
        }
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        if (share != null) {
            return share.isConnected();
        }
        return false;
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        loggedIn = false;
        if (share != null) {
            try {
                share.close();
            } catch (IOException e) {
                // ignore
            }
            share = null;
        }
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                // ignore
            }
            session = null;
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        disconnect();
        if (smbClient != null) {
            smbClient.close();
        }
        smbClient = null;
    }

    @Override
    public GenericFile<File> newGenericFile() {
        return new Smb2File();
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<File> endpoint) {
        this.endpoint = (Smb2Endpoint) endpoint;
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        share.rm(name);
        return true;
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        return share.fileExists(name);
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        return false;
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        SmbFiles files = new SmbFiles();
        files.mkdirs(share, normalize(directory));
        return true;
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        return true;
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        return false;
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        return null; // noop
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public File[] listFiles() throws GenericFileOperationFailedException {
        return null; // noop
    }

    @Override
    public File[] listFiles(String path) throws GenericFileOperationFailedException {
        return null; //noop
    }

    /*
     * Normalize changes separators for smb
     */
    private String normalize(String file) {
        return file.replace('\\', endpoint.getFileSeparator());
    }

}
