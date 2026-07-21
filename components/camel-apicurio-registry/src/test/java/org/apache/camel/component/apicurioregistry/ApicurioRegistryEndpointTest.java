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

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApicurioRegistryEndpointTest {

    private static final String SERVER_URL = "http://localhost:8080/apis/registry/v3";

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private ApicurioRegistryEndpoint endpoint(String uri) {
        return context.getEndpoint(uri, ApicurioRegistryEndpoint.class);
    }

    @Test
    void defaultsAreApplied() {
        ApicurioRegistryEndpoint endpoint = endpoint("apicurio-registry:" + SERVER_URL);

        assertThat(endpoint.getServerUrl()).isEqualTo(SERVER_URL);
        assertThat(endpoint.getGroupId()).isEqualTo(ApicurioRegistryEndpoint.DEFAULT_GROUP_ID);
        assertThat(endpoint.getContentType()).isEqualTo(ApicurioRegistryEndpoint.DEFAULT_CONTENT_TYPE);
        assertThat(endpoint.getArtifactId()).isNull();
        assertThat(endpoint.getOperation()).isNull();
        assertThat(endpoint.getServiceUrl()).isEqualTo(SERVER_URL);
    }

    @Test
    void queryParametersAreParsed() {
        ApicurioRegistryEndpoint endpoint = endpoint(
                "apicurio-registry:" + SERVER_URL
                                                     + "?groupId=my-group&artifactId=my-artifact&operation=getArtifact"
                                                     + "&artifactType=AVRO&version=2&contentType=application/xml");

        assertThat(endpoint.getGroupId()).isEqualTo("my-group");
        assertThat(endpoint.getArtifactId()).isEqualTo("my-artifact");
        assertThat(endpoint.getOperation()).isEqualTo(ApicurioRegistryOperations.getArtifact);
        assertThat(endpoint.getArtifactType()).isEqualTo("AVRO");
        assertThat(endpoint.getVersion()).isEqualTo("2");
        assertThat(endpoint.getContentType()).isEqualTo("application/xml");
    }

    @Test
    void createProducerIsSupported() throws Exception {
        ApicurioRegistryEndpoint endpoint = endpoint("apicurio-registry:" + SERVER_URL);
        assertThat(endpoint.createProducer()).isInstanceOf(ApicurioRegistryProducer.class);
    }

    @Test
    void createConsumerIsNotSupported() {
        ApicurioRegistryEndpoint endpoint = endpoint("apicurio-registry:" + SERVER_URL);
        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        })).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingServerUrlIsRejected() {
        assertThatThrownBy(() -> endpoint("apicurio-registry:"))
                .isInstanceOf(Exception.class);
    }
}
