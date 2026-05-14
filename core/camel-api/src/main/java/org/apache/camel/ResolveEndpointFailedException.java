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
package org.apache.camel;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import static org.apache.camel.util.URISupport.sanitizeUri;

/**
 * A runtime exception thrown if an {@link Endpoint} cannot be resolved via URI
 */
public class ResolveEndpointFailedException extends RuntimeCamelException {

    private final String uri;

    /**
     * @param uri   the endpoint URI that could not be resolved
     * @param cause the cause of the failure
     */
    public ResolveEndpointFailedException(@Nullable String uri, Throwable cause) {
        super("Failed to resolve endpoint: " + sanitizeUri(uri) + " due to: "
              + Objects.requireNonNull(cause, "cause").getMessage(), cause);
        this.uri = sanitizeUri(uri);
    }

    /**
     * @param uri     the endpoint URI that could not be resolved
     * @param message the detail message describing why the endpoint could not be resolved
     */
    public ResolveEndpointFailedException(@Nullable String uri, String message) {
        super("Failed to resolve endpoint: " + sanitizeUri(uri) + " due to: "
              + Objects.requireNonNull(message, "message"));
        this.uri = sanitizeUri(uri);
    }

    /**
     * @param uri the endpoint URI that could not be resolved
     */
    public ResolveEndpointFailedException(@Nullable String uri) {
        super("Failed to resolve endpoint: " + sanitizeUri(uri));
        this.uri = sanitizeUri(uri);
    }

    public String getUri() {
        return uri;
    }
}
