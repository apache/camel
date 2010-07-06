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

import org.apache.camel.component.file.GenericFileConfiguration;

/**
 * Configuration of the FTP server
 */
public abstract class RemoteFileConfiguration extends GenericFileConfiguration {
    private String protocol;
    private String username;
    private String host;
    private int port;
    private String password;
    private boolean binary;
    private boolean passiveMode;
    private int connectTimeout = 10000;
    private int timeout = 30000;
    private int soTimeout;

    public RemoteFileConfiguration() {
    }

    public RemoteFileConfiguration(URI uri) {
        configure(uri);
    }
    
    @Override
    public boolean needToNormalize() {
        return false;
    }

    @Override
    public void configure(URI uri) {
        super.configure(uri);
        setProtocol(uri.getScheme());
        setDefaultPort();
        setUsername(uri.getUserInfo());
        setHost(uri.getHost());
        setPort(uri.getPort());
    }

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return protocol + "://" + (username != null ? username : "anonymous") + "@" + host + ":" + getPort();
    }

    protected abstract void setDefaultPort();

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
        // only set port if provided with a positive number
        if (port > 0) {
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

    public boolean isPassiveMode() {
        return passiveMode;
    }

    /**
     * Sets passive mode connections.
     * <br/>
     * Default is active mode connections.
     */
    public void setPassiveMode(boolean passiveMode) {
        this.passiveMode = passiveMode;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the connect timeout for waiting for a connection to be established
     * <p/>
     * Used by both FTPClient and JSCH
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the data timeout for waiting for reply
     * <p/>
     * Used only by FTPClient
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * Sets the so timeout
     * <p/>
     * Used only by FTPClient
     */
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }
}
