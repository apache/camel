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
package org.apache.camel.component.knative.spi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.util.CollectionHelper.mapOf;
import static org.assertj.core.api.Assertions.assertThat;

public class KnativeEnvironmentTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"services\":[{\"type\":\"endpoint\",\"name\":\"knative3\",\"metadata\":{\"camel.endpoint.kind\":\"source\",\"knative.apiVersion\":\"serving.knative.dev/v1\",\"knative.kind\":\"Service\",\"service.path\":\"/\"}}]}",
            "{\"resources\":[{\"type\":\"endpoint\",\"name\":\"knative3\",\"metadata\":{\"camel.endpoint.kind\":\"source\",\"knative.apiVersion\":\"serving.knative.dev/v1\",\"knative.kind\":\"Service\",\"service.path\":\"/\"}}]}"
    })
    public void testKnativeEnvironmentDeserializationFromString(String content) throws Exception {
        KnativeEnvironment env = KnativeEnvironment.mandatoryLoadFromSerializedString(content);
        List<KnativeResource> res = env.lookup(Knative.Type.endpoint, "knative3").toList();

        assertThat(res).hasSize(1);
        assertThat(res).first().satisfies(resource -> {
            assertThat(resource.getName()).isEqualTo("knative3");
            assertThat(resource.getEndpointKind()).isEqualTo(Knative.EndpointKind.source);
            assertThat(resource.getObjectApiVersion()).isEqualTo("serving.knative.dev/v1");
            assertThat(resource.getObjectKind()).isEqualTo("Service");
            assertThat(resource.getPath()).isEqualTo("/");
            assertThat(resource.getMetadata()).isNotEmpty();
        });
    }

    @Test
    public void testKnativeEnvironmentDeserializationFromProperties() {
        Map<String, Object> properties = mapOf(
                "resources[0].name", "knative3",
                "resources[0].type", "endpoint",
                "resources[0].endpointKind", "source",
                "resources[0].objectApiVersion", "serving.knative.dev/v1",
                "resources[0].objectKind", "Service",
                "resources[0].path", "/");

        CamelContext context = new DefaultCamelContext();
        KnativeEnvironment env = KnativeEnvironment.mandatoryLoadFromProperties(context, properties);
        List<KnativeResource> res = env.lookup(Knative.Type.endpoint, "knative3").collect(Collectors.toList());

        assertThat(res).hasSize(1);
        assertThat(res).first().satisfies(resource -> {
            assertThat(resource.getName()).isEqualTo("knative3");
            assertThat(resource.getEndpointKind()).isEqualTo(Knative.EndpointKind.source);
            assertThat(resource.getObjectApiVersion()).isEqualTo("serving.knative.dev/v1");
            assertThat(resource.getObjectKind()).isEqualTo("Service");
            assertThat(resource.getPath()).isEqualTo("/");
            assertThat(resource.getMetadata()).isEmpty();
        });
    }
}
