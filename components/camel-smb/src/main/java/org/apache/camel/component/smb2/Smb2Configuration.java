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

import java.net.URI;

import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class Smb2Configuration extends GenericFileConfiguration {

    // component name is implied as the protocol
    private String protocol;

    @UriPath
    @Metadata(required = true)
    private String hostname;
    @UriPath(defaultValue = "445")
    private int port;
    @UriPath
    @Metadata(required = true)
    private String shareName;
    @UriParam(label = "security", description = "The username required to access the share", secret = true)
    private String username;
    @UriParam(label = "security", description = "The password to access the share", secret = true)
    private String password;
    @UriParam(label = "security", description = "The user domain")
    private String domain;

    public Smb2Configuration() {
        setProtocol("smb");
    }

    public Smb2Configuration(URI uri) {
        super.configure(uri);
        setProtocol(uri.getScheme());
        setHostname(uri.getHost());
        if (uri.getPort() > 0) {
            setPort(uri.getPort());
        }
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The smb protocol to use
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The share hostname or IP address
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * The share port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getShareName() {
        return shareName;
    }

    /**
     * The name of the share to connect to.
     */
    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
