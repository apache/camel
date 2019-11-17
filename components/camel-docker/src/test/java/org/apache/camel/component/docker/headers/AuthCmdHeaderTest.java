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

import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.api.model.AuthConfig;
import org.apache.camel.component.docker.DockerClientProfile;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

/**
 * Validates Authentication Request headers are parsed properly
 */
public class AuthCmdHeaderTest extends BaseDockerHeaderTest<AuthCmd> {

    @Mock
    private AuthCmd mockObject;

    private String userName = "jdoe";
    private String password = "password";
    private String email = "jdoe@example.com";
    private String serverAddress = "http://docker.io/v1";

    @Test
    public void authHeaderTest() {
        String userName = "jdoe";
        String password = "password";
        String email = "jdoe@example.com";
        String serverAddress = "http://docker.io/v1";

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_USERNAME, userName);
        headers.put(DockerConstants.DOCKER_PASSWORD, password);
        headers.put(DockerConstants.DOCKER_EMAIL, email);
        headers.put(DockerConstants.DOCKER_SERVER_ADDRESS, serverAddress);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).authCmd();
        Mockito.verify(mockObject, Mockito.times(1)).withAuthConfig((AuthConfig) any());

    }

    @Override
    public DockerClientProfile getClientProfile() {
        DockerClientProfile clientProfile = super.getClientProfile();
        clientProfile.setEmail(email);
        clientProfile.setPassword(password);
        clientProfile.setUsername(userName);
        clientProfile.setServerAddress(serverAddress);

        return clientProfile;
    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.authCmd()).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.AUTH;
    }

}
