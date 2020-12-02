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

package org.apache.camel.test.infra.hdfs.v2.services;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.dockerclient.DockerClientConfigUtils;

public class DataNodeContainer extends HadoopBaseContainer<DataNodeContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(DataNodeContainer.class);
    private static String dataNodeHost = "localhost";

    static {
        String dockerHost = System.getenv("DOCKER_HOST");

        if (dockerHost != null && !dockerHost.isEmpty()) {
            try {
                URI dockerHostUri = new URI(dockerHost);
                dataNodeHost = DockerClientConfigUtils.getDockerHostIpAddress(dockerHostUri);

            } catch (URISyntaxException e) {
                LOG.warn("Using 'localhost' as the docker host because the URI '{}' for did not parse correctly: {}",
                        dockerHost, e.getMessage(), e);
            }
        }
    }

    public DataNodeContainer(Network network) {
        this(network, dataNodeHost);
    }

    public DataNodeContainer(Network network, String name) {
        super(network, name);

        withCommand("sh", "-c", "/hadoop/run-datanode.sh");

        withExposedPorts(HDFSPorts.DATA_NODE_HTTP_PORT, HDFSPorts.DATA_NODE_DATA_TRANSFER_PORT, HDFSPorts.DATA_NODE_IPC_PORT);

        waitingFor(Wait.forHttp("/").forPort(HDFSPorts.DATA_NODE_HTTP_PORT));

        /*
         We need the name to be a valid hostname: the files are uploaded
         directly to the dataNode host using the *hostname*. By default, the hostname
         is not valid and no accessible from outside, therefore we trick the container
         into using the localhost name so when the data node is resolved, it actually
         points to the port on the local host that is redirected inside the container.
         */
        withCreateContainerCmdModifier(
                createContainerCmd -> {
                    createContainerCmd.withHostName(name);
                    createContainerCmd.withName(name);
                });

        addFixedExposedPort(HDFSPorts.DATA_NODE_HTTP_PORT, HDFSPorts.DATA_NODE_HTTP_PORT);
        addFixedExposedPort(HDFSPorts.DATA_NODE_DATA_TRANSFER_PORT, HDFSPorts.DATA_NODE_DATA_TRANSFER_PORT);
        addFixedExposedPort(HDFSPorts.DATA_NODE_IPC_PORT, HDFSPorts.DATA_NODE_IPC_PORT);
    }

    public int getHttpPort() {
        return getMappedPort(HDFSPorts.DATA_NODE_HTTP_PORT);
    }

    public int getDataTransferPort() {
        return HDFSPorts.DATA_NODE_DATA_TRANSFER_PORT;
    }

    public int getIpcPort() {
        return HDFSPorts.DATA_NODE_IPC_PORT;
    }

}
