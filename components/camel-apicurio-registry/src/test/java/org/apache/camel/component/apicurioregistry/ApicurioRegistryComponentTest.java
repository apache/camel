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
package org.apache.camel.component.apicurioregistry;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApicurioRegistryComponentTest extends CamelTestSupport {

    @Test
    void testEndpointCreatedWithGroupAndArtifact() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:myGroup/myArtifact?registryUrl=http://localhost:8080/apis/registry/v3");
        assertNotNull(endpoint);
        assertEquals("myGroup", endpoint.getGroupId());
        assertEquals("myArtifact", endpoint.getArtifactId());
        assertEquals("http://localhost:8080/apis/registry/v3", endpoint.getConfiguration().getRegistryUrl());
    }

    @Test
    void testEndpointCreatedWithGroupOnly() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:myGroup?registryUrl=http://localhost:8080/apis/registry/v3");
        assertNotNull(endpoint);
        assertEquals("myGroup", endpoint.getGroupId());
        assertNull(endpoint.getArtifactId());
    }

    @Test
    void testEndpointWithAuthOptions() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3"
                                                                                           + "&authType=basic&username=user&password=pass");
        assertNotNull(endpoint);
        assertEquals("basic", endpoint.getConfiguration().getAuthType());
        assertEquals("user", endpoint.getConfiguration().getUsername());
        assertEquals("pass", endpoint.getConfiguration().getPassword());
    }

    @Test
    void testEndpointWithOperationOption() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3"
                                                                                           + "&operation=createArtifact");
        assertNotNull(endpoint);
        assertEquals("createArtifact", endpoint.getConfiguration().getOperation());
    }

    @Test
    void testConfigurationCopy() {
        ApicurioRegistryConfiguration config = new ApicurioRegistryConfiguration();
        config.setRegistryUrl("http://localhost:8080");
        config.setOperation("createArtifact");
        config.setAuthType("basic");
        config.setUsername("user");

        ApicurioRegistryConfiguration copy = config.copy();
        assertEquals(config.getRegistryUrl(), copy.getRegistryUrl());
        assertEquals(config.getOperation(), copy.getOperation());
        assertEquals(config.getAuthType(), copy.getAuthType());
        assertEquals(config.getUsername(), copy.getUsername());
    }

    @Test
    void testEndpointServiceLocation() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3");
        assertEquals("http://localhost:8080/apis/registry/v3", endpoint.getServiceUrl());
        assertEquals("http", endpoint.getServiceProtocol());
    }
}
