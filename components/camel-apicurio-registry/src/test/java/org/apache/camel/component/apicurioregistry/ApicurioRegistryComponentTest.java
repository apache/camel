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

import static org.assertj.core.api.Assertions.assertThat;

class ApicurioRegistryComponentTest extends CamelTestSupport {

    @Test
    void testEndpointCreatedWithGroupAndArtifact() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:myGroup/myArtifact?registryUrl=http://localhost:8080/apis/registry/v3");
        assertThat(endpoint.getGroupId()).isEqualTo("myGroup");
        assertThat(endpoint.getArtifactId()).isEqualTo("myArtifact");
        assertThat(endpoint.getConfiguration().getRegistryUrl()).isEqualTo("http://localhost:8080/apis/registry/v3");
    }

    @Test
    void testEndpointCreatedWithGroupOnly() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:myGroup?registryUrl=http://localhost:8080/apis/registry/v3");
        assertThat(endpoint.getGroupId()).isEqualTo("myGroup");
        assertThat(endpoint.getArtifactId()).isNull();
    }

    @Test
    void testEndpointWithAuthOptions() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3"
                                                                                           + "&authType=basic&username=user&password=pass");
        assertThat(endpoint.getConfiguration().getAuthType()).isEqualTo("basic");
        assertThat(endpoint.getConfiguration().getUsername()).isEqualTo("user");
        assertThat(endpoint.getConfiguration().getPassword()).isEqualTo("pass");
    }

    @Test
    void testEndpointWithOperationOption() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3"
                                                                                           + "&operation=createArtifact");
        assertThat(endpoint.getConfiguration().getOperation()).isEqualTo("createArtifact");
    }

    @Test
    void testConfigurationCopy() {
        ApicurioRegistryConfiguration config = new ApicurioRegistryConfiguration();
        config.setRegistryUrl("http://localhost:8080");
        config.setOperation("createArtifact");
        config.setAuthType("basic");
        config.setUsername("user");

        ApicurioRegistryConfiguration copy = config.copy();
        assertThat(copy).isNotSameAs(config);
        assertThat(copy.getRegistryUrl()).isEqualTo(config.getRegistryUrl());
        assertThat(copy.getOperation()).isEqualTo(config.getOperation());
        assertThat(copy.getAuthType()).isEqualTo(config.getAuthType());
        assertThat(copy.getUsername()).isEqualTo(config.getUsername());
    }

    @Test
    void testEndpointServiceLocation() throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3");
        assertThat(endpoint.getServiceUrl()).isEqualTo("http://localhost:8080/apis/registry/v3");
        assertThat(endpoint.getServiceProtocol()).isEqualTo("http");
    }

    @Test
    void testEndpointWithOidcOptions() {
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3"
                                                                + "&authType=oidc&tokenEndpoint=http://localhost:8080/token"
                                                                + "&clientId=registry-client&clientSecret=secret&scope=registry",
                ApicurioRegistryEndpoint.class);
        ApicurioRegistryConfiguration configuration = endpoint.getConfiguration();
        assertThat(configuration.getAuthType()).isEqualTo("oidc");
        assertThat(configuration.getTokenEndpoint()).isEqualTo("http://localhost:8080/token");
        assertThat(configuration.getClientId()).isEqualTo("registry-client");
        assertThat(configuration.getClientSecret()).isEqualTo("secret");
        assertThat(configuration.getScope()).isEqualTo("registry");
    }
}
