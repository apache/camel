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

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Validates Log Container Request headers are applied properly
 */
public class LogContainerCmdHeaderTest extends BaseDockerHeaderTest<LogContainerCmd> {
    private static final Logger LOG = LoggerFactory.getLogger(LogContainerCmdHeaderTest.class);

    @Mock
    private LogContainerCmd mockObject;

    @Mock
    private ResultCallback.Adapter<Frame> callback;

    @Test
    void logContainerHeaderTest() {

        String containerId = "9c09acd48a25";
        boolean stdOut = true;
        boolean stdErr = true;
        boolean followStream = true;
        boolean timestamps = false;
        boolean tailAll = true;
        int tail = 5;

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_CONTAINER_ID, containerId);
        headers.put(DockerConstants.DOCKER_FOLLOW_STREAM, followStream);
        headers.put(DockerConstants.DOCKER_STD_OUT, stdOut);
        headers.put(DockerConstants.DOCKER_STD_ERR, stdErr);
        headers.put(DockerConstants.DOCKER_TIMESTAMPS, timestamps);
        headers.put(DockerConstants.DOCKER_TAIL, tail);
        headers.put(DockerConstants.DOCKER_TAIL_ALL, tailAll);

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).logContainerCmd(containerId);
        Mockito.verify(mockObject, Mockito.times(1)).withFollowStream(eq(followStream));
        Mockito.verify(mockObject, Mockito.times(1)).withTail(eq(tail));
        Mockito.verify(mockObject, Mockito.times(1)).withTailAll();
        Mockito.verify(mockObject, Mockito.times(1)).withStdErr(eq(stdErr));
        Mockito.verify(mockObject, Mockito.times(1)).withStdOut(eq(stdOut));
        Mockito.verify(mockObject, Mockito.times(1)).withTimestamps(eq(timestamps));

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.logContainerCmd(anyString())).thenReturn(mockObject);
        Mockito.when(mockObject.exec(any())).thenReturn(callback);
        try {
            Mockito.when(callback.awaitCompletion()).thenReturn(callback);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while setting up mocks", e);
        }
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.LOG_CONTAINER;
    }

}
