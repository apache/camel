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

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.as2.internal.AS2PropertiesHelper;
import org.apache.camel.support.component.AbstractApiProducer;
import org.apache.camel.support.component.ApiMethod;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * The AS2 producer.
 */
public class AS2Producer extends AbstractApiProducer<AS2ApiName, AS2Configuration> {

    private static final String EDI_MESSAGE = "ediMessage";

    public AS2Producer(AS2Endpoint endpoint) {
        super(endpoint, AS2PropertiesHelper.getHelper(endpoint.getCamelContext()));
    }

    @Override
    protected ApiMethod findMethod(Exchange exchange, Map<String, Object> properties) {
        ApiMethod method = super.findMethod(exchange, properties);
        if (method != null && !convertEdiMessage(exchange, properties)) {
            return null;
        }
        return method;
    }

    /**
     * The client sends text and a stream as they are, and any other EDI message, such as bytes or a file, as its bytes.
     */
    private boolean convertEdiMessage(Exchange exchange, Map<String, Object> properties) {
        Object ediMessage = properties.get(EDI_MESSAGE);
        if (ediMessage == null || ediMessage instanceof String || ediMessage instanceof InputStream) {
            return true;
        }
        try {
            properties.put(EDI_MESSAGE,
                    exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, ediMessage));
            return true;
        } catch (Exception e) {
            exchange.setException(new RuntimeCamelException(
                    String.format("Cannot send an EDI message of type %s: send text, a stream, bytes or a file",
                            ediMessage.getClass().getName()),
                    e));
            return false;
        }
    }

    @Override
    public void interceptResult(Object methodResult, Exchange resultExchange) {
        HttpCoreContext context = (HttpCoreContext) methodResult;
        resultExchange.setProperty(AS2Constants.AS2_INTERCHANGE, context);
        HttpResponse response = context.getResponse();
        if (response != null && (response.getCode() < 200 || response.getCode() >= 300)) {
            String reason = response.getReasonPhrase();
            throw new RuntimeCamelException(
                    "The AS2 partner answered with HTTP status " + response.getCode() + (reason != null ? " " + reason : ""));
        }
        if (response instanceof ClassicHttpResponse classicResponse) {
            HttpEntity entity = classicResponse.getEntity();
            if (entity instanceof DispositionNotificationMultipartReportEntity || entity instanceof MultipartSignedEntity) {
                resultExchange.getMessage().setBody(entity);
            } else {
                resultExchange.getMessage().setBody(null);
            }
        }
    }
}
