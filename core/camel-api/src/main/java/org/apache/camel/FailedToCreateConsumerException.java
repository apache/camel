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

import static org.apache.camel.util.URISupport.sanitizeUri;

/**
 * Thrown when an {@link Endpoint} cannot produce a {@link Consumer}, for example because the endpoint URI is not
 * consumer-capable, required options are missing, or an underlying client connection cannot be opened.
 * <p/>
 * The originating endpoint URI is sanitized (credentials removed) before being included in the message.
 */
public class FailedToCreateConsumerException extends RuntimeCamelException {

    private final String uri;

    /**
     * @param endpointURI the URI of the endpoint for which consumer creation failed
     * @param cause       the cause of the failure
     */
    public FailedToCreateConsumerException(String endpointURI, Throwable cause) {
        super("Failed to create Consumer for endpoint for: " + sanitizeUri(Objects.requireNonNull(endpointURI, "endpointURI"))
              + ". Reason: " + Objects.requireNonNull(cause, "cause"), cause);
        this.uri = sanitizeUri(endpointURI);
    }

    /**
     * @param endpoint the endpoint for which consumer creation failed
     * @param cause    the cause of the failure
     */
    public FailedToCreateConsumerException(Endpoint endpoint, Throwable cause) {
        super("Failed to create Consumer for endpoint: " + Objects.requireNonNull(endpoint, "endpoint") + ". Reason: "
              + Objects.requireNonNull(cause, "cause"), cause);
        this.uri = sanitizeUri(endpoint.getEndpointUri());
    }

    /**
     * @param endpoint the endpoint for which consumer creation failed
     * @param message  the detail message
     * @param cause    the cause of the failure
     */
    public FailedToCreateConsumerException(Endpoint endpoint, String message, Throwable cause) {
        super("Failed to create Consumer for endpoint: " + Objects.requireNonNull(endpoint, "endpoint") + ". Reason: "
              + Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.uri = sanitizeUri(endpoint.getEndpointUri());
    }

    /**
     * @param endpoint the endpoint for which consumer creation failed
     * @param message  the detail message
     */
    public FailedToCreateConsumerException(Endpoint endpoint, String message) {
        super("Failed to create Consumer for endpoint: " + Objects.requireNonNull(endpoint, "endpoint") + ". Reason: "
              + Objects.requireNonNull(message, "message"));
        this.uri = sanitizeUri(endpoint.getEndpointUri());
    }

    public String getUri() {
        return uri;
    }
}
