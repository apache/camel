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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;

/**
 * Utility methods for FTP.
 */
public final class FtpUtils {
    private static final transient Log LOG = LogFactory.getLog(FtpUtils.class);

    private FtpUtils() {
    }

    public static boolean connect(FTPClient client, RemoteFileConfiguration config) throws IOException {
        String host = config.getHost();
        int port = config.getPort();
        String username = config.getUsername();

        if (config.getFtpClientConfig() != null) {
            LOG.trace("Configuring FTPClient with config: " + config.getFtpClientConfig());
            client.configure(config.getFtpClientConfig());
        }

        LOG.trace("Connecting to " + config);
        client.connect(host, port);

        // must enter passive mode directly after connect
        if (config.isPassiveMode()) {
            LOG.trace("Using passive mode connections");
            client.enterLocalPassiveMode();
        }

        boolean login;
        if (username != null) {
            LOG.trace("Attempting to login user: " + username);
            login = client.login(username, config.getPassword());
        } else {
            LOG.trace("Attempting to login anonymous");
            login = client.login("anonymous", null);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("User " + (username != null ? username : "anonymous") + " logged in: " + login);
        }
        if (!login) {
            return false;
        }

        client.setFileType(config.isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
        return true;
    }

    public static void disconnect(FTPClient client) throws IOException {
        if (client.isConnected()) {
            client.disconnect();
        }
    }

    public static FTPClient createNewFtpClient() {
        return new FTPClient();
    }

    public static boolean buildDirectory(FTPClient ftpClient, String dirName) throws IOException {
        String originalDirectory = ftpClient.printWorkingDirectory();

        boolean success = false;
        try {
            // maybe the full directory already exsits
            success = ftpClient.changeWorkingDirectory(dirName);
            if (!success) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Trying to build remote directory: " + dirName);
                }
                success = ftpClient.makeDirectory(dirName);
                if (!success) {
                    // we are here if the server side doesn't create intermediate folders
                    // so create the folder one by one
                    buildDirectoryChunks(ftpClient, dirName);
                }
            }
        } finally {
            // change back to original directory
            ftpClient.changeWorkingDirectory(originalDirectory);
        }

        return success;
    }

    public static boolean buildDirectoryChunks(FTPClient ftpClient, String dirName) throws IOException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("\\/");

        boolean success = false;
        for (String dir : dirs) {
            sb.append(dir).append('/');
            String directory = sb.toString();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Trying to build remote directory: " + directory);
            }

            success = ftpClient.makeDirectory(directory);
        }

        return success;
    }

}
