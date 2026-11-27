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
package org.apache.camel.component.bonita;

import java.util.Map;

import org.apache.camel.component.bonita.util.BonitaOperation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class BonitaConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private BonitaOperation operation;
    @UriParam(defaultValue = "localhost")
    private String hostname = "localhost";
    @UriParam(defaultValue = "8080")
    private String port = "8080";
    @UriParam(defaultValue = "/bonita")
    private String basePath = "/bonita";
    @UriParam
    @Metadata(required = true)
    private String processName;
    @UriParam
    private String processVersion;
    @UriParam(label = "security", security = "secret")
    @Metadata(required = true)
    private String username;
    @UriParam(label = "security", security = "secret")
    @Metadata(required = true)
    private String password;

    public String getHostname() {
        return hostname;
    }

    /**
     * Hostname where Bonita engine runs
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPort() {
        return port;
    }

    /**
     * Port of the server hosting Bonita engine
     */
    public void setPort(String port) {
        this.port = port;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * The context path of the Bonita web application on the server, such as /bonita (the default) or the path that a
     * reverse proxy maps to it. The REST API, the login and the file uploads are called under it.
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    /**
     * The version of the process to start a case of. If not set and the process is deployed in several versions, the
     * first one that Bonita returns is used.
     */
    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public String getProcessName() {
        return processName;
    }

    /**
     * Name of the process involved in the operation
     */
    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public BonitaOperation getOperation() {
        return operation;
    }

    /**
     * Operation to use
     */
    public void setOperation(BonitaOperation operation) {
        this.operation = operation;
    }

    /**
     * Sets hostname, port and processName from a map. Camel does not call this method: the options are set through the
     * generated configurer.
     *
     * @deprecated not used by Camel, and it handles only three of the options. Use the setters.
     */
    @Deprecated(since = "4.24.0")
    public void setParameters(Map<String, Object> parameters) {
        if (parameters.get("hostname") != null) {
            this.hostname = (String) parameters.get("hostname");
        }
        if (parameters.get("port") != null) {
            this.port = (String) parameters.get("port");
        }
        if (parameters.get("processName") != null) {
            this.processName = (String) parameters.get("processName");
        }

    }

    public String getUsername() {
        return username;
    }

    /**
     * Username to authenticate to Bonita engine.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to authenticate to Bonita engine.
     */
    public void setPassword(String password) {
        this.password = password;
    }

}
