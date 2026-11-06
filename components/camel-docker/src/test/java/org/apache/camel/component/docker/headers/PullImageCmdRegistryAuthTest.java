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
package org.apache.camel.component.docker.headers;

import java.util.Map;

import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Validates that a pull image request uses the registry authentication of the endpoint only for images of the registry
 * of the endpoint
 */
public class PullImageCmdRegistryAuthTest extends BaseDockerHeaderTest<PullImageCmd> {

    private static final String USERNAME = "jdoe";
    private static final String SERVER_ADDRESS = "https://registry.example.com:5000/v2/";

    @Mock
    private PullImageCmd mockObject;

    @Mock
    private PullImageResultCallback callback;

    @Test
    void pullImageOfEndpointRegistrySendsEndpointCredentials() {
        AuthConfig authConfig = endpointAuthConfig();
        Mockito.when(dockerClient.authConfig()).thenReturn(authConfig);

        pull("registry.example.com:5000/team/app");

        Mockito.verify(mockObject, Mockito.times(1)).withAuthConfig(authConfig);
        Mockito.verify(mockObject, Mockito.times(1)).exec(any());
    }

    @Test
    void pullImageOfOtherRegistrySendsNoCredentials() {
        Mockito.when(dockerClient.authConfig()).thenReturn(endpointAuthConfig());

        pull("registry.example.org/team/app");

        Mockito.verify(mockObject, Mockito.never()).withAuthConfig(any());
        Mockito.verify(mockObject, Mockito.times(1)).exec(any());
    }

    @Test
    void pullImageOfDockerHubSendsNoCredentials() {
        Mockito.when(dockerClient.authConfig()).thenReturn(endpointAuthConfig());

        pull("team/app");

        Mockito.verify(mockObject, Mockito.never()).withAuthConfig(any());
        Mockito.verify(mockObject, Mockito.times(1)).exec(any());
    }

    private void pull(String repository) {
        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_REPOSITORY, repository);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).pullImageCmd(repository);
    }

    private static AuthConfig endpointAuthConfig() {
        return new AuthConfig().withUsername(USERNAME).withPassword("secret").withRegistryAddress(SERVER_ADDRESS);
    }

    @Override
    protected String getServerAddress() {
        return SERVER_ADDRESS;
    }

    @Override
    protected String getUsername() {
        return USERNAME;
    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.pullImageCmd(anyString())).thenReturn(mockObject);
        Mockito.when(mockObject.exec(any())).thenReturn(callback);
        try {
            Mockito.when(callback.awaitCompletion()).thenReturn(callback);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.PULL_IMAGE;
    }

}
