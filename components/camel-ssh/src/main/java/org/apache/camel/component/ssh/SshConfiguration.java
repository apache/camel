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
package org.apache.camel.component.ssh;

import org.apache.camel.util.ObjectHelper;

import java.net.URI;

public class SshConfiguration {
    public static final int DEFAULT_SSH_PORT = 22;

    private String username;
    private String host;
    private int port;
    private String password;

    public SshConfiguration() {
    }

    public SshConfiguration(URI uri) {
        configure(uri);
    }

    protected void setDefaultPort() {
        setPort(DEFAULT_SSH_PORT);
    }

    public void configure(URI uri) {
        setDefaultPort();

        // UserInfo can contain both username and password as: user:pwd@sshserver
        // see: http://en.wikipedia.org/wiki/URI_scheme
        String username = uri.getUserInfo();
        String pw = null;
        if (username != null && username.contains(":")) {
            pw = ObjectHelper.after(username, ":");
            username = ObjectHelper.before(username, ":");
        }
        if (username != null) {
            setUsername(username);
        }
        if (pw != null) {
            setPassword(pw);
        }

        setHost(uri.getHost());
        setPort(uri.getPort());
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
