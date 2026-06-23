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
package org.apache.camel.component.undertow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.http.base.OAuthHttpSecuritySupport.Validation;

final class OAuthUndertowHttpHandler implements HttpHandler {

    private final CamelContext camelContext;
    private final OAuthHttpSecuritySupport securitySupport;
    private final HttpHandler next;
    private final boolean optionsEnabled;
    private final BooleanSupplier suspended;

    OAuthUndertowHttpHandler(
                             CamelContext camelContext, OAuthHttpSecuritySupport securitySupport, HttpHandler next,
                             boolean optionsEnabled, BooleanSupplier suspended) {
        this.camelContext = camelContext;
        this.securitySupport = securitySupport;
        this.next = next;
        this.optionsEnabled = optionsEnabled;
        this.suspended = suspended;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (Methods.OPTIONS.equals(exchange.getRequestMethod()) && !optionsEnabled) {
            next.handleRequest(exchange);
            return;
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        if (suspended.getAsBoolean()) {
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            exchange.endExchange();
            return;
        }

        Validation validation
                = securitySupport.validate(camelContext, authorizationHeaders(exchange));
        exchange.getRequestHeaders().remove(Headers.AUTHORIZATION);
        if (validation.isAuthenticated()) {
            exchange.putAttachment(UndertowConsumer.OAUTH_TOKEN_VALIDATION_RESULT_ATTACHMENT,
                    validation.getValidationResult());
            next.handleRequest(exchange);
            return;
        }

        exchange.setStatusCode(validation.getRejectionStatusCode());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
        if (validation.getWwwAuthenticate() != null) {
            exchange.getResponseHeaders().put(Headers.WWW_AUTHENTICATE, validation.getWwwAuthenticate());
        }
        exchange.getResponseSender().send(validation.getResponseBody());
    }

    private static List<String> authorizationHeaders(HttpServerExchange exchange) {
        HeaderValues values = exchange.getRequestHeaders().get(Headers.AUTHORIZATION);
        if (values == null) {
            return List.of();
        }
        List<String> answer = new ArrayList<>(values.size());
        for (String value : values) {
            answer.add(value);
        }
        return answer;
    }
}
