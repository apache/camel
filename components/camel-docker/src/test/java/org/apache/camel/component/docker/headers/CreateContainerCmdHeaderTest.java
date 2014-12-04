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

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Validates Create Container Request headers are parsed properly
 */
public class CreateContainerCmdHeaderTest extends BaseDockerHeaderTest<CreateContainerCmd> {

    
    @Mock
    private CreateContainerCmd mockObject;
    
    @Test
    public void createContainerHeaderTest() {
        
        String imageId = "be29975e0098";
        ExposedPort tcp22 = ExposedPort.tcp(22);

        
        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_IMAGE_ID, imageId);
        headers.put(DockerConstants.DOCKER_EXPOSED_PORTS, tcp22);

        
        template.sendBodyAndHeaders("direct:in", "", headers);
        
        Mockito.verify(dockerClient, Mockito.times(1)).createContainerCmd(imageId);
        Mockito.verify(mockObject, Mockito.times(1)).withExposedPorts(Matchers.any(ExposedPort.class));
        
    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.createContainerCmd(Matchers.anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.CREATE_CONTAINER;
    }

}
