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

import com.github.dockerjava.api.command.PullImageCmd;

import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Validates Pull Image Request headers are applied properly
 */
public class PullImageCmdHeaderTest extends BaseDockerHeaderTest<PullImageCmd> {

    @Mock
    private PullImageCmd mockObject;

    @Test
    public void pullImageHeaderTest() {

        String repository = "docker/empty";
        String tag = "1.0";
        String registry = "registry";

        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_REPOSITORY, repository);
        headers.put(DockerConstants.DOCKER_TAG, tag);
        headers.put(DockerConstants.DOCKER_REGISTRY, registry);


        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).pullImageCmd(repository);
        Mockito.verify(mockObject, Mockito.times(1)).withTag(Matchers.eq(tag));
        Mockito.verify(mockObject, Mockito.times(1)).withRegistry(Matchers.eq(registry));

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.pullImageCmd(Matchers.anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.PULL_IMAGE;
    }

}
