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

    @UriPath(description = "The operation to perform. Only startCase is supported: it starts a case (an instance) of the"
                           + " process that the processName option names, with the contract inputs from the message body.")
    @Metadata(required = true)
    private BonitaOperation operation;
    @UriParam(description = "The host name of the Bonita server. The producer calls the REST API under the context path"
                            + " of the basePath option on this host.",
              defaultValue = "localhost")
    private String hostname = "localhost";
    @UriParam(description = "The HTTP port of the Bonita server.", defaultValue = "8080")
    private String port = "8080";
    @UriParam(description = "The context path of the Bonita web application on the server, such as /bonita or the path"
                            + " that a reverse proxy maps to it. The producer calls the REST API, the login and the file"
                            + " uploads under it. A missing leading slash is added and a trailing slash removed, and /"
                            + " is the root of the server.",
              defaultValue = "/bonita")
    private String basePath = "/bonita";
    @UriParam(description = "The exact name of the process to start a case of. When the process is deployed in several"
                            + " versions, processVersion selects one. The producer fails when it starts if the option is"
                            + " not set, and fails the exchange if no process has this name.")
    @Metadata(required = true)
    private String processName;
    @UriParam(description = "The version of the process to start a case of. If not set and the process is deployed in"
                            + " several versions, the producer uses the first one that Bonita returns and logs a WARN"
                            + " that names it.")
    private String processVersion;
    @UriParam(description = "The username of the Bonita account. The producer logs in with it at the loginservice of"
                            + " Bonita before each request, and fails when it starts if the option is not set.",
              label = "security", security = "secret")
    @Metadata(required = true)
    private String username;
    @UriParam(description = "The password of the Bonita account. The producer logs in with it at the loginservice of"
                            + " Bonita before each request, and fails when it starts if the option is not set.",
              label = "security", security = "secret")
    @Metadata(required = true)
    private String password;

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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public BonitaOperation getOperation() {
        return operation;
    }

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
