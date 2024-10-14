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
package org.apache.camel.support.processor;

import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.spi.RestClientRequestValidator;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.support.http.RestUtil.isValidOrAcceptedContentType;

public class DefaultRestClientRequestValidator implements RestClientRequestValidator {

    @Override
    public ValidationError validate(Exchange exchange, ValidationContext validationContext) {
        String contentType = ExchangeHelper.getContentType(exchange);

        // check if the content-type is accepted according to consumes
        if (!isValidOrAcceptedContentType(validationContext.consumes(), contentType)) {
            return new ValidationError(415, null);
        }
        // check if what is produces is accepted by the client
        String accept = exchange.getMessage().getHeader("Accept", String.class);
        if (!isValidOrAcceptedContentType(validationContext.produces(), accept)) {
            return new ValidationError(406, null);
        }
        // check for required query parameters
        if (validationContext.requiredQueryParameters() != null
                && !exchange.getIn().getHeaders().keySet().containsAll(validationContext.requiredQueryParameters())) {
            // this is a bad request, the client did not include some required query parameters
            return new ValidationError(400, "Some of the required query parameters are missing.");
        }
        // check for required http headers
        if (validationContext.requiredHeaders() != null
                && !exchange.getIn().getHeaders().keySet().containsAll(validationContext.requiredHeaders())) {
            // this is a bad request, the client did not include some required query parameters
            return new ValidationError(400, "Some of the required HTTP headers are missing.");
        }
        // allowed values for query/header parameters
        if (validationContext.queryAllowedValues() != null) {
            for (var e : validationContext.queryAllowedValues().entrySet()) {
                String k = e.getKey();
                Object v = exchange.getMessage().getHeader(k);
                if (v != null) {
                    String[] parts = e.getValue().split(",");
                    if (Arrays.stream(parts).noneMatch(v::equals)) {
                        // this is a bad request, the client did not include some required query parameters
                        return new ValidationError(
                                400, "Some of the query parameters or HTTP headers has a not-allowed value.");
                    }
                }
            }
        }

        Object body = exchange.getMessage().getBody();
        if (validationContext.requiredBody()) {
            // the body is required, so we need to know if we have a body or not
            // so force reading the body as a String which we can work with
            body = MessageHelper.extractBodyAsString(exchange.getIn());
            if (ObjectHelper.isNotEmpty(body)) {
                exchange.getIn().setBody(body);
            }
            if (ObjectHelper.isEmpty(body)) {
                // this is a bad request, the client did not include a message body
                return new ValidationError(400, "The request body is missing.");
            }
        }
        // if content-type is json then lets validate the message body can be parsed to json
        if (body != null && contentType != null && isValidOrAcceptedContentType("application/json", contentType)) {
            String json = MessageHelper.extractBodyAsString(exchange.getIn());
            if (!ObjectHelper.isEmpty(json)) {
                try {
                    Jsoner.deserialize(json);
                } catch (DeserializationException e) {
                    // request payload is not json
                    return new ValidationError(400, "Invalid JSon payload.");
                }
            }
        }

        // success
        return null;
    }

}
