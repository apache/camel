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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DockerHelperTest {

    @Test
    void transformHeaderTestFromHeader() {
        String headerField = DockerHelper.transformFromHeaderName(DockerConstants.DOCKER_REGISTRY);
        assertEquals("registry", headerField);
    }

    @Test
    void registryHostOfImageReference() {
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("ubuntu"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("ubuntu:24.04"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("library/ubuntu"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("team/app:1.0"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("docker.io/library/ubuntu"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("index.docker.io/team/app"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("registry-1.docker.io/team/app"));
        assertEquals("docker.io", DockerHelper.getImageRegistryHost("ubuntu@sha256:0123456789abcdef"));
        assertEquals("localhost", DockerHelper.getImageRegistryHost("localhost/app"));
        assertEquals("localhost:5000", DockerHelper.getImageRegistryHost("localhost:5000/team/app:1.0"));
        assertEquals("registry.example.com", DockerHelper.getImageRegistryHost("registry.example.com/team/app"));
        assertEquals("registry.example.com:5000",
                DockerHelper.getImageRegistryHost("Registry.Example.com:5000/team/app@sha256:0123456789abcdef"));
        assertEquals("myregistry", DockerHelper.getImageRegistryHost("MyRegistry/app"));
        assertNull(DockerHelper.getImageRegistryHost(null));
        assertNull(DockerHelper.getImageRegistryHost(""));
    }

    @Test
    void registryHostOfServerAddress() {
        assertEquals("docker.io", DockerHelper.getServerAddressRegistryHost("https://index.docker.io/v1/"));
        assertEquals("docker.io", DockerHelper.getServerAddressRegistryHost("http://docker.io/v1"));
        assertEquals("docker.io", DockerHelper.getServerAddressRegistryHost("docker.io"));
        assertEquals("docker.io", DockerHelper.getServerAddressRegistryHost("registry-1.docker.io"));
        assertEquals("registry.example.com:5000",
                DockerHelper.getServerAddressRegistryHost("https://Registry.Example.com:5000/v2/"));
        assertEquals("localhost:5000", DockerHelper.getServerAddressRegistryHost("localhost:5000"));
        assertNull(DockerHelper.getServerAddressRegistryHost(null));
        assertNull(DockerHelper.getServerAddressRegistryHost(" "));
    }

}
