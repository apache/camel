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
package org.apache.camel.component.bonita.api.util;

public class BonitaAPIConfig {

    public static final String DEFAULT_BASE_PATH = "/bonita";

    private String hostname;
    private String port;
    private String username;
    private String password;
    private String basePath = DEFAULT_BASE_PATH;

    public BonitaAPIConfig(String hostname, String port, String username, String password) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * @param basePath the context path of the Bonita web application, such as /bonita; null means /bonita
     */
    public BonitaAPIConfig(String hostname, String port, String username, String password, String basePath) {
        this(hostname, port, username, password);
        setBasePath(basePath);
    }

    public String getBaseBonitaURI() {
        return "http://" + hostname + ":" + port + basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * The context path of the Bonita web application. A missing leading slash is added and a trailing slash removed, so
     * that the paths of the REST API can be appended; an empty path or / is the root of the server, and null is
     * /bonita.
     */
    public void setBasePath(String basePath) {
        if (basePath == null) {
            this.basePath = DEFAULT_BASE_PATH;
            return;
        }
        String path = basePath.trim();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        this.basePath = path;
    }

    public String getProcessFileUploadBaseURI(String processName, String processVersion) {
        return getBaseBonitaURI() + "/portal/resource/process/" + processName + "/" + processVersion
               + "/API/formFileUpload";
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
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

}
