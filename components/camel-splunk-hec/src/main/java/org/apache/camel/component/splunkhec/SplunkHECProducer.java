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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.ssl.SSLContextBuilder;

/**
 * The Splunk HEC producer.
 */
public class SplunkHECProducer extends DefaultProducer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SplunkHECEndpoint endpoint;
    private CloseableHttpClient httpClient;

    public SplunkHECProducer(SplunkHECEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        HttpClientBuilder builder = HttpClients.custom()
                .setUserAgent("Camel Splunk HEC/" + getEndpoint().getCamelContext().getVersion());
        PoolingHttpClientConnectionManager connManager;
        if (endpoint.getConfiguration().isSkipTlsVerify()) {
            SSLContextBuilder sslbuilder = new SSLContextBuilder();
            sslbuilder.loadTrustMaterial(null, (chain, authType) -> true);
            SSLConnectionSocketFactory sslsf
                    = new SSLConnectionSocketFactory(sslbuilder.build(), NoopHostnameVerifier.INSTANCE);
            RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
            registryBuilder.register("https", sslsf);

            connManager = new PoolingHttpClientConnectionManager(registryBuilder.build());
        } else {
            connManager = new PoolingHttpClientConnectionManager();
        }
        connManager.setMaxTotal(10);
        builder.setConnectionManager(connManager);
        httpClient = builder.build();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> payload = createPayload(exchange.getIn());

        HttpPost httppost = new HttpPost(
                (endpoint.getConfiguration().isHttps() ? "https" : "http") + "://"
                                         + endpoint.getSplunkURL() + endpoint.getConfiguration().getSplunkEndpoint());
        httppost.addHeader("Authorization", " Splunk " + endpoint.getToken());

        EntityTemplate entityTemplate = new EntityTemplate(
                -1, ContentType.APPLICATION_JSON, null, outputStream -> MAPPER.writer().writeValue(outputStream, payload));

        httppost.setEntity(entityTemplate);
        httpClient.execute(
                httppost,
                response -> {
                    if (response.getCode() != 200) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        response.getEntity().writeTo(output);

                        throw new RuntimeException(new StatusLine(response) + "\n" + output.toString(StandardCharsets.UTF_8));
                    }
                    return null;
                });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (httpClient != null) {
            httpClient.close();
        }
    }

    Map<String, Object> createPayload(Message message) {
        Object body = message.getBody();
        Map<String, Object> payload = new HashMap<>();
        buildPayload(message, payload);

        if (endpoint.getConfiguration().isBodyOnly()) {
            payload.put("event", body);
        } else if (endpoint.getConfiguration().isHeadersOnly()) {
            payload.put("event", message.getHeaders());
        } else {
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("body", body);
            eventPayload.put("headers", message.getHeaders());
            payload.put("event", eventPayload);
        }

        return payload;
    }

    private void buildPayload(Message message, Map<String, Object> payload) {
        if (endpoint.getConfiguration().getSourceType() != null) {
            payload.put("sourcetype", endpoint.getConfiguration().getSourceType());
        }
        if (endpoint.getConfiguration().getSource() != null) {
            payload.put("source", endpoint.getConfiguration().getSource());
        }
        if (endpoint.getConfiguration().getIndex() != null) {
            payload.put("index", endpoint.getConfiguration().getIndex());
        }
        if (endpoint.getConfiguration().getHost() != null) {
            payload.put("host", endpoint.getConfiguration().getHost());
        }

        Long time = message.getHeader(
                SplunkHECConstants.INDEX_TIME,
                endpoint.getConfiguration().getTime(),
                Long.class);

        if (time != null) {
            payload.put("time", time);
        }
    }
}
