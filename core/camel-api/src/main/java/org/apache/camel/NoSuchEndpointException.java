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
 * Thrown when a routing processor (such as a recipient list or dynamic router) cannot resolve a URI to a known
 * {@link Endpoint}, typically because the required Camel component is missing from the classpath.
 * <p/>
 * Contrast with {@link ResolveEndpointFailedException}, which is thrown when the component is present but the URI is
 * malformed or configuration fails.
 *
 * @see ResolveEndpointFailedException
 * @see Endpoint
 */
public class NoSuchEndpointException extends RuntimeCamelException {

    private final String uri;

    /**
     * @param uri the endpoint URI that could not be found
     */
    public NoSuchEndpointException(String uri) {
        super("No endpoint could be found for: " + sanitizeUri(Objects.requireNonNull(uri, "uri"))
              + ", please check your classpath contains the needed Camel component jar.");
        this.uri = sanitizeUri(uri);
    }

    /**
     * @param uri           the endpoint URI that could not be found
     * @param resolveMethod a resolution instruction appended after "please" in the error message
     */
    public NoSuchEndpointException(String uri, String resolveMethod) {
        super("No endpoint could be found for: " + sanitizeUri(Objects.requireNonNull(uri, "uri"))
              + ", please " + Objects.requireNonNull(resolveMethod, "resolveMethod"));
        this.uri = sanitizeUri(uri);
    }

    public String getUri() {
        return uri;
    }
}
