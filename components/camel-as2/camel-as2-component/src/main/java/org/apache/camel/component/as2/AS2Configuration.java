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
package org.apache.camel.component.as2;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for AS2 component.
 */
@UriParams
public class AS2Configuration {

    @UriPath
    @Metadata(required = "true")
    private AS2ApiName apiName;

    @UriPath
    @Metadata(required = "true")
    private String methodName;

    @UriParam
    private String userAgent = "Camel AS2 Client Endpoint";
    
    @UriParam
    private String server = "Camel AS2 Server Endpoint";
    
    @UriParam
    private String targetHostname;
    
    @UriParam
    private int targetPortNumber;
    
    @UriParam
    private String clientFqdn = "camel.apache.org";
    
    @UriParam
    private int serverPortNumber;

    /**
     * What kind of operation to perform
     * 
     * @return the API Name
     */
    public AS2ApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     * 
     * @param apiName
     *            the API Name to set
     */
    public void setApiName(AS2ApiName apiName) {
        this.apiName = apiName;
    }

    /**
     * What sub operation to use for the selected operation
     * 
     * @return the methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     * 
     * @param methodName
     *            the methodName to set
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * The value included in the <code>User-Agent</code>
     * message header identifying the AS2 user agent.
     * 
     * @return AS2 user agent identification string.
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * The value included in the <code>User-Agent</code>
     * message header identifying the AS2 user agent.
     * 
     * @param userAgent - AS2 user agent identification string.
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * The value included in the <code>Server</code> 
     * message header identifying the AS2 Server.
     * 
     * @return AS2 server identification string.
     */
    public String getServer() {
        return server;
    }

    /**
     * The value included in the <code>Server</code> 
     * message header identifying the AS2 Server.
     * 
     * @param server - AS2 server identification string.
     */
    public void setServer(String server) {
        this.server = server;
    }

    /**
     * The host name (IP or DNS) of target host.
     * 
     * @return The target host name (IP or DNS name).
     */
    public String getTargetHostname() {
        return targetHostname;
    }

    /**
     * The host name (IP or DNS name) of target host.
     * 
     * @param targetHostname - the target host name (IP or DNS name).
     */
    public void setTargetHostname(String targetHostname) {
        this.targetHostname = targetHostname;
    }

    /**
     * The port number of target host.
     * 
     * @return The target port number. -1 indicates the scheme default port.
     */
    public int getTargetPortNumber() {
        return targetPortNumber;
    }

    /**
     * The port number of target host.
     * 
     * @param targetPortNumber - the target port number. -1 indicates the scheme default port.
     */
    public void setTargetPortNumber(String targetPortNumber) {
        try {
            this.targetPortNumber = Integer.parseUnsignedInt(targetPortNumber);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid target port number: %s", targetPortNumber));
        }
    }

    /**
     * The Client Fully Qualified Domain Name (FQDN). 
     * 
     * <p> Used in message ids sent by endpoint.
     * 
     * @return The FQDN of client.
     */
    public String getClientFqdn() {
        return clientFqdn;
    }

    /**
     * The Client Fully Qualified Domain Name (FQDN). 
     * 
     * <p> Used in message ids sent by endpoint.
     * 
     * @param clientFqdn - the FQDN of client.
     */
    public void setClientFqdn(String clientFqdn) {
        if (clientFqdn == null) {
            throw new RuntimeCamelException("Parameter 'clientFqdn' can not be null");
        }
        this.clientFqdn = clientFqdn;
    }

    /**
     * The port number of server.
     * 
     * @return The server port number.
     */
    public int getServerPortNumber() {
        return serverPortNumber;
    }

    /**
     * The port number of server.
     * 
     * @param serverPortNumber - the server port number.
     */
    public void setServerPortNumber(String serverPortNumber) {
        try {
            this.serverPortNumber = Integer.parseUnsignedInt(serverPortNumber);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid target port number: %s", targetPortNumber));
        }
    }
    
}
