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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * SFTP remote file operations
 */
public class SftpRemoteFileOperations implements RemoteFileOperations<ChannelSftp> {
    private static final Log LOG = LogFactory.getLog(SftpRemoteFileOperations.class);
    private ChannelSftp channel;
    private Session session;

    public boolean connect(RemoteFileConfiguration configuration) throws RemoteFileOperationFailedException {
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

        if (isNotEmpty(configuration.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: " + configuration.getPrivateKeyFile());
            if (isNotEmpty(configuration.getPrivateKeyFilePassphrase())) {
                jsch.addIdentity(configuration.getPrivateKeyFile(), configuration.getPrivateKeyFilePassphrase());
            } else {
                jsch.addIdentity(configuration.getPrivateKeyFile());
            }
        }

        if (isNotEmpty(configuration.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: " + configuration.getKnownHostsFile());
            jsch.setKnownHosts(configuration.getKnownHostsFile());
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

    public boolean isConnected() throws RemoteFileOperationFailedException {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }

    public void disconnect() throws RemoteFileOperationFailedException {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }

    public boolean deleteFile(String name) throws RemoteFileOperationFailedException {
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

    public boolean renameFile(String from, String to) throws RemoteFileOperationFailedException {
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

    public boolean buildDirectory(String dirName) throws RemoteFileOperationFailedException {
        boolean success = false;

        String originalDirectory = getCurrentDirectory();
        try {
            // maybe the full directory already exsits
            try {
                channel.cd(dirName);
                success = true;
            } catch (SftpException e) {
                // ignore, we could not change directory so try to create it instead
            }

            if (!success) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Trying to build remote directory: " + dirName);
                }

                try {
                    channel.mkdir(dirName);
                    success = true;
                } catch (SftpException e) {
                    // we are here if the server side doesn't create intermediate folders
                    // so create the folder one by one
                    success = buildDirectoryChunks(dirName);
                }
            }
        } catch (IOException e) {
            throw new RemoteFileOperationFailedException("Cannot build directory " + dirName, e);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot build directory " + dirName, e);
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
                LOG.trace("Trying to build remote directory: " + directory);
            }

            try {
                channel.mkdir(directory);
                success = true;
            } catch (SftpException e) {
                // ignore keep trying to create the rest of the path
            }
        }

        return success;
    }

    public boolean retrieveFile(String name, OutputStream out) throws RemoteFileOperationFailedException {
        try {
            channel.get(name, out);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot get file: " + name, e);
        }
        return true;
    }

    public boolean storeFile(String name, InputStream body) throws RemoteFileOperationFailedException {
        try {
            channel.put(body, name);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot put file: " + name, e);
        }
        return true;
    }

    public String getCurrentDirectory() throws RemoteFileOperationFailedException {
        try {
            return channel.pwd();
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot get current directory", e);
        }
    }

    public void changeCurrentDirectory(String path) throws RemoteFileOperationFailedException {
        try {
            channel.cd(path);
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot change current directory to: " + path, e);
        }
    }

    public List listFiles() throws RemoteFileOperationFailedException {
        return listFiles(".");
    }

    public List listFiles(String path) throws RemoteFileOperationFailedException {
        try {
            final List list = new ArrayList();
            Vector files = channel.ls(path);
            for (Object file : files) {
                list.add(file);
            }
            return list;
        } catch (SftpException e) {
            throw new RemoteFileOperationFailedException("Cannot list directory: " + path, e);
        }
    }

}