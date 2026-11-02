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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.EncryptResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The plaintext encrypt sends for each kind of body: binary data must be encrypted unchanged, so decrypt gives the same
 * bytes back.
 */
class EncryptPlaintextTest extends CamelTestSupport {

    // 0xff, 0xfe and 0x80 are not valid UTF-8, so reading them as text replaces them with EF BF BD
    private static final byte[] BINARY = { 0x00, (byte) 0xff, (byte) 0xfe, 0x41, (byte) 0x80, 0x7f };

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("kmsClient")
    Client kmsClient = mock(Client.class);

    @TempDir
    Path dir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:encrypt")
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
    void aStringIsEncryptedInUtf8() throws Exception {
        String text = "Zürich €";

        assertThat(encryptedPlaintext(text)).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bytesAreEncryptedAsTheyAre() throws Exception {
        assertThat(encryptedPlaintext(BINARY.clone())).isEqualTo(BINARY);
    }

    @Test
    void aBinaryStreamIsEncryptedUnchanged() throws Exception {
        assertThat(encryptedPlaintext(new ByteArrayInputStream(BINARY))).isEqualTo(BINARY);
    }

    @Test
    void aBinaryFileIsEncryptedUnchanged() throws Exception {
        File file = Files.write(dir.resolve("secret.bin"), BINARY).toFile();

        assertThat(encryptedPlaintext(file)).isEqualTo(BINARY);
    }

    @Test
    void theBinaryFileOfTheFileConsumerIsEncryptedUnchanged() throws Exception {
        File file = Files.write(dir.resolve("secret.bin"), BINARY).toFile();
        GenericFile<File> genericFile = FileConsumer.asGenericFile(dir.toString(), file, null, false);

        assertThat(encryptedPlaintext(genericFile)).isEqualTo(BINARY);
    }

    @Test
    void noBodyFails() throws Exception {
        stubEncrypt();

        assertThatThrownBy(() -> template.sendBody("direct:encrypt", null))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessage("Plaintext is required for encrypt");

        verify(kmsClient, never()).encrypt(any(EncryptRequest.class));
    }

    @Test
    void aMapFailsInsteadOfBeingEncryptedAsItsToString() throws Exception {
        assertFailsWithoutEncrypting(Map.of("secret", "value"));
    }

    @Test
    void aPojoFailsInsteadOfBeingEncryptedAsItsToString() throws Exception {
        assertFailsWithoutEncrypting(new Secret("value"));
    }

    private byte[] encryptedPlaintext(Object body) throws Exception {
        stubEncrypt();

        template.sendBody("direct:encrypt", body);

        ArgumentCaptor<EncryptRequest> request = ArgumentCaptor.forClass(EncryptRequest.class);
        verify(kmsClient).encrypt(request.capture());
        assertThat(request.getValue().getKeyId()).isEqualTo(testConfiguration.getProperty("keyId"));
        return Base64.getDecoder().decode(request.getValue().getPlaintext());
    }

    private void assertFailsWithoutEncrypting(Object body) throws Exception {
        stubEncrypt();

        assertThatThrownBy(() -> template.sendBody("direct:encrypt", body))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(InvalidPayloadException.class)
                .cause().hasMessageContaining("byte[]");

        verify(kmsClient, never()).encrypt(any(EncryptRequest.class));
    }

    private void stubEncrypt() throws Exception {
        EncryptResponseBody responseBody = new EncryptResponseBody();
        responseBody.setCiphertextBlob("encrypted-data");
        EncryptResponse response = new EncryptResponse();
        response.setStatusCode(200);
        response.setBody(responseBody);
        when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(response);
    }

    private record Secret(String value) {
    }
}
