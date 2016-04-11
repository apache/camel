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

import com.github.dockerjava.api.command.AttachContainerCmd;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Validates Attach Container Request headers are applied properly
 */
public class AttachContainerCmdHeaderTest extends BaseDockerHeaderTest<AttachContainerCmd> {

    @Mock
    private AttachContainerCmd mockObject;

    @Test
    public void attachContainerHeaderTest() {

        String containerId = "9c09acd48a25";
        boolean stdOut = true;
        boolean stdErr = true;
        boolean followStream = false;
        boolean logs = true;
        boolean timestamps = true;

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_FOLLOW_STREAM, followStream);
        headers.put(DockerConstants.DOCKER_STD_OUT, stdOut);
        headers.put(DockerConstants.DOCKER_STD_ERR, stdErr);
        headers.put(DockerConstants.DOCKER_TIMESTAMPS, timestamps);
        headers.put(DockerConstants.DOCKER_LOGS, logs);


        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).attachContainerCmd(containerId);
        Mockito.verify(mockObject, Mockito.times(1)).withFollowStream(Matchers.eq(followStream));
        Mockito.verify(mockObject, Mockito.times(1)).withLogs(Matchers.eq(logs));
        Mockito.verify(mockObject, Mockito.times(1)).withStdErr(Matchers.eq(stdErr));
        Mockito.verify(mockObject, Mockito.times(1)).withStdOut(Matchers.eq(stdOut));
        Mockito.verify(mockObject, Mockito.times(1)).withTimestamps(Matchers.eq(timestamps));

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.attachContainerCmd(Matchers.anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.ATTACH_CONTAINER;
    }

}
