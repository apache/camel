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

import java.util.HashSet;
import java.util.Set;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.impl.HttpContext;
import org.apache.camel.RuntimeCamelException;

public class KnativeOidcInterceptor implements Handler<HttpContext<?>> {

    private final Set<HttpContext<?>> dejaVu = new HashSet<>();
    private final KnativeOidcClientOptions oidcClientOptions;

    public KnativeOidcInterceptor(KnativeOidcClientOptions options) {
        oidcClientOptions = options;
    }

    @Override
    public void handle(HttpContext<?> context) {
        switch (context.phase()) {
            case CREATE_REQUEST:
                addAuthorizationHeader(context);
                break;
            case DISPATCH_RESPONSE:
                checkTokenRenew(context);
                break;
            default:
                context.next();
                break;
        }
    }

    private void checkTokenRenew(HttpContext<?> context) {
        if (context.response().statusCode() == 401) {
            if (!oidcClientOptions.isRenewTokenOnForbidden() || dejaVu.contains(context)) {
                // already seen, clear and continue without recovery
                dejaVu.remove(context);
                context.next();
            } else {
                // we need some stop condition, so we don't go into an infinite loop
                dejaVu.add(context);
                String old = oidcClientOptions.retrieveOidcToken();
                String renewed = oidcClientOptions.renewOidcToken();
                if (old.equals(renewed)) {
                    // token has not been renewed continue with 401 response
                    dejaVu.remove(context);
                    context.next();
                } else {
                    // retry request with renewed token
                    context.createRequest(context.requestOptions());
                }
            }
        } else {// already seen, clear and continue without recovery
            dejaVu.remove(context);
            context.next();
        }
    }

    private void addAuthorizationHeader(HttpContext<?> context) {
        if (oidcClientOptions.getOidcTokenPath() == null) {
            context.fail(new RuntimeCamelException("Missing OIDC access token path"));
            return;
        }

        context.requestOptions().putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + oidcClientOptions.retrieveOidcToken());
        context.next();
    }
}
