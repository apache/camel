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

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_FORM_URLENCODED;
import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Callable;

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
import org.apache.camel.WrappedFile;
import org.apache.camel.component.vertx.common.VertxBufferConverter;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.MimeTypeHelper;
import org.apache.camel.util.URISupport;

public class VertxHttpProducer extends DefaultAsyncProducer {

    private final VertxHttpBinding vertxHttpBinding;

    public VertxHttpProducer(VertxHttpEndpoint endpoint) {
        super(endpoint);
        this.vertxHttpBinding = endpoint.getConfiguration().getVertxHttpBinding();
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
        Message message = exchange.getMessage();

        try {
            HttpRequest<Buffer> request = vertxHttpBinding.prepareHttpRequest(getEndpoint(), exchange);
            Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler = createResultHandler(exchange, callback);

            Object body = message.getBody();
            if (body == null) {
                request.send(resultHandler);
            } else {
                String contentType = MessageHelper.getContentType(message);
                boolean multipart = getEndpoint().getConfiguration().isMultipartUpload();
                String multipartName = getEndpoint().getConfiguration().getMultipartUploadName();

                // Handle vertx specific body first
                if (body instanceof MultiMap mm) {
                    request.sendForm(mm, resultHandler);
                    return false;
                } else if (body instanceof MultipartForm mf) {
                    request.sendMultipartForm(mf, resultHandler);
                    return false;
                } else if (body instanceof ReadStream rs) {
                    request.sendStream(rs, resultHandler);
                    return false;
                } else if (body instanceof Buffer buf) {
                    request.sendBuffer(buf, resultHandler);
                    return false;
                }

                if (body instanceof File || body instanceof WrappedFile<?>) {
                    // file based (could potentially also be a FTP file etc)
                    File file = message.getBody(File.class);
                    if (file != null) {
                        try (InputStream is = new FileInputStream(file)) {
                            Buffer buf = VertxBufferConverter.toBuffer(is);
                            if (multipart) {
                                String type = MimeTypeHelper.probeMimeType(file.getName());
                                if (type == null) {
                                    type = "application/octet-stream"; // default binary
                                }
                                MultipartForm form = MultipartForm.create()
                                        .binaryFileUpload(multipartName, file.getName(), buf, type);
                                request.sendMultipartForm(form, resultHandler);
                            } else {
                                request.sendBuffer(buf, resultHandler);
                            }
                        }
                    }
                } else if (body instanceof String str) {
                    // Try to extract URL encoded form data from the message body
                    if (CONTENT_TYPE_FORM_URLENCODED.equals(contentType)) {
                        MultiMap map = MultiMap.caseInsensitiveMultiMap();
                        Map<String, Object> formParams = URISupport.parseQuery(str);
                        formParams.forEach((key, o) -> map.add(key, String.valueOf(o)));
                        request.sendForm(map, resultHandler);
                    } else {
                        // Fallback to send as Buffer
                        Buffer buffer = VertxBufferConverter.toBuffer(str, exchange);
                        request.sendBuffer(buffer, resultHandler);
                    }
                } else {
                    // Handle x-java-serialized-object Content-Type
                    if (CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
                        if (!getComponent().isAllowJavaSerializedObject()) {
                            throw new CamelExchangeException(
                                    "Content-type " + CONTENT_TYPE_JAVA_SERIALIZED_OBJECT + " is not allowed",
                                    exchange);
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
        // Process the response on a thread from the Vert.x worker pool since there may be blocking code:
        // - If a custom VertxHttpBinding is in use
        // - If the Camel error handler routes to logic that contains blocking code
        return response -> getEndpoint().getVertx().executeBlocking((Callable<Void>) () -> {
            try {
                vertxHttpBinding.handleResponse(getEndpoint(), exchange, response);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                callback.done(false);
            }
            return null;
        });
    }
}
