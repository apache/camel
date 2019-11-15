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

import com.github.dockerjava.api.command.ExecCreateCmd;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Validates Exec Create Request headers are parsed properly
 */
public class ExecCreateCmdHeaderTest extends BaseDockerHeaderTest<ExecCreateCmd> {

    @Mock
    private ExecCreateCmd mockObject;

    @Test
    public void execCreateHeaderTest() {

        String containerId = "9c09acd48a25";
        boolean tty = true;
        boolean stdErr = false;
        boolean stdOut = true;
        boolean stdIn = true;


        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_TTY, tty);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_ERR, stdErr);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_OUT, stdOut);
        headers.put(DockerConstants.DOCKER_ATTACH_STD_IN, stdIn);
        headers.put(DockerConstants.DOCKER_CMD, "date;whoami");


        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).execCreateCmd(eq(containerId));
        Mockito.verify(mockObject, Mockito.times(1)).withTty(eq(tty));
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStderr(eq(stdErr));
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdout(eq(stdOut));
        Mockito.verify(mockObject, Mockito.times(1)).withAttachStdin(eq(stdIn));
        Mockito.verify(mockObject, Mockito.times(1)).withCmd(new String[]{"date", "whoami"});


    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.execCreateCmd(anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.EXEC_CREATE;
    }

}
