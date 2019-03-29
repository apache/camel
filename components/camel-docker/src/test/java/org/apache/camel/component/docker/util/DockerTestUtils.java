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
package org.apache.camel.component.docker.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.docker.DockerClientProfile;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.DockerConstants;

public final class DockerTestUtils {
    
    private DockerTestUtils() {
        
    }
    
    public static Map<String, Object> getDefaultParameters(String host, Integer port, DockerConfiguration dockerConfiguration) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(DockerConstants.DOCKER_HOST, host);
        parameters.put(DockerConstants.DOCKER_PORT, port);
        parameters.put(DockerConstants.DOCKER_EMAIL, dockerConfiguration.getEmail());
        parameters.put(DockerConstants.DOCKER_SERVER_ADDRESS, dockerConfiguration.getServerAddress());
        parameters.put(DockerConstants.DOCKER_MAX_PER_ROUTE_CONNECTIONS, dockerConfiguration.getMaxPerRouteConnections());
        parameters.put(DockerConstants.DOCKER_MAX_TOTAL_CONNECTIONS, dockerConfiguration.getMaxTotalConnections());
        parameters.put(DockerConstants.DOCKER_SECURE, dockerConfiguration.isSecure());
        parameters.put(DockerConstants.DOCKER_TLSVERIFY, dockerConfiguration.isTlsVerify());
        parameters.put(DockerConstants.DOCKER_SOCKET_ENABLED, dockerConfiguration.isSocket());
        parameters.put(DockerConstants.DOCKER_CMD_EXEC_FACTORY, dockerConfiguration.getCmdExecFactory());

        return parameters;
    }

    public static DockerClientProfile getClientProfile(String host, Integer port, DockerConfiguration dockerConfiguration) {
        DockerClientProfile clientProfile = new DockerClientProfile();
        clientProfile.setHost(host);
        clientProfile.setPort(port);
        clientProfile.setEmail(dockerConfiguration.getEmail());
        clientProfile.setServerAddress(dockerConfiguration.getServerAddress());
        clientProfile.setMaxPerRouteConnections(dockerConfiguration.getMaxPerRouteConnections());
        clientProfile.setMaxTotalConnections(dockerConfiguration.getMaxTotalConnections());
        clientProfile.setSecure(dockerConfiguration.isSecure());
        clientProfile.setTlsVerify(dockerConfiguration.isTlsVerify());
        clientProfile.setSocket(dockerConfiguration.isSocket());
        clientProfile.setCmdExecFactory(dockerConfiguration.getCmdExecFactory());
        
        return clientProfile;
    }

}