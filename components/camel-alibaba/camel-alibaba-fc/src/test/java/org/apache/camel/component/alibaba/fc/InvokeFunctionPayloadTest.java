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
package org.apache.camel.component.alibaba.fc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.aliyun.fc_open20210406.Client;
import com.aliyun.fc_open20210406.models.InvokeFunctionRequest;
import com.aliyun.fc_open20210406.models.InvokeFunctionResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The payload invokeFunction sends for each kind of body: binary data must reach the function unchanged.
 */
class InvokeFunctionPayloadTest extends CamelTestSupport {

    // 0xff, 0xfe and 0x80 are not valid UTF-8, so reading them as text replaces them with EF BF BD
    private static final byte[] BINARY = { 0x00, (byte) 0xff, (byte) 0xfe, 0x41, (byte) 0x80, 0x7f };

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("fcClient")
    Client fcClient = mock(Client.class);

    @TempDir
    Path dir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invoke")
                        .to("alibaba-fc:invokeFunction"
                            + "?serviceName=" + testConfiguration.getProperty("serviceName")
                            + "&functionName=" + testConfiguration.getProperty("functionName")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&fcClient=#fcClient");
            }
        };
    }

    @Test
    void aStringIsSentInUtf8() throws Exception {
        String json = "{\"city\":\"Zürich €\"}";

        assertThat(invoke(json)).isEqualTo(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bytesAreSentAsTheyAre() throws Exception {
        assertThat(invoke(BINARY.clone())).isEqualTo(BINARY);
    }

    @Test
    void aBinaryStreamIsSentUnchanged() throws Exception {
        assertThat(invoke(new ByteArrayInputStream(BINARY))).isEqualTo(BINARY);
    }

    @Test
    void aBinaryFileIsSentUnchanged() throws Exception {
        File file = Files.write(dir.resolve("payload.bin"), BINARY).toFile();

        assertThat(invoke(file)).isEqualTo(BINARY);
    }

    @Test
    void theBinaryFileOfTheFileConsumerIsSentUnchanged() throws Exception {
        File file = Files.write(dir.resolve("payload.bin"), BINARY).toFile();
        GenericFile<File> genericFile = FileConsumer.asGenericFile(dir.toString(), file, null, false);

        assertThat(invoke(genericFile)).isEqualTo(BINARY);
    }

    @Test
    void noBodyInvokesTheFunctionWithAnEmptyPayload() throws Exception {
        assertThat(invoke(null)).isEmpty();
    }

    @Test
    void aMapFailsInsteadOfBeingSentAsItsToString() throws Exception {
        assertFailsWithoutInvoking(Map.of("name", "camel"));
    }

    @Test
    void aPojoFailsInsteadOfBeingSentAsItsToString() throws Exception {
        assertFailsWithoutInvoking(new Order("1001"));
    }

    private byte[] invoke(Object body) throws Exception {
        stubInvokeFunction();

        template.sendBody("direct:invoke", body);

        ArgumentCaptor<InvokeFunctionRequest> request = ArgumentCaptor.forClass(InvokeFunctionRequest.class);
        verify(fcClient).invokeFunction(
                eq(testConfiguration.getProperty("serviceName")),
                eq(testConfiguration.getProperty("functionName")),
                request.capture());
        return request.getValue().getBody();
    }

    private void assertFailsWithoutInvoking(Object body) throws Exception {
        stubInvokeFunction();

        assertThatThrownBy(() -> template.sendBody("direct:invoke", body))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(InvalidPayloadException.class)
                .cause().hasMessageContaining("byte[]");

        verify(fcClient, never()).invokeFunction(anyString(), anyString(), any(InvokeFunctionRequest.class));
    }

    private void stubInvokeFunction() throws Exception {
        InvokeFunctionResponse response = new InvokeFunctionResponse();
        response.setStatusCode(200);
        when(fcClient.invokeFunction(anyString(), anyString(), any(InvokeFunctionRequest.class))).thenReturn(response);
    }

    private record Order(String id) {
    }
}
