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
package org.apache.camel.component.rest.postman.support;

import java.util.Map;

import org.apache.camel.component.rest.postman.model.PostmanItem;

/**
 * One Postman request, reduced to everything the {@code rest} component needs in order to call it.
 * <p>
 * Every {@code {{variable}}} has already been substituted, so the values here are final; the only placeholders left are
 * the {@code {name}} markers that {@code camel-rest} resolves per exchange from message headers.
 *
 * @param item              the request this was built from
 * @param method            the HTTP method, upper-cased
 * @param host              scheme, host and optional port, or {@code null} when it could not be derived
 * @param basePath          the context path, always starting with {@code /}
 * @param uriTemplate       the remaining path, with {@code :name} rewritten to {@code {name}}
 * @param queryParameters   the query string in the placeholder syntax understood by {@code camel-rest}, or {@code null}
 *                          when the request declares none
 * @param consumes          the {@code Accept} header to send, or {@code null} to leave it to the caller
 * @param produces          the {@code Content-Type} header to send, or {@code null} when there is no body
 * @param staticHeaders     headers declared by the collection, applied only when the message does not already carry
 *                          them
 * @param defaultPathValues defaults for the {@code {name}} path markers, taken from {@code url.variable}
 * @param collectionBody    the body written in the collection, used when running a whole folder or collection where one
 *                          exchange body cannot serve every request; {@code null} when there is none
 */
public record PostmanRequestBinding(
        PostmanItem item,
        String method,
        String host,
        String basePath,
        String uriTemplate,
        String queryParameters,
        String consumes,
        String produces,
        Map<String, String> staticHeaders,
        Map<String, String> defaultPathValues,
        String collectionBody) {

    public PostmanRequestBinding {
        staticHeaders = Map.copyOf(staticHeaders);
        defaultPathValues = Map.copyOf(defaultPathValues);
    }

    /**
     * The id a route author uses to refer to this request.
     */
    public String id() {
        return item.getCanonicalId();
    }

    /**
     * The full path this request is served on or sent to, that is the base path followed by the template.
     */
    public String fullPath() {
        if ("/".equals(basePath)) {
            return uriTemplate;
        }
        return basePath + uriTemplate;
    }
}
