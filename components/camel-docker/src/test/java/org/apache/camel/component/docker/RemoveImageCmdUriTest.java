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
package org.apache.camel.component.docker;

import java.util.Map;

import com.github.dockerjava.api.command.RemoveImageCmd;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.headers.BaseDockerHeaderTest;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Validates Remove Image Request URI parameters are applied properly
 */
public class RemoveImageCmdUriTest extends BaseDockerHeaderTest<RemoveImageCmd> {

    private String imageId = "be29975e0098";
    private Boolean noPrune = false;
    private Boolean force = true;

    @Mock
    private RemoveImageCmd mockObject;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:in").to("docker://" + getOperation().toString() + "?imageId=" + imageId + "&noPrune=" + noPrune + "&force=" + force);

            }
        };
    }

    @Ignore
    @Test
    public void removeImageHeaderTest() {

        Map<String, Object> headers = getDefaultParameters();

        template.sendBodyAndHeaders("direct:in", "", headers);

        Mockito.verify(dockerClient, Mockito.times(1)).removeImageCmd(imageId);
    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.removeImageCmd(anyString())).thenReturn(mockObject);
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.REMOVE_IMAGE;
    }

}
