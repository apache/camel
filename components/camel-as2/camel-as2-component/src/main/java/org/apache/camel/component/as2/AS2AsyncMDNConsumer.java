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

package org.apache.camel.component.as2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.as2.api.AS2AsyncMDNServerConnection;
import org.apache.camel.component.as2.api.AS2AsyncMDNServerManager;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.apache.camel.support.component.ApiConsumerHelper;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodHelper;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * A consumer that receives the asynchronous AS2-MDN confirming receipt of the AS2 message by the receiver.
 */
public class AS2AsyncMDNConsumer extends AbstractApiConsumer<AS2ApiName, AS2Configuration>
        implements HttpRequestHandler {

    private static final String HANDLER_PROPERTY = "handler";
    private final ApiMethod apiMethod;
    private final Map<String, Object> properties;
    private AS2AsyncMDNServerConnection as2ReceiptServerConnection;
    private AS2AsyncMDNServerManager apiProxy;

    public AS2AsyncMDNConsumer(AS2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        apiMethod = ApiConsumerHelper.findMethod(endpoint, this);
        properties = new HashMap<>();
        properties.putAll(endpoint.getEndpointProperties());
        properties.put(HANDLER_PROPERTY, this);
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        propertyNames.add(HANDLER_PROPERTY);
    }

    @Override
    protected int poll() throws Exception {
        return 0;
    }

    @Override
    public AS2Endpoint getEndpoint() {
        return (AS2Endpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        as2ReceiptServerConnection = getEndpoint().getAS2AsyncMDNServerConnection();
        apiProxy = new AS2AsyncMDNServerManager(as2ReceiptServerConnection);
        ApiMethodHelper.invokeMethod(apiProxy, apiMethod, properties);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws HttpException {

        Exception exception = null;
        try {
            if (request instanceof HttpEntityContainer) {
                EntityParser.parseAS2MessageEntity(request);
            }
            HttpEntity entity = EntityUtils.getMessageEntity(request);
            if (!isValidReceiptEntity(entity)) {
                String mimeType = ContentType.parse(entity.getContentType()).getMimeType();
                throw new HttpException("Received invalid receipt entity type: " + mimeType);
            }

            Exchange exchange = createExchange(false);
            try {
                HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                exchange.setProperty(AS2Constants.AS2_INTERCHANGE, coreContext);
                exchange.getIn().setBody(entity);
                // send message to next processor in the route
                getProcessor().process(exchange);
            } finally {
                // check if an exception occurred and was not handled
                exception = exchange.getException();
                releaseExchange(exchange, false);
            }
        } catch (Exception e) {
            exception = e;
        }

        if (exception != null) {
            throw new HttpException("Failed to process AS2 receipt: " + exception.getMessage(), exception);
        }
    }

    private boolean isValidReceiptEntity(HttpEntity entity) {
        if (entity instanceof MultipartSignedEntity multipartEntity) {
            // multipart entity consists of MDN and signature
            entity = multipartEntity.getPart(0);
        }
        if (entity instanceof DispositionNotificationMultipartReportEntity) {
            return true;
        }
        return false;
    }
}
