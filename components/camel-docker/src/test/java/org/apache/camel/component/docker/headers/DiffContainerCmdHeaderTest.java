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

import com.github.dockerjava.api.command.ContainerDiffCmd;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Validates Container Diff Request headers are parsed properly
 */
public class DiffContainerCmdHeaderTest extends BaseDockerHeaderTest<ContainerDiffCmd> {

    @Mock
    private ContainerDiffCmd mockObject;

    @Test
    public void containerDiffHeaderTest() {

        String containerId = "9c09acd48a25";
        String containerIdDiff = "0f9dfe8defa0";

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_CONTAINER_ID_DIFF, containerIdDiff);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).containerDiffCmd(containerId);
        Mockito.verify(mockObject, Mockito.times(1)).withContainerId(containerIdDiff);

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.containerDiffCmd(anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.DIFF_CONTAINER;
    }

}
