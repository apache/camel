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

import java.util.Locale;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import javax.net.ssl.SSLContext;

public class BonitaAPIConfig {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";

    private String protocol = HTTP;
    private SSLContext sslContext;

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
        return protocol + "://" + hostname + ":" + port + basePath;
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

    /**
     * Creates a new JAX-RS client for the Bonita server of this configuration, with the {@link #getSslContext() SSL
     * context} of this configuration when it has one. The caller closes the client.
     */
    public Client newClient() {
        ClientBuilder builder = ClientBuilder.newBuilder();
        if (sslContext != null) {
            builder = builder.sslContext(sslContext);
        }
        return builder.build();
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol of the URIs of the Bonita server: http or https (in any case); null is http.
     *
     * @throws IllegalArgumentException for any other protocol
     */
    public void setProtocol(String protocol) {
        if (protocol == null) {
            this.protocol = HTTP;
            return;
        }
        String value = protocol.trim().toLowerCase(Locale.ROOT);
        if (!HTTP.equals(value) && !HTTPS.equals(value)) {
            throw new IllegalArgumentException(
                    "The protocol of the Bonita server must be http or https, but is: " + protocol);
        }
        this.protocol = value;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * The SSL context of the clients that {@link #newClient()} creates; null uses the default SSL context of the JVM.
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
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
