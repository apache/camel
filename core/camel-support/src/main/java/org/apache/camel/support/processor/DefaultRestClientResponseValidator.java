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

import static org.apache.camel.support.http.RestUtil.isValidOrAcceptedContentType;

import org.apache.camel.Exchange;
import org.apache.camel.spi.RestClientResponseValidator;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

public class DefaultRestClientResponseValidator implements RestClientResponseValidator {

    @Override
    public ValidationError validate(Exchange exchange, ValidationContext validationContext) {
        String contentType = ExchangeHelper.getContentType(exchange);

        // does the content-type match for the given response codes
        if (contentType != null && validationContext.responseCode() != null) {
            String code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, "200", String.class);
            String found = null;
            for (var e : validationContext.responseCode().entrySet()) {
                String k = e.getKey();
                if (code.equals(k)) {
                    found = e.getValue();
                }
            }
            // if no direct found then see if there is a default code
            if (found == null) {
                found = validationContext.responseCode().get("default");
            }
            if (found != null) {
                if (!isValidOrAcceptedContentType(found, contentType)) {
                    return new ValidationError(
                            500, "Invalid content-type: " + contentType + " for response code: " + code);
                }
            }
        }
        // check for response http headers
        if (validationContext.responseHeaders() != null
                && !exchange.getMessage().getHeaders().keySet().containsAll(validationContext.responseHeaders())) {
            return new ValidationError(500, "Some of the response HTTP headers are missing.");
        }

        Object body = exchange.getMessage().getBody();
        // if content-type is json then lets validate the message body can be parsed to json
        if (body != null && contentType != null && isValidOrAcceptedContentType("application/json", contentType)) {
            String json = MessageHelper.extractBodyAsString(exchange.getIn());
            if (!ObjectHelper.isEmpty(json)) {
                try {
                    Jsoner.deserialize(json);
                } catch (DeserializationException e) {
                    // request payload is not json
                    return new ValidationError(500, "Invalid response JSon payload.");
                }
            }
        }

        // success
        return null;
    }
}
