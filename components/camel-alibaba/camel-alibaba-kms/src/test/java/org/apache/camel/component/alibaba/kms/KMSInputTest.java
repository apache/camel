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
package org.apache.camel.component.alibaba.kms;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.EncryptResponseBody;
import com.aliyun.kms20160120.models.GenerateDataKeyRequest;
import com.aliyun.kms20160120.models.GenerateDataKeyResponse;
import com.aliyun.kms20160120.models.GenerateDataKeyResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.kms.constants.KMSHeaders;
import org.apache.camel.component.alibaba.kms.constants.KMSOperations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operation, the plaintext and the number of bytes that the producer reads from a header or an exchange property:
 * the plaintext must be text, as it must be in the message body, and only generateDataKey reads the number of bytes.
 */
class KMSInputTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("kmsClient")
    Client kmsClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:kms")
                        .to("alibaba-kms:encrypt"
                            + "?keyId=" + testConfiguration.getProperty("keyId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&kmsClient=#kmsClient");
            }
        };
    }

    @Test
    void theOperationHeaderReplacesTheOperationOfTheEndpoint() throws Exception {
        stubGenerateDataKey();

        template.sendBodyAndHeader("direct:kms", null, KMSHeaders.OPERATION, KMSOperations.GENERATE_DATA_KEY);

        verify(kmsClient).generateDataKey(any(GenerateDataKeyRequest.class));
        verify(kmsClient, never()).encrypt(any(EncryptRequest.class));
    }

    @Test
    void thePlaintextHeaderIsEncryptedInUtf8() throws Exception {
        String text = "Zürich €";
        stubEncrypt();

        template.sendBodyAndHeader("direct:kms", null, KMSHeaders.PLAINTEXT, text);

        assertThat(encryptedPlaintext()).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aBytesPlaintextHeaderIsReadAsItsText() throws Exception {
        stubEncrypt();

        template.sendBodyAndHeader("direct:kms", null, KMSHeaders.PLAINTEXT,
                "secret".getBytes(StandardCharsets.UTF_8));

        assertThat(encryptedPlaintext()).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
    }

    /** A value whose text is its toString(), which the type converter has nothing for. */
    @Test
    void aUuidAndAUriPlaintextHeaderAreEncryptedAsTheirText() throws Exception {
        stubEncrypt();
        UUID uuid = UUID.randomUUID();

        template.sendBodyAndHeader("direct:kms", null, KMSHeaders.PLAINTEXT, uuid);

        assertThat(encryptedPlaintext()).isEqualTo(uuid.toString().getBytes(StandardCharsets.UTF_8));

        template.sendBodyAndHeader("direct:kms", null, KMSHeaders.PLAINTEXT, URI.create("https://camel.apache.org"));

        ArgumentCaptor<EncryptRequest> request = ArgumentCaptor.forClass(EncryptRequest.class);
        verify(kmsClient, times(2)).encrypt(request.capture());
        assertThat(Base64.getDecoder().decode(request.getValue().getPlaintext()))
                .isEqualTo("https://camel.apache.org".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aMapInThePlaintextHeaderFailsInsteadOfBeingEncryptedAsItsToString() throws Exception {
        stubEncrypt();

        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:kms", null, KMSHeaders.PLAINTEXT,
                Map.of("secret", "value")))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KMSHeaders.PLAINTEXT + " header")
                .hasMessageContaining("Map")
                // the plaintext is a secret, so the message says the class and not the value
                .hasMessageNotContaining("value");

        verify(kmsClient, never()).encrypt(any(EncryptRequest.class));
    }

    @Test
    void aPojoInThePlaintextExchangePropertyFails() throws Exception {
        stubEncrypt();

        Exchange result = template.send("direct:kms",
                exchange -> exchange.setProperty(KMSHeaders.PLAINTEXT, new Secret("value")));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KMSHeaders.PLAINTEXT + " exchange property")
                .hasMessageContaining(Secret.class.getName());

        verify(kmsClient, never()).encrypt(any(EncryptRequest.class));
    }

    @Test
    void anInvalidNumberOfBytesHeaderDoesNotFailEncrypt() throws Exception {
        stubEncrypt();

        Exchange result = template.send("direct:kms", exchange -> {
            exchange.getIn().setHeader(KMSHeaders.NUMBER_OF_BYTES, "abc");
            exchange.getIn().setHeader(KMSHeaders.PLAINTEXT, "secret");
        });

        assertThat(result.getException()).isNull();
        assertThat(encryptedPlaintext()).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anInvalidNumberOfBytesHeaderFailsGenerateDataKey() throws Exception {
        stubGenerateDataKey();

        Exchange result = template.send("direct:kms", exchange -> {
            exchange.getIn().setHeader(KMSHeaders.OPERATION, KMSOperations.GENERATE_DATA_KEY);
            exchange.getIn().setHeader(KMSHeaders.NUMBER_OF_BYTES, "abc");
        });

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KMSHeaders.NUMBER_OF_BYTES + " header")
                .hasMessageContaining("abc");

        verify(kmsClient, never()).generateDataKey(any(GenerateDataKeyRequest.class));
    }

    @Test
    void anEmptyNumberOfBytesHeaderCountsAsUnset() throws Exception {
        stubGenerateDataKey();

        Exchange result = template.send("direct:kms", exchange -> {
            exchange.getIn().setHeader(KMSHeaders.OPERATION, KMSOperations.GENERATE_DATA_KEY);
            exchange.getIn().setHeader(KMSHeaders.NUMBER_OF_BYTES, "");
        });

        assertThat(result.getException()).isNull();
        assertThat(generateDataKeyRequest().getNumberOfBytes()).isNull();
    }

    @Test
    void theNumberOfBytesHeaderIsSentAsANumber() throws Exception {
        stubGenerateDataKey();

        Exchange result = template.send("direct:kms", exchange -> {
            exchange.getIn().setHeader(KMSHeaders.OPERATION, KMSOperations.GENERATE_DATA_KEY);
            exchange.getIn().setHeader(KMSHeaders.NUMBER_OF_BYTES, "32");
        });

        assertThat(result.getException()).isNull();
        assertThat(generateDataKeyRequest().getNumberOfBytes()).isEqualTo(32);
    }

    private byte[] encryptedPlaintext() throws Exception {
        ArgumentCaptor<EncryptRequest> request = ArgumentCaptor.forClass(EncryptRequest.class);
        verify(kmsClient).encrypt(request.capture());
        return Base64.getDecoder().decode(request.getValue().getPlaintext());
    }

    private GenerateDataKeyRequest generateDataKeyRequest() throws Exception {
        ArgumentCaptor<GenerateDataKeyRequest> request = ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kmsClient).generateDataKey(request.capture());
        return request.getValue();
    }

    private void stubEncrypt() throws Exception {
        EncryptResponseBody responseBody = new EncryptResponseBody();
        responseBody.setCiphertextBlob("encrypted-data");
        EncryptResponse response = new EncryptResponse();
        response.setStatusCode(200);
        response.setBody(responseBody);
        when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(response);
    }

    private void stubGenerateDataKey() throws Exception {
        GenerateDataKeyResponseBody responseBody = new GenerateDataKeyResponseBody();
        responseBody.setCiphertextBlob("encrypted-data-key");
        responseBody.setPlaintext("data-key");
        GenerateDataKeyResponse response = new GenerateDataKeyResponse();
        response.setStatusCode(200);
        response.setBody(responseBody);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(response);
    }

    private record Secret(String value) {
    }
}
