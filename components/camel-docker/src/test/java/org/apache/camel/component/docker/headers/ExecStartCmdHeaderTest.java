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

import com.github.dockerjava.api.command.ExecStartCmd;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Validates Exec Start Request headers are parsed properly
 */
public class ExecStartCmdHeaderTest extends BaseDockerHeaderTest<ExecStartCmd> {

    @Mock
    private ExecStartCmd mockObject;

    @Test
    public void execCreateHeaderTest() {

        String id = "1";
        boolean tty = true;

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_EXEC_ID, id);
        headers.put(DockerConstants.DOCKER_TTY, tty);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).execStartCmd(Matchers.eq(id));
        Mockito.verify(mockObject, Mockito.times(1)).withTty(Matchers.eq(tty));

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.execStartCmd(Matchers.anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.EXEC_START;
    }

}
