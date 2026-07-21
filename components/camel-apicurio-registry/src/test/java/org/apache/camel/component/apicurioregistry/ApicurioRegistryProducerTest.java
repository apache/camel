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

import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApicurioRegistryProducerTest {

    private static final String SERVER_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"foo\"}";

    @Mock
    private ApicurioRegistryClient client;

    private DefaultCamelContext context;
    private ApicurioRegistryEndpoint endpoint;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        ApicurioRegistryComponent component = new ApicurioRegistryComponent(context);
        endpoint = new ApicurioRegistryEndpoint("apicurio-registry:" + SERVER_URL, SERVER_URL, component);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Exchange process(ApicurioRegistryProducer producer, Object body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        producer.process(exchange);
        return exchange;
    }

    private ApicurioRegistryProducer producer() {
        return new ApicurioRegistryProducer(endpoint, client);
    }

    @Test
    void createArtifactUsesEndpointConfiguration() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.createArtifact);
        endpoint.setArtifactId("my-artifact");
        endpoint.setArtifactType("AVRO");

        Object response = new Object();
        when(client.createArtifact("default", "my-artifact", "AVRO", SCHEMA, "application/json"))
                .thenReturn(response);

        Exchange exchange = process(producer(), SCHEMA);

        assertThat(exchange.getIn().getBody()).isSameAs(response);
        verify(client).createArtifact("default", "my-artifact", "AVRO", SCHEMA, "application/json");
    }

    @Test
    void updateArtifactAddsNewVersion() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.updateArtifact);
        endpoint.setArtifactId("my-artifact");

        Object response = new Object();
        when(client.addArtifactVersion("default", "my-artifact", SCHEMA, "application/json"))
                .thenReturn(response);

        Exchange exchange = process(producer(), SCHEMA);

        assertThat(exchange.getIn().getBody()).isSameAs(response);
        verify(client).addArtifactVersion("default", "my-artifact", SCHEMA, "application/json");
    }

    @Test
    void getArtifactReturnsLatestContent() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.getArtifact);
        endpoint.setArtifactId("my-artifact");

        when(client.getLatestArtifactContent("default", "my-artifact")).thenReturn(SCHEMA);

        Exchange exchange = process(producer(), null);

        assertThat(exchange.getIn().getBody(String.class)).isEqualTo(SCHEMA);
        verify(client).getLatestArtifactContent("default", "my-artifact");
    }

    @Test
    void getArtifactVersionReturnsVersionContent() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.getArtifactVersion);
        endpoint.setArtifactId("my-artifact");
        endpoint.setVersion("2");

        when(client.getArtifactVersionContent("default", "my-artifact", "2")).thenReturn(SCHEMA);

        Exchange exchange = process(producer(), null);

        assertThat(exchange.getIn().getBody(String.class)).isEqualTo(SCHEMA);
        verify(client).getArtifactVersionContent("default", "my-artifact", "2");
    }

    @Test
    void listArtifactVersionsSetsResultsAsBody() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.listArtifactVersions);
        endpoint.setArtifactId("my-artifact");

        VersionSearchResults results = new VersionSearchResults();
        when(client.listArtifactVersions("default", "my-artifact")).thenReturn(results);

        Exchange exchange = process(producer(), null);

        assertThat(exchange.getIn().getBody()).isSameAs(results);
        verify(client).listArtifactVersions("default", "my-artifact");
    }

    @Test
    void deleteArtifactInvokesClient() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.deleteArtifact);
        endpoint.setArtifactId("my-artifact");

        process(producer(), null);

        verify(client).deleteArtifact("default", "my-artifact");
    }

    @Test
    void deleteArtifactVersionInvokesClient() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.deleteArtifactVersion);
        endpoint.setArtifactId("my-artifact");
        endpoint.setVersion("3");

        process(producer(), null);

        verify(client).deleteArtifactVersion("default", "my-artifact", "3");
    }

    @Test
    void headersOverrideEndpointConfiguration() throws Exception {
        endpoint.setOperation(ApicurioRegistryOperations.createArtifact);
        endpoint.setArtifactId("endpoint-artifact");
        endpoint.setArtifactType("JSON");

        Object response = new Object();
        when(client.createArtifact("other-group", "header-artifact", "PROTOBUF", SCHEMA, "application/xml"))
                .thenReturn(response);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(SCHEMA);
        exchange.getIn().setHeader(ApicurioRegistryConstants.OPERATION, ApicurioRegistryOperations.createArtifact);
        exchange.getIn().setHeader(ApicurioRegistryConstants.GROUP_ID, "other-group");
        exchange.getIn().setHeader(ApicurioRegistryConstants.ARTIFACT_ID, "header-artifact");
        exchange.getIn().setHeader(ApicurioRegistryConstants.ARTIFACT_TYPE, "PROTOBUF");
        exchange.getIn().setHeader(ApicurioRegistryConstants.CONTENT_TYPE, "application/xml");
        producer().process(exchange);

        assertThat(exchange.getIn().getBody()).isSameAs(response);
        verify(client).createArtifact("other-group", "header-artifact", "PROTOBUF", SCHEMA, "application/xml");
    }

    @Test
    void operationCanBeProvidedByHeaderOnly() throws Exception {
        endpoint.setArtifactId("my-artifact");

        when(client.getLatestArtifactContent("default", "my-artifact")).thenReturn(SCHEMA);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(ApicurioRegistryConstants.OPERATION, ApicurioRegistryOperations.getArtifact);
        producer().process(exchange);

        assertThat(exchange.getIn().getBody(String.class)).isEqualTo(SCHEMA);
    }

    @Test
    void missingOperationThrows() {
        endpoint.setArtifactId("my-artifact");

        ApicurioRegistryProducer producer = producer();
        assertThatThrownBy(() -> process(producer, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No operation specified");
        verifyNoInteractions(client);
    }

    @Test
    void missingArtifactIdThrows() {
        endpoint.setOperation(ApicurioRegistryOperations.getArtifact);

        ApicurioRegistryProducer producer = producer();
        assertThatThrownBy(() -> process(producer, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No artifactId specified");
        verifyNoInteractions(client);
    }

    @Test
    void missingVersionThrows() {
        endpoint.setOperation(ApicurioRegistryOperations.getArtifactVersion);
        endpoint.setArtifactId("my-artifact");

        ApicurioRegistryProducer producer = producer();
        assertThatThrownBy(() -> process(producer, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No version specified");
        verifyNoInteractions(client);
    }

    @Test
    void createArtifactRequiresBody() {
        endpoint.setOperation(ApicurioRegistryOperations.createArtifact);
        endpoint.setArtifactId("my-artifact");

        ApicurioRegistryProducer producer = producer();
        assertThatThrownBy(() -> process(producer, null))
                .isInstanceOf(InvalidPayloadException.class);
    }
}
