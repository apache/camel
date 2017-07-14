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
package org.apache.camel.component.docker;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DockerConfiguration implements Cloneable {

    @UriPath(enums = "events,stats,auth,info,ping,version,imagebuild,imagecreate,imageinspect,imagelist,imagepull,imagepush"
            + "imageremove,imagesearch,imagetag,containerattach,containercommit,containercopyfile,containercreate,containerdiff"
            + "inspectcontainer,containerkill,containerlist,containerlog,containerpause,containerrestart,containerremove,containerstart"
            + "containerstop,containertop,containerunpause,containerwait,execcreate,execstart") @Metadata(required = "true")
    private DockerOperation operation;
    @UriParam(defaultValue = "localhost") @Metadata(required = "true")
    private String host = "localhost";
    @UriParam(defaultValue = "2375") @Metadata(required = "true")
    private Integer port = 2375;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    private String email;
    @UriParam(label = "advanced", defaultValue = "https://index.docker.io/v1/")
    private String serverAddress = "https://index.docker.io/v1/";
    @UriParam
    private Integer requestTimeout;
    @UriParam(label = "security")
    private boolean secure;
    @UriParam(label = "security")
    private String certPath;
    @UriParam(label = "advanced", defaultValue = "100")
    private Integer maxTotalConnections = 100;
    @UriParam(label = "advanced", defaultValue = "100")
    private Integer maxPerRouteConnections = 100;
    @UriParam(label = "advanced")
    private boolean loggingFilter;
    @UriParam(label = "advanced")
    private boolean followRedirectFilter;
    @UriParam(label = "security", defaultValue = "false")
    private boolean tlsVerify;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean socket;
    @UriParam(label = "advanced", defaultValue = "com.github.dockerjava.netty.NettyDockerCmdExecFactory")
    private String cmdExecFactory = "com.github.dockerjava.netty.NettyDockerCmdExecFactory";
    
    private Map<String, Object> parameters = new HashMap<String, Object>();

    public String getHost() {
        return host;
    }

    /**
     * Docker host
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * Docker port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    /**
     * User name to authenticate with
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to authenticate with
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Email address associated with the user
     */
    public void setEmail(String email) {
        this.email = email;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    /**
     * Server address for docker registry.
     */
    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Request timeout for response (in seconds)
     */
    public void setRequestTimeout(Integer requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isSecure() {
        return secure;
    }

    /**
     * Use HTTPS communication
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getCertPath() {
        return certPath;
    }

    /**
     * Location containing the SSL certificate chain
     */
    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    /**
     * Maximum total connections
     */
    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Integer getMaxPerRouteConnections() {
        return maxPerRouteConnections;
    }

    /**
     * Maximum route connections
     */
    public void setMaxPerRouteConnections(Integer maxPerRouteConnections) {
        this.maxPerRouteConnections = maxPerRouteConnections;
    }

    public boolean isLoggingFilterEnabled() {
        return loggingFilter;
    }

    /**
     * Whether to use logging filter
     */
    public void setLoggingFilter(boolean loggingFilterEnabled) {
        this.loggingFilter = loggingFilterEnabled;
    }

    public boolean isFollowRedirectFilterEnabled() {
        return followRedirectFilter;
    }

    /**
     * Whether to follow redirect filter
     */
    public void setFollowRedirectFilter(boolean followRedirectFilterEnabled) {
        this.followRedirectFilter = followRedirectFilterEnabled;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Additional configuration parameters as key/value pairs
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public DockerOperation getOperation() {
        return operation;
    }

    /**
     * Which operation to use
     */
    public void setOperation(DockerOperation operation) {
        this.operation = operation;
    }

    public boolean isTlsVerify() {
        return tlsVerify;
    }
    
    /**
     * Check TLS 
     */
    public void setTlsVerify(boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
    }
    
    public boolean isSocket() {
        return socket;
    }

    /**
     * Socket connection mode
     */
    public void setSocket(boolean socket) {
        this.socket = socket;
    }

    public String getCmdExecFactory() {
        return cmdExecFactory;
    }

    /**
     * The fully qualified class name of the DockerCmdExecFactory implementation to use
     */
    public void setCmdExecFactory(String cmdExecFactory) {
        this.cmdExecFactory = cmdExecFactory;
    }

    public DockerConfiguration copy() {
        try {
            return (DockerConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
