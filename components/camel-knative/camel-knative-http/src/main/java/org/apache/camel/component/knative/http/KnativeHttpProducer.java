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
package org.apache.camel.component.knative.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed KnativeHttpProducer")
public class KnativeHttpProducer extends DefaultAsyncProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnativeHttpProducer.class);

    private final KnativeResource serviceDefinition;
    private final Vertx vertx;
    private final WebClientOptions clientOptions;
    private final HeaderFilterStrategy headerFilterStrategy;

    private String uri;
    private String host;
    private WebClient client;

    public KnativeHttpProducer(
                               Endpoint endpoint,
                               KnativeResource serviceDefinition,
                               Vertx vertx,
                               WebClientOptions clientOptions) {
        super(endpoint);

        this.serviceDefinition = serviceDefinition;
        this.vertx = ObjectHelper.notNull(vertx, "vertx");
        this.clientOptions = ObjectHelper.supplyIfEmpty(clientOptions, WebClientOptions::new);
        this.headerFilterStrategy = new KnativeHttpHeaderFilterStrategy();
    }

    @ManagedAttribute(description = "Url for calling the Knative HTTP service")
    public String getUrl() {
        return uri;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (exchange.getMessage().getBody() == null) {
            exchange.setException(new IllegalArgumentException("body must not be null"));
            callback.done(true);
            return true;
        }

        final byte[] payload;
        try {
            payload = exchange.getMessage().getMandatoryBody(byte[].class);
        } catch (InvalidPayloadException e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add(HttpHeaders.CONTENT_LENGTH, Integer.toString(payload.length));
        headers.add(HttpHeaders.HOST, this.host);

        String contentType = MessageHelper.getContentType(exchange.getMessage());
        if (contentType != null) {
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        }

        for (Map.Entry<String, Object> entry : exchange.getMessage().getHeaders().entrySet()) {
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                headers.add(entry.getKey(), entry.getValue().toString());
            }
        }

        client.postAbs(this.uri)
                .putHeaders(headers)
                .sendBuffer(Buffer.buffer(payload), response -> {
                    if (response.succeeded()) {
                        HttpResponse<Buffer> result = response.result();
                        Message answer = exchange.getMessage();

                        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, result.statusCode());

                        for (Map.Entry<String, String> entry : result.headers().entries()) {
                            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(),
                                    exchange)) {
                                answer.setHeader(entry.getKey(), entry.getValue());
                            }
                        }

                        if (result.body() != null) {
                            answer.setBody(result.body().getBytes());
                        } else {
                            answer.setBody(null);
                        }

                        if (result.statusCode() < 200 || result.statusCode() >= 300) {
                            String exceptionMessage = String.format(
                                    "HTTP operation failed invoking %s with statusCode: %d, statusMessage: %s",
                                    URISupport.sanitizeUri(this.uri),
                                    result.statusCode(),
                                    result.statusMessage());

                            exchange.setException(new CamelException(exceptionMessage));
                        }

                        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, result.statusCode());
                    } else if (response.failed()) {
                        String exceptionMessage = "HTTP operation failed invoking " + URISupport.sanitizeUri(this.uri);
                        if (response.result() != null) {
                            exceptionMessage += " with statusCode: " + response.result().statusCode();
                        }

                        exchange.setException(new CamelException(exceptionMessage));
                    }

                    callback.done(false);
                });

        return false;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.uri = getUrl(serviceDefinition);
        this.host = getHost(serviceDefinition);
        this.client = WebClient.create(vertx, clientOptions);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (this.client != null) {
            LOGGER.debug("Shutting down client: {}", client);
            this.client.close();
            this.client = null;
        }
    }

    private String getUrl(KnativeResource definition) {
        String url = definition.getUrl();
        if (url == null) {
            throw new RuntimeCamelException("Unable to determine the `url` for definition: " + definition);
        }

        String path = definition.getPath();
        if (path != null) {
            if (path.charAt(0) != '/') {
                path = "/" + path;
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            url += path;
        }

        return getEndpoint().getCamelContext().resolvePropertyPlaceholders(url);
    }

    private String getHost(KnativeResource definition) {
        String url = getUrl(definition);
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeCamelException("Unable to determine `host` for definition: " + definition, e);
        }
    }

}
