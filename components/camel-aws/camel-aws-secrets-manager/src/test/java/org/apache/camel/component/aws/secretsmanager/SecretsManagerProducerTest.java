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
package org.apache.camel.component.aws.secretsmanager;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretsManagerProducerTest extends CamelTestSupport {

    // bytes that are not valid UTF-8 text
    private static final byte[] BINARY = new byte[] { 0x00, (byte) 0xff, (byte) 0xfe, 0x41 };

    @BindToRegistry("smClient")
    private final SecretsManagerClient client = mock(SecretsManagerClient.class);

    @Test
    public void createSecretWithBinaryPayloadStoresTheBytesOfTheBody() {
        when(client.createSecret(any(CreateSecretRequest.class))).thenReturn(CreateSecretResponse.builder().build());

        template.send("direct:binary", e -> {
            e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.createSecret);
            e.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "test");
            e.getIn().setBody(BINARY);
        });

        ArgumentCaptor<CreateSecretRequest> captor = ArgumentCaptor.forClass(CreateSecretRequest.class);
        verify(client).createSecret(captor.capture());
        assertArrayEquals(BINARY, captor.getValue().secretBinary().asByteArray());
        assertNull(captor.getValue().secretString());
    }

    @Test
    public void createSecretWithBinaryPayloadStoresTheBytesOfATextBody() {
        when(client.createSecret(any(CreateSecretRequest.class))).thenReturn(CreateSecretResponse.builder().build());

        template.send("direct:binary", e -> {
            e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.createSecret);
            e.getIn().setHeader(SecretsManagerConstants.SECRET_NAME, "test");
            e.getIn().setBody("test-value-4");
        });

        ArgumentCaptor<CreateSecretRequest> captor = ArgumentCaptor.forClass(CreateSecretRequest.class);
        verify(client).createSecret(captor.capture());
        assertEquals("test-value-4", captor.getValue().secretBinary().asUtf8String());
    }

    @Test
    public void updateSecretWithBinaryPayloadStoresTheBytesOfTheBody() {
        when(client.updateSecret(any(UpdateSecretRequest.class))).thenReturn(UpdateSecretResponse.builder().build());

        template.send("direct:binary", e -> {
            e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.updateSecret);
            e.getIn().setHeader(SecretsManagerConstants.SECRET_ID, "test");
            e.getIn().setBody(BINARY);
        });

        ArgumentCaptor<UpdateSecretRequest> captor = ArgumentCaptor.forClass(UpdateSecretRequest.class);
        verify(client).updateSecret(captor.capture());
        assertArrayEquals(BINARY, captor.getValue().secretBinary().asByteArray());
    }

    @Test
    public void putSecretValueWithBinaryPayloadStoresTheBytesOfTheBody() {
        when(client.putSecretValue(any(PutSecretValueRequest.class))).thenReturn(PutSecretValueResponse.builder().build());

        template.send("direct:binary", e -> {
            e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.putSecretValue);
            e.getIn().setHeader(SecretsManagerConstants.SECRET_ID, "test");
            e.getIn().setBody(BINARY);
        });

        ArgumentCaptor<PutSecretValueRequest> captor = ArgumentCaptor.forClass(PutSecretValueRequest.class);
        verify(client).putSecretValue(captor.capture());
        assertArrayEquals(BINARY, captor.getValue().secretBinary().asByteArray());
    }

    @Test
    public void getSecretOfABinarySecretGivesTheBytes() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromByteArray(BINARY)).versionId("v1").build());

        for (String uri : List.of("direct:text", "direct:binary")) {
            Exchange exchange = template.send(uri, e -> {
                e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.getSecret);
                e.getIn().setHeader(SecretsManagerConstants.SECRET_ID, "test");
            });

            assertNull(exchange.getException());
            assertArrayEquals(BINARY, exchange.getMessage().getBody(byte[].class));
            assertInstanceOf(byte[].class, exchange.getMessage().getBody());
            assertEquals("v1", exchange.getMessage().getHeader(SecretsManagerConstants.SECRET_VERSION_ID));
        }
    }

    @Test
    public void getSecretOfAStringSecretGivesTheString() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder()
                .secretString("value").build());

        for (String uri : List.of("direct:text", "direct:binary")) {
            Exchange exchange = template.send(uri, e -> {
                e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.getSecret);
                e.getIn().setHeader(SecretsManagerConstants.SECRET_ID, "test");
            });

            assertNull(exchange.getException());
            assertEquals("value", exchange.getMessage().getBody());
        }
    }

    @Test
    public void batchGetSecretGivesWhatEachSecretHas() {
        when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(
                BatchGetSecretValueResponse.builder().secretValues(
                        SecretValueEntry.builder().name("a").secretString("value").build(),
                        SecretValueEntry.builder().name("b").secretBinary(SdkBytes.fromByteArray(BINARY)).versionId("v2")
                                .build())
                        .build());

        for (String uri : List.of("direct:text", "direct:binary")) {
            Exchange exchange = template.send(uri, e -> {
                e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.batchGetSecret);
                e.getIn().setHeader(SecretsManagerConstants.SECRET_IDS, "a,b");
            });

            assertNull(exchange.getException());
            List<?> body = exchange.getMessage().getBody(List.class);
            assertEquals(2, body.size());
            assertEquals("value", body.get(0));
            assertArrayEquals(BINARY, (byte[]) body.get(1));
            List<?> versionIds = exchange.getMessage().getHeader(SecretsManagerConstants.SECRET_VERSION_IDS, List.class);
            assertEquals(Arrays.asList(null, "v2"), versionIds);
        }
    }

    @Test
    public void batchGetSecretFailsForTheErrorsOfTheResponse() {
        when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(
                BatchGetSecretValueResponse.builder()
                        .secretValues(SecretValueEntry.builder().name("a").secretString("value").build())
                        .errors(APIErrorType.builder().secretId("b").errorCode("ResourceNotFoundException")
                                .message("Secrets Manager can't find the specified secret.").build())
                        .build());

        Exchange exchange = template.send("direct:text", e -> {
            e.getIn().setHeader(SecretsManagerConstants.OPERATION, SecretsManagerOperations.batchGetSecret);
            e.getIn().setHeader(SecretsManagerConstants.SECRET_IDS, "a,b");
        });

        assertNotNull(exchange.getException());
        String msg = exchange.getException().getMessage();
        assertTrue(msg.contains("b"), msg);
        assertTrue(msg.contains("ResourceNotFoundException"), msg);
        assertTrue(msg.contains("Secrets Manager can't find the specified secret."), msg);
    }

    @Test
    public void noOperationSaysTheOperationIsMissing() {
        Exchange exchange = template.send("direct:text", e -> {
            e.getIn().setHeader(SecretsManagerConstants.SECRET_ID, "test");
        });

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().startsWith("Operation must be specified"),
                exchange.getException().getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:text").to("aws-secrets-manager://test?secretsManagerClient=#smClient");
                from("direct:binary").to("aws-secrets-manager://test?secretsManagerClient=#smClient&binaryPayload=true");
            }
        };
    }
}
