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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.LocalDirectorySSLConfig;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.component.docker.ssl.NoImplSslConfig;
import org.apache.camel.support.ObjectHelper;

/**
 * Methods for communicating with Docker
 */
public final class DockerClientFactory {

    private DockerClientFactory() {
        // Helper class
    }

    /**
     * Produces a {@link DockerClient} to communicate with Docker
     */
    @SuppressWarnings("resource")
    public static DockerClient getDockerClient(DockerComponent dockerComponent, DockerConfiguration dockerConfiguration, Message message) throws DockerException {

        org.apache.camel.util.ObjectHelper.notNull(dockerConfiguration, "dockerConfiguration");

        Integer port = DockerHelper.getProperty(DockerConstants.DOCKER_PORT, dockerConfiguration, message, Integer.class, dockerConfiguration.getPort());
        String host = DockerHelper.getProperty(DockerConstants.DOCKER_HOST, dockerConfiguration, message, String.class, dockerConfiguration.getHost());

        Integer maxTotalConnections = DockerHelper.getProperty(DockerConstants.DOCKER_MAX_TOTAL_CONNECTIONS, dockerConfiguration, message, Integer.class,
                                                               dockerConfiguration.getMaxTotalConnections());
        Integer maxPerRouteConnections = DockerHelper.getProperty(DockerConstants.DOCKER_MAX_PER_ROUTE_CONNECTIONS, dockerConfiguration, message, Integer.class,
                                                                  dockerConfiguration.getMaxPerRouteConnections());

        String username = DockerHelper.getProperty(DockerConstants.DOCKER_USERNAME, dockerConfiguration, message, String.class, dockerConfiguration.getUsername());
        String password = DockerHelper.getProperty(DockerConstants.DOCKER_PASSWORD, dockerConfiguration, message, String.class, dockerConfiguration.getPassword());
        String email = DockerHelper.getProperty(DockerConstants.DOCKER_EMAIL, dockerConfiguration, message, String.class, dockerConfiguration.getEmail());
        Integer requestTimeout = DockerHelper.getProperty(DockerConstants.DOCKER_API_REQUEST_TIMEOUT, dockerConfiguration, message, Integer.class,
                                                          dockerConfiguration.getRequestTimeout());
        String serverAddress = DockerHelper.getProperty(DockerConstants.DOCKER_SERVER_ADDRESS, dockerConfiguration, message, String.class, dockerConfiguration.getServerAddress());
        String certPath = DockerHelper.getProperty(DockerConstants.DOCKER_CERT_PATH, dockerConfiguration, message, String.class, dockerConfiguration.getCertPath());
        Boolean secure = DockerHelper.getProperty(DockerConstants.DOCKER_SECURE, dockerConfiguration, message, Boolean.class, dockerConfiguration.isSecure());
        Boolean tlsVerify = DockerHelper.getProperty(DockerConstants.DOCKER_TLSVERIFY, dockerConfiguration, message, Boolean.class, dockerConfiguration.isTlsVerify());
        Boolean socket = DockerHelper.getProperty(DockerConstants.DOCKER_SOCKET_ENABLED, dockerConfiguration, message, Boolean.class, dockerConfiguration.isSocket());

        String cmdExecFactory = DockerHelper.getProperty(DockerConstants.DOCKER_CMD_EXEC_FACTORY,
            dockerConfiguration, message, String.class, dockerConfiguration.getCmdExecFactory());

        DockerClientProfile clientProfile = new DockerClientProfile();
        clientProfile.setHost(host);
        clientProfile.setEmail(email);
        clientProfile.setUsername(username);
        clientProfile.setPassword(password);
        clientProfile.setRequestTimeout(requestTimeout);
        clientProfile.setServerAddress(serverAddress);
        clientProfile.setCertPath(certPath);
        clientProfile.setMaxTotalConnections(maxTotalConnections);
        clientProfile.setMaxPerRouteConnections(maxPerRouteConnections);
        clientProfile.setSecure(secure);
        clientProfile.setTlsVerify(tlsVerify);
        clientProfile.setSocket(socket);
        clientProfile.setCmdExecFactory(cmdExecFactory);

        if (!socket) {
            clientProfile.setPort(port);
        }

        DockerClient dockerClient = dockerComponent.getClient(clientProfile);

        if (dockerClient != null) {
            return dockerClient;
        }

        SSLConfig sslConfig;
        if (clientProfile.isSecure() != null && clientProfile.isSecure()) {
            org.apache.camel.util.ObjectHelper.notNull(clientProfile.getCertPath(), "certPath must be specified in secure mode");
            sslConfig = new LocalDirectorySSLConfig(clientProfile.getCertPath());
        } else {
            // docker-java requires an implementation of SslConfig interface
            // to be available for DockerCmdExecFactoryImpl
            sslConfig = new NoImplSslConfig();
        }

        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(clientProfile.toUrl())
            .withDockerTlsVerify(clientProfile.isTlsVerify())
            .withRegistryUsername(clientProfile.getUsername())
            .withRegistryPassword(clientProfile.getPassword())
            .withRegistryEmail(clientProfile.getEmail())
            .withRegistryUrl(clientProfile.getServerAddress())
            .withCustomSslConfig(sslConfig);

        if (clientProfile.getCertPath() != null) {
            configBuilder.withDockerCertPath(clientProfile.getCertPath());
        }

        CamelContext camelContext = dockerComponent.getCamelContext();
        try {
            DockerCmdExecFactory factory = null;

            if (cmdExecFactory.equals(JerseyDockerCmdExecFactory.class.getName())) {
                factory = new JerseyDockerCmdExecFactory();
                ((JerseyDockerCmdExecFactory) factory)
                    .withReadTimeout(clientProfile.getRequestTimeout())
                    .withConnectTimeout(clientProfile.getRequestTimeout())
                    .withMaxTotalConnections(clientProfile.getMaxTotalConnections())
                    .withMaxPerRouteConnections(clientProfile.getMaxPerRouteConnections());
            } else if (cmdExecFactory.equals(NettyDockerCmdExecFactory.class.getName())) {
                factory = new NettyDockerCmdExecFactory();
                ((NettyDockerCmdExecFactory) factory)
                    .withConnectTimeout(clientProfile.getRequestTimeout());
            } else {
                Class<DockerCmdExecFactory> clazz = camelContext.getClassResolver().resolveMandatoryClass(cmdExecFactory, DockerCmdExecFactory.class);
                factory = ObjectHelper.newInstance(clazz);
            }

            dockerClient = DockerClientBuilder.getInstance(configBuilder)
                .withDockerCmdExecFactory(factory)
                .build();

            dockerComponent.setClient(clientProfile, dockerClient);

            return dockerClient;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to resolve DockerCmdExecFactory class: " + cmdExecFactory, e);
        }
    }

}
