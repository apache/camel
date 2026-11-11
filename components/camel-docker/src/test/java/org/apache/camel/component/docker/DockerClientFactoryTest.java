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
package org.apache.camel.component.docker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The Docker daemon, how the client connects to it, and the registry credentials come from the endpoint, and from the
 * headers of the message only with allowConnectionAndCredentialsFromHeaders.
 */
class DockerClientFactoryTest extends CamelTestSupport {

    private static final String OPTION = "allowConnectionAndCredentialsFromHeaders";

    private static final String HOST = "docker.example.com";
    private static final int PORT = 2376;
    private static final String USERNAME = "endpoint-user";
    private static final String PASSWORD = "endpoint-password";
    private static final String REGISTRY = "https://registry.example.com/v1/";

    private static final String OTHER_HOST = "other.example.com";
    private static final int OTHER_PORT = 4243;
    private static final String OTHER_CERT_PATH = "/etc/docker/other-certs";
    // never loaded, as the component gives its client for every profile
    private static final String OTHER_CMD_EXEC_FACTORY = "org.example.docker.OtherDockerCmdExecFactory";
    private static final String OTHER_USERNAME = "header-user";
    private static final String OTHER_PASSWORD = "header-password";
    private static final String OTHER_REGISTRY = "https://other-registry.example.com/v1/";

    private final DockerClient dockerClient = Mockito.mock(DockerClient.class);
    private DockerComponent component;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        DockerConfiguration configuration = new DockerConfiguration();
        configuration.setHost(HOST);
        configuration.setPort(PORT);
        configuration.setUsername(USERNAME);
        configuration.setPassword(PASSWORD);
        configuration.setServerAddress(REGISTRY);

        // the same client for every profile, so that the tests see the profile that the producer asks a client for
        component = Mockito.spy(new DockerComponent(configuration));
        Mockito.doReturn(dockerClient).when(component).getClient(any());
        camelContext.addComponent("docker", component);

        Mockito.when(dockerClient.authCmd()).thenReturn(Mockito.mock(AuthCmd.class));
        return camelContext;
    }

    static Stream<Arguments> connectionAndCredentialHeaders() {
        return Stream.of(
                Arguments.of(DockerConstants.DOCKER_HOST, OTHER_HOST),
                Arguments.of(DockerConstants.DOCKER_PORT, OTHER_PORT),
                Arguments.of(DockerConstants.DOCKER_SOCKET_ENABLED, true),
                Arguments.of(DockerConstants.DOCKER_SECURE, true),
                Arguments.of(DockerConstants.DOCKER_TLSVERIFY, true),
                Arguments.of(DockerConstants.DOCKER_CERT_PATH, OTHER_CERT_PATH),
                Arguments.of(DockerConstants.DOCKER_CMD_EXEC_FACTORY, OTHER_CMD_EXEC_FACTORY),
                Arguments.of(DockerConstants.DOCKER_USERNAME, OTHER_USERNAME),
                Arguments.of(DockerConstants.DOCKER_PASSWORD, OTHER_PASSWORD),
                Arguments.of(DockerConstants.DOCKER_SERVER_ADDRESS, OTHER_REGISTRY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("connectionAndCredentialHeaders")
    void headerDoesNotChangeTheClientProfileByDefault(String header, Object value) {
        DockerClientProfile endpointProfile = profileOf("docker:auth", Map.of());

        assertEquals(endpointProfile, profileOf("docker:auth", Map.of(header, value)),
                "The " + header + " header changed the client profile");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("connectionAndCredentialHeaders")
    void headerChangesTheClientProfileWithTheOption(String header, Object value) {
        String uri = "docker:auth?" + OPTION + "=true";
        DockerClientProfile endpointProfile = profileOf(uri, Map.of());

        assertNotEquals(endpointProfile, profileOf(uri, Map.of(header, value)),
                "The " + header + " header did not change the client profile");
    }

    @Test
    void pushUsesTheDaemonAndTheRegistryCredentialsOfTheEndpoint() {
        mockPush();

        Exchange exchange = template.send("docker:imagepush", e -> {
            otherDaemonAndRegistry().forEach(e.getIn()::setHeader);
            e.getIn().setHeader(DockerConstants.DOCKER_SOCKET_ENABLED, true);
            e.getIn().setHeader(DockerConstants.DOCKER_NAME, "camel/demo");
        });

        assertNull(exchange.getException());
        DockerClientProfile profile = lastProfile();
        assertEquals("tcp://docker.example.com:2376", profile.toUrl());
        assertFalse(profile.isSecure());
        assertFalse(profile.isTlsVerify());
        assertNull(profile.getCertPath());
        assertEquals(DockerConstants.DEFAULT_CMD_EXEC_FACTORY, profile.getCmdExecFactory());
        assertEquals(USERNAME, profile.getUsername());
        assertEquals(PASSWORD, profile.getPassword());
        assertEquals(REGISTRY, profile.getServerAddress());
        // the headers of the operation still apply
        Mockito.verify(dockerClient).pushImageCmd("camel/demo");
    }

    @Test
    void optionOnTheComponentLetsTheHeadersChooseTheDaemonAndTheRegistry() {
        allowOnTheComponent();
        mockPush();

        Exchange exchange = template.send("docker:imagepush", e -> {
            otherDaemonAndRegistry().forEach(e.getIn()::setHeader);
            e.getIn().setHeader(DockerConstants.DOCKER_NAME, "camel/demo");
        });

        assertNull(exchange.getException());
        DockerClientProfile profile = lastProfile();
        assertEquals("tcp://other.example.com:4243", profile.toUrl());
        assertTrue(profile.isSecure());
        assertTrue(profile.isTlsVerify());
        assertEquals(OTHER_CERT_PATH, profile.getCertPath());
        assertEquals(OTHER_CMD_EXEC_FACTORY, profile.getCmdExecFactory());
        assertEquals(OTHER_USERNAME, profile.getUsername());
        assertEquals(OTHER_PASSWORD, profile.getPassword());
        assertEquals(OTHER_REGISTRY, profile.getServerAddress());
        Mockito.verify(dockerClient).pushImageCmd("camel/demo");
    }

    @Test
    void endpointCanTurnTheOptionOfTheComponentOff() {
        allowOnTheComponent();

        DockerClientProfile profile = profileOf("docker:auth?" + OPTION + "=false",
                Map.of(DockerConstants.DOCKER_HOST, OTHER_HOST, DockerConstants.DOCKER_USERNAME, OTHER_USERNAME));

        assertEquals(HOST, profile.getHost());
        assertEquals(USERNAME, profile.getUsername());
    }

    // as camel.component.docker.allowConnectionAndCredentialsFromHeaders=true sets it
    private void allowOnTheComponent() {
        assertTrue(component.getComponentPropertyConfigurer().configure(context, component, OPTION, true, false),
                "The component has no " + OPTION + " option");
    }

    // the headers of another daemon and registry, in TCP mode, as socket mode does not use the port
    private static Map<String, Object> otherDaemonAndRegistry() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(DockerConstants.DOCKER_HOST, OTHER_HOST);
        headers.put(DockerConstants.DOCKER_PORT, OTHER_PORT);
        headers.put(DockerConstants.DOCKER_SECURE, true);
        headers.put(DockerConstants.DOCKER_TLSVERIFY, true);
        headers.put(DockerConstants.DOCKER_CERT_PATH, OTHER_CERT_PATH);
        headers.put(DockerConstants.DOCKER_CMD_EXEC_FACTORY, OTHER_CMD_EXEC_FACTORY);
        headers.put(DockerConstants.DOCKER_USERNAME, OTHER_USERNAME);
        headers.put(DockerConstants.DOCKER_PASSWORD, OTHER_PASSWORD);
        headers.put(DockerConstants.DOCKER_SERVER_ADDRESS, OTHER_REGISTRY);
        return headers;
    }

    private DockerClientProfile profileOf(String uri, Map<String, Object> headers) {
        Exchange exchange = template.send(uri, e -> headers.forEach(e.getIn()::setHeader));

        assertNull(exchange.getException());
        return lastProfile();
    }

    private DockerClientProfile lastProfile() {
        ArgumentCaptor<DockerClientProfile> profile = ArgumentCaptor.forClass(DockerClientProfile.class);
        Mockito.verify(component, Mockito.atLeastOnce()).getClient(profile.capture());
        return profile.getValue();
    }

    private void mockPush() {
        PushImageCmd push = Mockito.mock(PushImageCmd.class);
        Mockito.when(dockerClient.pushImageCmd(anyString())).thenReturn(push);
        Mockito.when(push.exec(any())).thenAnswer(invocation -> {
            ResultCallback<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });
    }
}
