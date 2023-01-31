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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

/**
 * The Splunk HEC producer.
 */
public class SplunkHECProducer extends DefaultProducer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private SplunkHECEndpoint endpoint;
    private CloseableHttpClient httpClient;

    public SplunkHECProducer(SplunkHECEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        HttpClientBuilder builder = HttpClients.custom()
                .setUserAgent("Camel Splunk HEC/" + getEndpoint().getCamelContext().getVersion()).setMaxConnTotal(10);
        if (endpoint.getConfiguration().isSkipTlsVerify()) {
            SSLContextBuilder sslbuilder = new SSLContextBuilder();
            sslbuilder.loadTrustMaterial(null, (chain, authType) -> true);
            SSLConnectionSocketFactory sslsf
                    = new SSLConnectionSocketFactory(sslbuilder.build(), NoopHostnameVerifier.INSTANCE);
            builder.setSSLSocketFactory(sslsf);
        }
        httpClient = builder.build();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> payload = createPayload(exchange.getIn());

        HttpPost httppost = new HttpPost(
                (endpoint.getConfiguration().isHttps() ? "https" : "http") + "://"
                                         + endpoint.getSplunkURL() + endpoint.getConfiguration().getSplunkEndpoint());
        httppost.addHeader("Authorization", " Splunk " + endpoint.getToken());

        EntityTemplate entityTemplate = new EntityTemplate(outputStream -> MAPPER.writer().writeValue(outputStream, payload));
        entityTemplate.setContentType(ContentType.APPLICATION_JSON.getMimeType());

        httppost.setEntity(entityTemplate);
        try (CloseableHttpResponse response = httpClient.execute(httppost)) {
            if (response.getStatusLine().getStatusCode() != 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                response.getEntity().writeTo(output);

                throw new RuntimeException(
                        response.getStatusLine().toString() + "\n" + new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        }
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
