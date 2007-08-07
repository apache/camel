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

import java.net.URI;

import org.apache.camel.RuntimeCamelException;

public class RemoteFileConfiguration implements Cloneable {
    private String protocol;
    private String username;
    private String host;
    private int port;
    private String password;
    private String file;
    private boolean binary;
    private boolean directory = true;

    public RemoteFileConfiguration() {
    }

    public RemoteFileConfiguration(URI uri) {
        configure(uri);
    }

    public RemoteFileConfiguration copy() {
        try {
            return (RemoteFileConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String toString() {
        return protocol + ":\\" + username + "@" + host + ":" + port + "/" + directory;
    }

    public void configure(URI uri) {
        setProtocol(uri.getScheme());
        setDefaultPort();
        setUsername(uri.getUserInfo());
        setHost(uri.getHost());
        setPort(uri.getPort());
        setFile(uri.getPath());
    }

    protected void setDefaultPort() {
        if ("ftp".equalsIgnoreCase(protocol)) {
            setPort(21);
        } else if ("sftp".equalsIgnoreCase(protocol)) {
            setPort(22);
        }
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port != -1) { // use default
            this.port = port;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public String dump() {
        return "RemoteFileConfiguration{" + "protocol='" + protocol + '\'' + ", username='" + username + '\'' + ", host='" + host + '\'' + ", port=" + port + ", password='" + password + '\''
               + ", file='" + file + '\'' + ", binary=" + binary + ", directory=" + directory + '}';
    }
}
