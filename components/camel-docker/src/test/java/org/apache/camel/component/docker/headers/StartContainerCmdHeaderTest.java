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
package org.apache.camel.component.docker.headers;

import java.util.Map;

import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Validates Start Container Request headers are applied properly
 */
public class StartContainerCmdHeaderTest extends BaseDockerHeaderTest<StartContainerCmd> {

    @Mock
    ExposedPort exposedPort;

    @Mock
    private StartContainerCmd mockObject;

    @Test
    public void startContainerHeaderTest() {

        String containerId = "be29975e0098";
        Volume vol = new Volume("/opt/webapp1");
        Bind[] binds = new Bind[]{new Bind("/opt/webapp1", vol)};
        boolean publishAllPorts = false;
        boolean privileged = false;
        String[] dns = new String[]{"8.8.8.8"};
        Link[] links = new Link[]{new Link("container1", "container1Link")};
        String networkMode = "host";
        LxcConf[] lxcConf = new LxcConf[]{new LxcConf()};
        String volumesFromContainer = "container2";
        Capability capAdd = Capability.NET_BROADCAST;
        Capability capDrop = Capability.BLOCK_SUSPEND;
        Device[] devices = new Device[]{new Device("rwm", "/dev/nulo", "/dev/zero")};
        RestartPolicy restartPolicy = RestartPolicy.noRestart();
        PortBinding[] portBindings = new PortBinding[]{new PortBinding(Ports.Binding(28768), ExposedPort.tcp(22))};
        Ports ports = new Ports(ExposedPort.tcp(22), Ports.Binding(11022));

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_BINDS, binds);
        headers.put(DockerConstants.DOCKER_PUBLISH_ALL_PORTS, publishAllPorts);
        headers.put(DockerConstants.DOCKER_PRIVILEGED, privileged);
        headers.put(DockerConstants.DOCKER_DNS, dns);
        headers.put(DockerConstants.DOCKER_DNS_SEARCH, dns);
        headers.put(DockerConstants.DOCKER_LINKS, links);
        headers.put(DockerConstants.DOCKER_NETWORK_MODE, networkMode);
        headers.put(DockerConstants.DOCKER_LXC_CONF, lxcConf);
        headers.put(DockerConstants.DOCKER_VOLUMES_FROM, volumesFromContainer);
        headers.put(DockerConstants.DOCKER_CAP_ADD, capAdd);
        headers.put(DockerConstants.DOCKER_CAP_DROP, capDrop);
        headers.put(DockerConstants.DOCKER_DEVICES, devices);
        headers.put(DockerConstants.DOCKER_RESTART_POLICY, restartPolicy);
        headers.put(DockerConstants.DOCKER_PORT_BINDINGS, portBindings);
        headers.put(DockerConstants.DOCKER_PORTS, ports);


        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).startContainerCmd(containerId);
        Mockito.verify(mockObject, Mockito.times(1)).withBinds(binds);
        Mockito.verify(mockObject, Mockito.times(1)).withPublishAllPorts(publishAllPorts);
        Mockito.verify(mockObject, Mockito.times(1)).withPrivileged(privileged);
        Mockito.verify(mockObject, Mockito.times(1)).withDns(dns);
        Mockito.verify(mockObject, Mockito.times(1)).withDnsSearch(dns);
        Mockito.verify(mockObject, Mockito.times(1)).withLinks(links);
        Mockito.verify(mockObject, Mockito.times(1)).withNetworkMode(networkMode);
        Mockito.verify(mockObject, Mockito.times(1)).withLxcConf(lxcConf);
        Mockito.verify(mockObject, Mockito.times(1)).withVolumesFrom(volumesFromContainer);
        Mockito.verify(mockObject, Mockito.times(1)).withCapAdd(capAdd);
        Mockito.verify(mockObject, Mockito.times(1)).withCapDrop(capDrop);
        Mockito.verify(mockObject, Mockito.times(1)).withDevices(devices);
        Mockito.verify(mockObject, Mockito.times(1)).withRestartPolicy(restartPolicy);
        Mockito.verify(mockObject, Mockito.times(1)).withPortBindings(portBindings);
        Mockito.verify(mockObject, Mockito.times(1)).withPortBindings(ports);

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.startContainerCmd(Matchers.anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.START_CONTAINER;
    }

}
