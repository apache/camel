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

import com.github.dockerjava.api.command.CommitCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.ExposedPorts;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.Volumes;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Validates Commit Container Request headers are parsed properly
 */
public class CommitContainerCmdHeaderTest extends BaseDockerHeaderTest<CommitCmd> {


    @Mock
    private CommitCmd mockObject;

    @Test
    public void commitContainerHeaderTest() {

        String containerId = "9c09acd48a25";
        String env = "FOO=bar";
        boolean attachStdErr = true;
        boolean attachStdOut = true;
        boolean attachStdIn = false;
        boolean disableNetwork = false;
        boolean openStdIn = false;
        String portSpecs = "80";
        boolean stdInOnce = false;
        String tag = "1.0";
        String repository = "docker/empty";
        String cmd = "whoami";
        String author = "cameluser";
        String message = "Camel Docker Container Commit";
        boolean pause = false;
        ExposedPorts exposedPorts = new ExposedPorts(ExposedPort.tcp(22));
        Integer memory = 2048;
        Integer swapMemory = 512;
        String workingDir = "/opt";
        String user = "docker";
        Volumes volumes = new Volumes(new Volume("/example"));
        boolean tty = true;
        String hostname = "dockerhostname";


        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_ENV, env);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_IN, attachStdIn);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_ERR, attachStdErr);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_OUT, attachStdOut);
        headers.put(DockerConstants.DOCKER_DISABLE_NETWORK, disableNetwork);
        headers.put(DockerConstants.DOCKER_TAG, tag);
        headers.put(DockerConstants.DOCKER_REPOSITORY, repository);
        headers.put(DockerConstants.DOCKER_CMD, cmd);
        headers.put(DockerConstants.DOCKER_AUTHOR, author);
        headers.put(DockerConstants.DOCKER_MESSAGE, message);
        headers.put(DockerConstants.DOCKER_PAUSE, pause);
        headers.put(DockerConstants.DOCKER_EXPOSED_PORTS, exposedPorts);
        headers.put(DockerConstants.DOCKER_MEMORY, memory);
        headers.put(DockerConstants.DOCKER_MEMORY_SWAP, swapMemory);
        headers.put(DockerConstants.DOCKER_WORKING_DIR, workingDir);
        headers.put(DockerConstants.DOCKER_USER, user);
        headers.put(DockerConstants.DOCKER_VOLUMES, volumes);
        headers.put(DockerConstants.DOCKER_TTY, tty);
        headers.put(DockerConstants.DOCKER_HOSTNAME, hostname);
        headers.put(DockerConstants.DOCKER_OPEN_STD_IN, openStdIn);
        headers.put(DockerConstants.DOCKER_STD_IN_ONCE, stdInOnce);
        headers.put(DockerConstants.DOCKER_PORT_SPECS, portSpecs);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).commitCmd(containerId);
        Mockito.verify(mockObject, Mockito.times(1)).withEnv(env);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStderr(attachStdErr);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdin(attachStdIn);
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdout(attachStdOut);
        Mockito.verify(mockObject, Mockito.times(1)).withDisableNetwork(disableNetwork);
        Mockito.verify(mockObject, Mockito.times(1)).withTag(tag);
        Mockito.verify(mockObject, Mockito.times(1)).withRepository(repository);
        Mockito.verify(mockObject, Mockito.times(1)).withCmd(cmd);
        Mockito.verify(mockObject, Mockito.times(1)).withAuthor(author);
        Mockito.verify(mockObject, Mockito.times(1)).withMessage(message);
        Mockito.verify(mockObject, Mockito.times(1)).withPause(pause);
        Mockito.verify(mockObject, Mockito.times(1)).withExposedPorts(exposedPorts);
        Mockito.verify(mockObject, Mockito.times(1)).withMemory(memory);
        Mockito.verify(mockObject, Mockito.times(1)).withMemorySwap(swapMemory);
        Mockito.verify(mockObject, Mockito.times(1)).withWorkingDir(workingDir);
        Mockito.verify(mockObject, Mockito.times(1)).withUser(user);
        Mockito.verify(mockObject, Mockito.times(1)).withVolumes(volumes);
        Mockito.verify(mockObject, Mockito.times(1)).withTty(tty);
        Mockito.verify(mockObject, Mockito.times(1)).withHostname(hostname);
        Mockito.verify(mockObject, Mockito.times(1)).withOpenStdin(openStdIn);
        Mockito.verify(mockObject, Mockito.times(1)).withStdinOnce(stdInOnce);
        Mockito.verify(mockObject, Mockito.times(1)).withPortSpecs(portSpecs);

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.commitCmd(anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.COMMIT_CONTAINER;
    }

}
