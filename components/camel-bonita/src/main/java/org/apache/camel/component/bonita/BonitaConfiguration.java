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
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class BonitaConfiguration implements Cloneable {

    @UriPath(description = "The operation to perform. Only startCase is supported: it starts a case (an instance) of the"
                           + " process that the processName option names, with the contract inputs from the message body.")
    @Metadata(required = true)
    private BonitaOperation operation;
    @UriParam(description = "The protocol of the REST API of the Bonita server: http, or https to send the login, the"
                            + " session cookies and the requests over TLS. With https, set the port to the HTTPS port of"
                            + " the server, and the sslContextParameters option when the JVM does not trust the"
                            + " certificate of the server.",
              enums = "http,https", defaultValue = "http")
    private String protocol = "http";
    @UriParam(description = "The host name of the Bonita server. The producer calls the REST API under the"
                            + " /bonita context path of this host.",
              defaultValue = "localhost")
    private String hostname = "localhost";
    @UriParam(description = "The HTTP port of the Bonita server.", defaultValue = "8080")
    private String port = "8080";
    @UriParam(description = "The name of the process to start a case of. The producer searches the processes of the Bonita"
                            + " REST API for this name and uses the first one found, and fails if none is found or the"
                            + " option is not set.")
    private String processName;
    @UriParam(description = "The username of the Bonita account. The producer logs in with it at the loginservice of"
                            + " Bonita before each request, and fails if it is not set.",
              label = "security", security = "secret")
    private String username;
    @UriParam(description = "The password of the Bonita account. The producer logs in with it at the loginservice of"
                            + " Bonita before each request, and fails if it is not set.",
              label = "security", security = "secret")
    private String password;
    @UriParam(description = "The SSL configuration of the HTTPS connections to the Bonita server, such as the trust store"
                            + " with the certificate of the server. It is used only when the protocol is https; without it"
                            + " the default SSL context of the JVM is used.",
              label = "security")
    private SSLContextParameters sslContextParameters;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
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

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

}
