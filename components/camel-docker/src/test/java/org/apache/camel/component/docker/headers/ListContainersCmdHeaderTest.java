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

import com.github.dockerjava.api.command.ListContainersCmd;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;

/**
 * Validates List Containers Request headers are applied properly
 */
public class ListContainersCmdHeaderTest extends BaseDockerHeaderTest<ListContainersCmd> {

    @Mock
    private ListContainersCmd mockObject;

    @Test
    public void listContainerHeaderTest() {

        boolean showSize = true;
        boolean showAll = false;
        int limit = 2;
        String since = "id1";
        String before = "id2";

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_LIMIT, limit);
        headers.put(DockerConstants.DOCKER_SHOW_ALL, showAll);
        headers.put(DockerConstants.DOCKER_SHOW_SIZE, showSize);
        headers.put(DockerConstants.DOCKER_SINCE, since);
        headers.put(DockerConstants.DOCKER_BEFORE, before);


        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).listContainersCmd();
        Mockito.verify(mockObject, Mockito.times(1)).withShowAll(eq(showAll));
        Mockito.verify(mockObject, Mockito.times(1)).withShowSize(eq(showSize));
        Mockito.verify(mockObject, Mockito.times(1)).withLimit(eq(limit));
        Mockito.verify(mockObject, Mockito.times(1)).withSince(eq(since));
        Mockito.verify(mockObject, Mockito.times(1)).withBefore(eq(before));

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.listContainersCmd()).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.LIST_CONTAINERS;
    }

}
