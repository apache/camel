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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility methods for SFTP.
 */
public final class SftpUtils {
    private static final transient Log LOG = LogFactory.getLog(SftpUtils.class);

    private SftpUtils() {
    }

    public static boolean buildDirectory(ChannelSftp sftpClient, String dirName)
        throws IOException, SftpException {
        String originalDirectory = sftpClient.pwd();

        boolean success = false;
        try {
            // maybe the full directory already exsits
            try {
                sftpClient.cd(dirName);
                success = true;
            } catch (SftpException e) {
                // ignore, we could not change directory so try to create it instead
            }

            if (!success) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Trying to build remote directory: " + dirName);
                }

                try {
                    sftpClient.mkdir(dirName);
                    success = true;
                } catch (SftpException e) {
                    // we are here if the server side doesn't create intermediate folders
                    // so create the folder one by one
                    success = buildDirectoryChunks(sftpClient, dirName);
                }
            }
        } finally {
            // change back to original directory
            sftpClient.cd(originalDirectory);
        }

        return success;
    }

    public static boolean buildDirectoryChunks(ChannelSftp sftpClient, String dirName)
        throws IOException, SftpException {
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
                sftpClient.mkdir(directory);
                success = true;
            } catch (SftpException e) {
                // ignore keep trying to create the rest of the path
            }
        }

        return success;
    }
}