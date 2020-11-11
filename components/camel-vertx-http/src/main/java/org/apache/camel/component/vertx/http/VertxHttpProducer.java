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
package org.apache.camel.component.vertx.http;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_FORM_URLENCODED;
import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT;

public class VertxHttpProducer extends DefaultAsyncProducer {

    public VertxHttpProducer(VertxHttpEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public VertxHttpEndpoint getEndpoint() {
        return (VertxHttpEndpoint) super.getEndpoint();
    }

    public VertxHttpComponent getComponent() {
        return getEndpoint().getComponent();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        VertxHttpConfiguration configuration = getEndpoint().getConfiguration();
        Message message = exchange.getMessage();

        try {
            VertxHttpBinding vertxHttpBinding = configuration.getVertxHttpBinding();
            HttpRequest<Buffer> request = vertxHttpBinding.prepareHttpRequest(getEndpoint(), exchange);
            Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler = createResultHandler(exchange, callback);

            Object body = message.getBody();
            if (body == null) {
                request.send(resultHandler);
            } else {
                // Handle the request body payload
                if (body instanceof MultiMap) {
                    request.sendForm((MultiMap) body, resultHandler);
                } else if (body instanceof MultipartForm) {
                    request.sendMultipartForm((MultipartForm) body, resultHandler);
                } else if (body instanceof ReadStream) {
                    request.sendStream((ReadStream<Buffer>) body, resultHandler);
                } else if (body instanceof String) {
                    // Try to extract URL encoded form data from the message body
                    if (VertxHttpHelper.isContentTypeMatching(exchange, CONTENT_TYPE_FORM_URLENCODED)) {
                        MultiMap map = MultiMap.caseInsensitiveMultiMap();
                        Map<String, Object> formParams = URISupport.parseQuery((String) body);
                        formParams.keySet().forEach(key -> map.add(key, String.valueOf(formParams.get(key))));
                        request.sendForm(map, resultHandler);
                    } else {
                        // Fallback to send as Buffer
                        Buffer buffer;
                        String charset = VertxHttpHelper.getCharsetFromExchange(exchange);
                        if (ObjectHelper.isNotEmpty(charset)) {
                            buffer = Buffer.buffer((String) body, charset);
                        } else {
                            buffer = Buffer.buffer((String) body);
                        }
                        request.sendBuffer(buffer, resultHandler);
                    }
                } else if (body instanceof Buffer) {
                    request.sendBuffer((Buffer) body, resultHandler);
                } else {
                    // Handle x-java-serialized-object Content-Type
                    if (VertxHttpHelper.isContentTypeMatching(exchange, CONTENT_TYPE_JAVA_SERIALIZED_OBJECT)) {
                        if (!getComponent().isAllowJavaSerializedObject()) {
                            throw new CamelExchangeException(
                                    "Content-type " + CONTENT_TYPE_JAVA_SERIALIZED_OBJECT + " is not allowed", exchange);
                        }

                        // Send a serialized Java object message body
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            Serializable serializable = message.getMandatoryBody(Serializable.class);
                            VertxHttpHelper.writeObjectToStream(baos, serializable);
                            request.sendBuffer(Buffer.buffer(baos.toByteArray()), resultHandler);
                        }
                    } else {
                        Buffer buffer = message.getMandatoryBody(Buffer.class);
                        request.sendBuffer(buffer, resultHandler);
                    }
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        return false;
    }

    private Handler<AsyncResult<HttpResponse<Buffer>>> createResultHandler(Exchange exchange, AsyncCallback callback) {
        return response -> {
            try {
                VertxHttpEndpoint endpoint = getEndpoint();
                VertxHttpBinding vertxHttpBinding = endpoint.getConfiguration().getVertxHttpBinding();
                vertxHttpBinding.handleResponse(endpoint, exchange, response);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            } finally {
                callback.done(false);
            }
        };
    }
}
