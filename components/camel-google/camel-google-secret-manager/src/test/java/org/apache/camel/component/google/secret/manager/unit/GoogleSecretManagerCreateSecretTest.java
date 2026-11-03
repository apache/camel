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
package org.apache.camel.component.google.secret.manager.unit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.google.cloud.secretmanager.v1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1.CreateSecretRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the calls createSecret makes on a mocked {@link SecretManagerServiceClient}.
 */
class GoogleSecretManagerCreateSecretTest extends CamelTestSupport {

    private static final String CREATE_SECRET = "google-secret-manager://myProject?client=#smClient&operation=createSecret";
    private static final Secret SECRET = Secret.newBuilder().setName("projects/myProject/secrets/test").build();
    private static final SecretVersion VERSION
            = SecretVersion.newBuilder().setName("projects/myProject/secrets/test/versions/1").build();

    private final SecretManagerServiceClient client = mock(SecretManagerServiceClient.class);

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("smClient", client);
    }

    @BeforeEach
    void stubClient() {
        when(client.createSecret(any(ProjectName.class), anyString(), any(Secret.class))).thenReturn(SECRET);
        when(client.createSecret(any(CreateSecretRequest.class))).thenReturn(SECRET);
        when(client.addSecretVersion(anyString(), any(SecretPayload.class))).thenReturn(VERSION);
        when(client.addSecretVersion(any(AddSecretVersionRequest.class))).thenReturn(VERSION);
    }

    @Test
    void createSecretWithoutABodyFailsBeforeTheSecretIsCreated() {
        Exchange exchange = template.request(CREATE_SECRET, e -> {
            e.getIn().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test");
            e.getIn().setBody(null);
        });

        // no secret is left behind without a version
        verify(client, never()).createSecret(any(ProjectName.class), any(), any(Secret.class));
        verify(client, never()).addSecretVersion((String) any(), any(SecretPayload.class));
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals(String.class, cause.getType());
    }

    @Test
    void createSecretCreatesTheSecretAndAddsTheValueAsAVersion() {
        Exchange exchange = template.request(CREATE_SECRET, e -> {
            e.getIn().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test");
            e.getIn().setBody("hello");
        });

        assertNull(exchange.getException());
        assertSame(VERSION, exchange.getMessage().getBody());
        ArgumentCaptor<Secret> secret = ArgumentCaptor.forClass(Secret.class);
        verify(client).createSecret(eq(ProjectName.of("myProject")), eq("test"), secret.capture());
        assertEquals(Replication.ReplicationCase.AUTOMATIC, secret.getValue().getReplication().getReplicationCase());
        assertEquals("hello", sentValue());
    }

    @Test
    void createSecretReadsAStreamAsText() {
        Exchange exchange = template.request(CREATE_SECRET, e -> {
            e.getIn().setHeader(GoogleSecretManagerConstants.SECRET_ID, "test");
            e.getIn().setBody(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        });

        assertNull(exchange.getException());
        assertEquals("hello", sentValue());
    }

    @Test
    void createSecretWithPojoRequestCreatesTheSecretOfACreateSecretRequest() {
        CreateSecretRequest request = CreateSecretRequest.newBuilder()
                .setParent(ProjectName.of("myProject").toString())
                .setSecretId("test")
                .setSecret(Secret.newBuilder()
                        .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder())))
                .build();

        Exchange exchange = template.request(CREATE_SECRET + "&pojoRequest=true", e -> e.getIn().setBody(request));

        assertNull(exchange.getException());
        assertSame(SECRET, exchange.getMessage().getBody());
        verify(client).createSecret(request);
        verify(client, never()).addSecretVersion(any(AddSecretVersionRequest.class));
    }

    @Test
    void createSecretWithPojoRequestAddsTheVersionOfAnAddSecretVersionRequest() {
        AddSecretVersionRequest request = AddSecretVersionRequest.newBuilder()
                .setParent(SECRET.getName())
                .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("hello")))
                .build();

        Exchange exchange = template.request(CREATE_SECRET + "&pojoRequest=true", e -> e.getIn().setBody(request));

        assertNull(exchange.getException());
        assertSame(VERSION, exchange.getMessage().getBody());
        verify(client).addSecretVersion(request);
        verify(client, never()).createSecret(any(CreateSecretRequest.class));
    }

    @Test
    void createSecretWithPojoRequestFailsForAnotherBody() {
        Exchange exchange = template.request(CREATE_SECRET + "&pojoRequest=true", e -> e.getIn().setBody("hello"));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(cause.getMessage().contains("takes a CreateSecretRequest"), cause.getMessage());
        verify(client, never()).createSecret(any(CreateSecretRequest.class));
        verify(client, never()).addSecretVersion(any(AddSecretVersionRequest.class));
    }

    /**
     * The value of the version that createSecret added.
     */
    private String sentValue() {
        ArgumentCaptor<SecretPayload> payload = ArgumentCaptor.forClass(SecretPayload.class);
        verify(client).addSecretVersion(eq(SECRET.getName()), payload.capture());
        return payload.getValue().getData().toStringUtf8();
    }
}
