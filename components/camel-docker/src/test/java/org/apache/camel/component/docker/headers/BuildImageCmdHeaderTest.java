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

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Validates Build Image Request headers are parsed properly
 */
public class BuildImageCmdHeaderTest extends BaseDockerHeaderTest<BuildImageCmd> {

    @Mock
    private BuildImageCmd mockObject;

    @Mock
    private InputStream inputStream;

    @Mock
    private BuildImageResultCallback callback;
    
    @Mock
    private File file;

    private String repository = "docker/empty";
    private boolean quiet = true;
    private boolean noCache = true;
    private boolean remove = true;
    private String tag = "1.0";

    @Test
    public void buildImageFromInputStreamHeaderTest() {

        template.sendBodyAndHeaders("direct:in", inputStream, getHeaders());

        Mockito.verify(dockerClient, Mockito.times(1)).buildImageCmd(any(InputStream.class));
        Mockito.verify(mockObject, Mockito.times(1)).withQuiet(quiet);
        Mockito.verify(mockObject, Mockito.times(1)).withNoCache(noCache);
        Mockito.verify(mockObject, Mockito.times(1)).withRemove(remove);
        Mockito.verify(mockObject, Mockito.times(1)).withTag(tag);

    }

    @Test
    public void buildImageFromFileHeaderTest() {

        template.sendBodyAndHeaders("direct:in", file, getHeaders());

        Mockito.verify(dockerClient, Mockito.times(1)).buildImageCmd(any(File.class));
        Mockito.verify(mockObject, Mockito.times(1)).withQuiet(quiet);
        Mockito.verify(mockObject, Mockito.times(1)).withNoCache(noCache);
        Mockito.verify(mockObject, Mockito.times(1)).withRemove(remove);
        Mockito.verify(mockObject, Mockito.times(1)).withTag(tag);

    }

    @Override
    protected void setupMocks() {
        Mockito.when(dockerClient.buildImageCmd(any(InputStream.class))).thenReturn(mockObject);
        Mockito.when(dockerClient.buildImageCmd(any(File.class))).thenReturn(mockObject);

        Mockito.when(mockObject.exec(any())).thenReturn(callback);
        Mockito.when(callback.awaitImageId()).thenReturn(anyString());
    }

    @Override
    protected DockerOperation getOperation() {
        return DockerOperation.BUILD_IMAGE;
    }

    private Map<String, Object> getHeaders() {
        Map<String, Object> headers = getDefaultParameters();
        headers.put(DockerConstants.DOCKER_REPOSITORY, repository);
        headers.put(DockerConstants.DOCKER_QUIET, quiet);
        headers.put(DockerConstants.DOCKER_NO_CACHE, noCache);
        headers.put(DockerConstants.DOCKER_TAG, tag);
        headers.put(DockerConstants.DOCKER_REMOVE, remove);

        return headers;
    }

}
