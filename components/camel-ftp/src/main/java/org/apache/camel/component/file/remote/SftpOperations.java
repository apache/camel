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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * SFTP remote file operations
 */
public class SftpOperations implements RemoteFileOperations<ChannelSftp.LsEntry> {
    private static final transient Log LOG = LogFactory.getLog(SftpOperations.class);
    private ChannelSftp channel;
    private Session session;

    public void setEndpoint(GenericFileEndpoint endpoint) {
        // noop
    }

    public boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException {
        try {
            if (isConnected()) {
                // already connected
                return true;
            }
            if (channel == null || !channel.isConnected()) {
                if (session == null || !session.isConnected()) {
                    LOG.trace("Session isn't connected, trying to recreate and connect.");
                    session = createSession(configuration);
                    session.connect();
                }
                LOG.trace("Channel isn't connected, trying to recreate and connect.");
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect();
                LOG.info("Connected to " + configuration.remoteServerInformation());
            }

            return true;

        } catch (JSchException e) {
            throw new RemoteFileOperationFailedException("Cannot connect to " + configuration.remoteServerInformation(), e);
        }
    }

    protected Session createSession(final RemoteFileConfiguration configuration) throws JSchException {
        final JSch jsch = new JSch();

        SftpConfiguration sftpConfig = (SftpConfiguration) configuration;

        if (isNotEmpty(sftpConfig.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: " + sftpConfig.getPrivateKeyFile());
            if (isNotEmpty(sftpConfig.getPrivateKeyFilePassphrase())) {
                jsch.addIdentity(sftpConfig.getPrivateKeyFile(), sftpConfig.getPrivateKeyFilePassphrase());
            } else {
                jsch.addIdentity(sftpConfig.getPrivateKeyFile());
            }
        }

        if (isNotEmpty(sftpConfig.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: " + sftpConfig.getKnownHostsFile());
            jsch.setKnownHosts(sftpConfig.getKnownHostsFile());
        }

        final Session session = jsch.getSession(configuration.getUsername(), configuration.getHost(), configuration.getPort());
        session.setUserInfo(new UserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return configuration.getPassword();
            }

            public boolean promptPassword(String s) {
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                LOG.error(s);
                // Return 'false' indicating modification of the hosts file is disabled.
                return false;
            }

            public void showMessage(String s) {
            }
        });
        return session;
    }

    public boolean isConnected() throws GenericFileOperationFailedException {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }

    public void disconnect() throws GenericFileOperationFailedException {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleteing file: " + name);
        }
        try {
            channel.rm(name);
            return true;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot delete file: " + name, e);
        }
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + from + " to: " + to);
        }
        try {
            channel.rename(from, to);
            return true;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot rename file from: " + from + " to: " + to, e);
        }
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        // ignore absolute as all dirs are relative with FTP

        boolean success = false;

        String originalDirectory = getCurrentDirectory();
        try {
            // maybe the full directory already exsits
            try {
                channel.cd(directory);
                success = true;
            } catch (SftpException e) {
                // ignore, we could not change directory so try to create it instead
            }

            if (!success) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Trying to build remote directory: " + directory);
                }

                try {
                    channel.mkdir(directory);
                    success = true;
                } catch (SftpException e) {
                    // we are here if the server side doesn't create intermediate folders
                    // so create the folder one by one
                    success = buildDirectoryChunks(directory);
                }
            }
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException("Cannot build directory " + directory, e);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot build directory " + directory, e);
        } finally {
            // change back to original directory
            if (originalDirectory != null) {
                changeCurrentDirectory(originalDirectory);
            }
        }

        return success;
    }

    private boolean buildDirectoryChunks(String dirName) throws IOException, SftpException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("\\/");

        boolean success = false;
        for (String dir : dirs) {
            sb.append(dir).append('/');
            String directory = sb.toString();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Trying to build remote directory by chunk: " + directory);
            }

            // do not try to build root / folder
            if (!directory.equals("/")) {
                try {
                    channel.mkdir(directory);
                    success = true;
                } catch (SftpException e) {
                    // ignore keep trying to create the rest of the path
                }
            }
        }

        return success;
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        try {
            return channel.pwd();
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot get current directory", e);
        }
    }

    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        try {
            channel.cd(path);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot change current directory to: " + path, e);
        }
    }

    public List<ChannelSftp.LsEntry> listFiles() throws GenericFileOperationFailedException {
        return listFiles(".");
    }

    public List<ChannelSftp.LsEntry> listFiles(String path) throws GenericFileOperationFailedException {
        if (ObjectHelper.isEmpty(path)) {
            // list current dirctory if file path is not given
            path = ".";
        }

        try {
            final List<ChannelSftp.LsEntry> list = new ArrayList<ChannelSftp.LsEntry>();
            Vector files = channel.ls(path);
            for (Object file : files) {
                list.add((ChannelSftp.LsEntry)file);
            }
            return list;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot list directory: " + path, e);
        }
    }

    public boolean retrieveFile(String name, GenericFileExchange<ChannelSftp.LsEntry> exchange) throws GenericFileOperationFailedException {
        try {
            GenericFile<ChannelSftp.LsEntry> target = exchange.getGenericFile();
            OutputStream os = new ByteArrayOutputStream();
            target.setBody(os);
            channel.get(name, os);
            return true;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Could not retrieve the file [" + name + "]", e);
        }
    }

    public boolean storeFile(String name, GenericFileExchange<ChannelSftp.LsEntry> exchange) throws GenericFileOperationFailedException {
        try {
            InputStream in = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
            channel.put(in, name);
            return true;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Could not write the file [" + name + "]", e);
        } catch (InvalidPayloadException e) {
            throw new RemoteFileOperationFailedException("Could not write the file [" + name + "]", e);
        }
    }

}