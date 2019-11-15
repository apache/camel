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

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Validates Create Container Request headers are parsed properly
 */
public class CreateContainerCmdHeaderTest extends BaseDockerHeaderTest<CreateContainerCmd> {


    @Mock
    private CreateContainerCmd mockObject;

    @Mock
    private HostConfig hostConfig;

    @Test
    public void createContainerHeaderTest() {

        String image = "busybox";
        ExposedPort exposedPort = ExposedPort.tcp(22);
        boolean tty = true;
        String name = "cameldocker";
        String workingDir = "/opt";
        boolean disableNetwork = false;
        String domainName = "apache.org";
        String hostname = "dockerjava";
        String user = "docker";
        boolean stdInOpen = false;
        boolean stdInOnce = false;
        boolean attachStdErr = true;
        boolean attachStdOut = true;
        boolean attachStdIn = false;
        Long memoryLimit = 2048L;
        Long swapMemory = 512L;
        Integer cpuShares = 512;
        Volume volumes = new Volume("/example");
        VolumesFrom volumesFromContainer = new VolumesFrom("/etc");
        String env = "FOO=bar";
        String cmd = "whoami";
        Capability capAdd = Capability.NET_BROADCAST;
        Capability capDrop = Capability.BLOCK_SUSPEND;
        String[] entrypoint = new String[]{"sleep", "9999"};
        String portSpecs = "80";
        String dns = "8.8.8.8";


        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_IMAGE, image);
        headers.put(DockerConstants.DOCKER_EXPOSED_PORTS, exposedPort);
        headers.put(DockerConstants.DOCKER_TTY, tty);
        headers.put(DockerConstants.DOCKER_NAME, name);
        headers.put(DockerConstants.DOCKER_WORKING_DIR, workingDir);
        headers.put(DockerConstants.DOCKER_DISABLE_NETWORK, disableNetwork);
        headers.put(DockerConstants.DOCKER_HOSTNAME, hostname);
        headers.put(DockerConstants.DOCKER_USER, user);
        headers.put(DockerConstants.DOCKER_STD_IN_OPEN, stdInOpen);
        headers.put(DockerConstants.DOCKER_STD_IN_ONCE, stdInOnce);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_IN, attachStdIn);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_ERR, attachStdErr);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_OUT, attachStdOut);
        headers.put(DockerConstants.DOCKER_MEMORY_LIMIT, memoryLimit);
        headers.put(DockerConstants.DOCKER_MEMORY_SWAP, swapMemory);
        headers.put(DockerConstants.DOCKER_CPU_SHARES, cpuShares);
        headers.put(DockerConstants.DOCKER_VOLUMES, volumes);
        headers.put(DockerConstants.DOCKER_VOLUMES_FROM, volumesFromContainer);
        headers.put(DockerConstants.DOCKER_ENV, env);
        headers.put(DockerConstants.DOCKER_CMD, cmd);
        headers.put(DockerConstants.DOCKER_HOST_CONFIG, hostConfig);
        headers.put(DockerConstants.DOCKER_CAP_ADD, capAdd);
        headers.put(DockerConstants.DOCKER_CAP_DROP, capDrop);
        headers.put(DockerConstants.DOCKER_ENTRYPOINT, entrypoint);
        headers.put(DockerConstants.DOCKER_PORT_SPECS, portSpecs);
        headers.put(DockerConstants.DOCKER_DNS, dns);
        headers.put(DockerConstants.DOCKER_DOMAIN_NAME, domainName);

        Mockito.when(mockObject.getHostConfig()).thenReturn(hostConfig);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).createContainerCmd(image);
        Mockito.verify(mockObject, Mockito.times(1)).withExposedPorts(eq(exposedPort));
        Mockito.verify(mockObject, Mockito.times(1)).withTty(eq(tty));
        Mockito.verify(mockObject, Mockito.times(1)).withName(eq(name));
        Mockito.verify(mockObject, Mockito.times(1)).withWorkingDir(workingDir);
        Mockito.verify(mockObject, Mockito.times(1)).withNetworkDisabled(disableNetwork);
        Mockito.verify(mockObject, Mockito.times(1)).withHostName(hostname);
        Mockito.verify(mockObject, Mockito.times(1)).withUser(user);
        Mockito.verify(mockObject, Mockito.times(1)).withStdinOpen(stdInOpen);
        Mockito.verify(mockObject, Mockito.times(1)).withStdInOnce(stdInOnce);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStderr(attachStdErr);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdin(attachStdIn);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdout(attachStdOut);
        Mockito.verify(mockObject, Mockito.times(1)).withVolumes(volumes);
        Mockito.verify(mockObject, Mockito.times(1)).withEnv(env);
        Mockito.verify(mockObject, Mockito.times(1)).withCmd(cmd);
        Mockito.verify(mockObject, Mockito.times(1)).withHostConfig(hostConfig);
        Mockito.verify(mockObject, Mockito.times(1)).withEntrypoint(entrypoint);
        Mockito.verify(mockObject, Mockito.times(1)).withPortSpecs(portSpecs);
        Mockito.verify(mockObject, Mockito.times(1)).withDomainName(domainName);

        Mockito.verify(hostConfig, Mockito.times(1)).withVolumesFrom(volumesFromContainer);
        Mockito.verify(hostConfig, Mockito.times(1)).withCapAdd(capAdd);
        Mockito.verify(hostConfig, Mockito.times(1)).withCapDrop(capDrop);
        Mockito.verify(hostConfig, Mockito.times(1)).withDns(dns);
        Mockito.verify(hostConfig, Mockito.times(1)).withMemory(memoryLimit);
        Mockito.verify(hostConfig, Mockito.times(1)).withMemorySwap(swapMemory);
        Mockito.verify(hostConfig, Mockito.times(1)).withCpuShares(cpuShares);
    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.createContainerCmd(anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.CREATE_CONTAINER;
    }

}
