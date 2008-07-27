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

import org.apache.commons.net.ftp.FTPClient;

/**
 * Utility methods for FTP.
 */
public class FtpUtils {

    private FtpUtils() {
    }

    public static void connect(FTPClient client, RemoteFileConfiguration config) throws IOException {
        String host = config.getHost();
        int port = config.getPort();
        String username = config.getUsername();

        client.connect(host, port);
        if (username != null) {
            client.login(username, config.getPassword());
        } else {
            client.login("anonymous", null);
        }
        client.setFileType(config.isBinary() ? FTPClient.BINARY_FILE_TYPE : FTPClient.ASCII_FILE_TYPE);
    }

    public static void disconnect(FTPClient client) throws IOException {
        if (client.isConnected()) {
            client.disconnect();
        }
    }

    public static FTPClient createNewFtpClient() {
        return new FTPClient();
    }

}
