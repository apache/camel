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

import com.github.dockerjava.api.DockerClient;
import org.apache.camel.Endpoint;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link DockerEndpoint}.
 */
public class DockerComponent extends DefaultComponent {

    private DockerConfiguration configuration = new DockerConfiguration();
    private Map<DockerClientProfile, DockerClient> clients = new HashMap<DockerClientProfile, DockerClient>();

    public DockerComponent() {
    }

    public DockerComponent(DockerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // Each endpoint can have its own configuration so make
        // a copy of the configuration
        DockerConfiguration configuration = getConfiguration().copy();

        String normalizedRemaining = remaining.replaceAll("/", "");

        DockerOperation operation = DockerOperation.getDockerOperation(normalizedRemaining);

        if (operation == null) {
            throw new DockerException(remaining + " is not a valid operation");
        }

        configuration.setOperation(operation);

        // Validate URI Parameters
        DockerHelper.validateParameters(operation, parameters);

        Endpoint endpoint = new DockerEndpoint(uri, this, configuration);
        setProperties(configuration, parameters);
        configuration.setParameters(parameters);

        return endpoint;
    }

    public void setConfiguration(DockerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * To use the shared docker configuration. Properties of the shared configuration can also be set individually.
     */
    protected DockerConfiguration getConfiguration() {
        return configuration;
    }

    public DockerClient getClient(DockerClientProfile clientProfile) throws DockerException {
        return clients.get(clientProfile);
    }

    /**
     * To use the given docker client
     */
    public void setClient(DockerClientProfile clientProfile, DockerClient client) {
        clients.put(clientProfile, client);
    }

    public String getHost() {
        return configuration.getHost();
    }

    /**
     * Docker host
     * @param host
     */
    public void setHost(String host) {
        configuration.setHost(host);
    }

    public Integer getPort() {
        return configuration.getPort();
    }

    /**
     * Docker port
     * @param port
     */
    public void setPort(Integer port) {
        configuration.setPort(port);
    }

    public String getUsername() {
        return configuration.getUsername();
    }

    /**
     * User name to authenticate with
     * @param username
     */
    public void setUsername(String username) {
        configuration.setUsername(username);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    /**
     * Password to authenticate with
     * @param password
     */
    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public String getEmail() {
        return configuration.getEmail();
    }

    /**
     * Email address associated with the user
     * @param email
     */
    public void setEmail(String email) {
        configuration.setEmail(email);
    }

    public String getServerAddress() {
        return configuration.getServerAddress();
    }

    /**
     * Server address for docker registry.
     * @param serverAddress
     */
    public void setServerAddress(String serverAddress) {
        configuration.setServerAddress(serverAddress);
    }

    public Integer getRequestTimeout() {
        return configuration.getRequestTimeout();
    }

    /**
     * Request timeout for response (in seconds)
     * @param requestTimeout
     */
    public void setRequestTimeout(Integer requestTimeout) {
        configuration.setRequestTimeout(requestTimeout);
    }

    public boolean isSecure() {
        return configuration.isSecure();
    }

    /**
     * Use HTTPS communication
     * @param secure
     */
    public void setSecure(boolean secure) {
        configuration.setSecure(secure);
    }

    public String getCertPath() {
        return configuration.getCertPath();
    }

    /**
     * Location containing the SSL certificate chain
     * @param certPath
     */
    public void setCertPath(String certPath) {
        configuration.setCertPath(certPath);
    }

    public Integer getMaxTotalConnections() {
        return configuration.getMaxTotalConnections();
    }

    /**
     * Maximum total connections
     * @param maxTotalConnections
     */
    public void setMaxTotalConnections(Integer maxTotalConnections) {
        configuration.setMaxTotalConnections(maxTotalConnections);
    }

    public Integer getMaxPerRouteConnections() {
        return configuration.getMaxPerRouteConnections();
    }

    /**
     * Maximum route connections
     * @param maxPerRouteConnections
     */
    public void setMaxPerRouteConnections(Integer maxPerRouteConnections) {
        configuration.setMaxPerRouteConnections(maxPerRouteConnections);
    }

    public boolean isLoggingFilterEnabled() {
        return configuration.isLoggingFilterEnabled();
    }

    /**
     * Whether to use logging filter
     * @param loggingFilterEnabled
     */
    public void setLoggingFilter(boolean loggingFilterEnabled) {
        configuration.setLoggingFilter(loggingFilterEnabled);
    }

    public boolean isFollowRedirectFilterEnabled() {
        return configuration.isFollowRedirectFilterEnabled();
    }

    /**
     * Whether to follow redirect filter
     * @param followRedirectFilterEnabled
     */
    public void setFollowRedirectFilter(boolean followRedirectFilterEnabled) {
        configuration.setFollowRedirectFilter(followRedirectFilterEnabled);
    }

    public Map<String, Object> getParameters() {
        return configuration.getParameters();
    }

    /**
     * Additional configuration parameters as key/value pairs
     * @param parameters
     */
    public void setParameters(Map<String, Object> parameters) {
        configuration.setParameters(parameters);
    }

    public DockerOperation getOperation() {
        return configuration.getOperation();
    }

    /**
     * Which operation to use
     * @param operation
     */
    public void setOperation(DockerOperation operation) {
        configuration.setOperation(operation);
    }
}
