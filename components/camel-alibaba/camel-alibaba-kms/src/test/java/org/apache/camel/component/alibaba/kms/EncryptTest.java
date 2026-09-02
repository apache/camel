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

import java.util.Base64;
import java.util.Map;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.EncryptResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.kms.constants.KMSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("kmsClient")
    Client kmsClient = mock(Client.class);

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
                            + "&kmsClient=#kmsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testEncrypt() throws Exception {
        EncryptResponseBody body = new EncryptResponseBody();
        body.setCiphertextBlob("encrypted-data");
        body.setKeyId(testConfiguration.getProperty("keyId"));
        body.setRequestId("req-789");

        EncryptResponse response = new EncryptResponse();
        response.setStatusCode(200);
        response.setBody(body);

        when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:encrypt", "secret-value");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = exchange.getMessage().getBody(Map.class);
        assertThat(responseBody)
                .containsEntry("ciphertextBlob", "encrypted-data")
                .containsEntry("keyId", testConfiguration.getProperty("keyId"));
        assertThat(exchange.getMessage().getHeader(KMSHeaders.REQUEST_ID)).isEqualTo("req-789");

        verify(kmsClient).encrypt(argThat((EncryptRequest request) -> Base64.getEncoder()
                .encodeToString("secret-value".getBytes()).equals(request.getPlaintext())));
    }
}
