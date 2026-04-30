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
package org.apache.camel.component.splunkhec;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SplunkHECProducerTest {

    private DefaultCamelContext camelContext;
    private SplunkHECEndpoint endpoint;
    private SplunkHECProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        SplunkHECConfiguration config = new SplunkHECConfiguration();
        config.setToken("11111111-1111-1111-1111-111111111111");
        config.setHttps(false);
        config.setSkipTlsVerify(false);

        SplunkHECComponent component = new SplunkHECComponent();
        component.setCamelContext(camelContext);

        endpoint = new SplunkHECEndpoint("splunk-hec:localhost:8088", component, config);
        endpoint.setSplunkURL("localhost:8088");

        producer = new SplunkHECProducer(endpoint);
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.close();
    }

    @Test
    public void testProcessThrowsRuntimeCamelExceptionOnNon200Response() throws Exception {
        CloseableHttpClient mockClient = createMockClient(400, "Bad Request",
                "{\"text\":\"Invalid data format\",\"code\":6}");
        injectHttpClient(producer, mockClient);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody("test event");

        RuntimeCamelException thrown = assertThrows(RuntimeCamelException.class, () -> producer.process(exchange));
        assertTrue(thrown.getMessage().contains("Splunk HEC request failed"));
        assertTrue(thrown.getMessage().contains("Invalid data format"));
    }

    @Test
    public void testProcessThrowsRuntimeCamelExceptionOnServerError() throws Exception {
        CloseableHttpClient mockClient = createMockClient(503, "Service Unavailable",
                "{\"text\":\"Server is busy\",\"code\":9}");
        injectHttpClient(producer, mockClient);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody("test event");

        RuntimeCamelException thrown = assertThrows(RuntimeCamelException.class, () -> producer.process(exchange));
        assertTrue(thrown.getMessage().contains("Splunk HEC request failed"));
        assertTrue(thrown.getMessage().contains("Server is busy"));
    }

    @SuppressWarnings("unchecked")
    private CloseableHttpClient createMockClient(int statusCode, String reasonPhrase, String responseBody)
            throws Exception {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<Object> handler = invocation.getArgument(1);
                    ClassicHttpResponse mockResponse = mock(ClassicHttpResponse.class);
                    when(mockResponse.getCode()).thenReturn(statusCode);
                    when(mockResponse.getReasonPhrase()).thenReturn(reasonPhrase);
                    when(mockResponse.getVersion()).thenReturn(org.apache.hc.core5.http.HttpVersion.HTTP_1_1);
                    HttpEntity entity = mock(HttpEntity.class);
                    doAnswer(inv -> {
                        OutputStream os = inv.getArgument(0);
                        os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                        return null;
                    }).when(entity).writeTo(any(OutputStream.class));
                    when(mockResponse.getEntity()).thenReturn(entity);
                    return handler.handleResponse(mockResponse);
                });
        return mockClient;
    }

    private void injectHttpClient(SplunkHECProducer producer, CloseableHttpClient client) throws Exception {
        Field httpClientField = SplunkHECProducer.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(producer, client);
    }
}
